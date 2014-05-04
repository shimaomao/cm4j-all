package com.cm4j.test.guava.consist.cc;

import com.cm4j.dao.hibernate.HibernateDao;
import com.cm4j.test.guava.consist.cc.constants.Constants;
import com.cm4j.test.guava.consist.cc.persist.CacheEntryInPersistQueue;
import com.cm4j.test.guava.consist.cc.persist.DBState;
import com.cm4j.test.guava.consist.entity.IEntity;
import com.cm4j.test.guava.service.ServiceManager;
import org.hibernate.HibernateException;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.perf4j.StopWatch;
import org.perf4j.slf4j.Slf4JStopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by yanghao on 14-4-1.
 */
public class DBPersistQueue {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ConcurrentLinkedQueue<CacheEntryInPersistQueue> queue;

    /**
     * 更新队列消费计数器
     */
    public long counter = 0L;

    public DBPersistQueue() {
        this.queue = new ConcurrentLinkedQueue<CacheEntryInPersistQueue>();
    }

    /**
     * 发送到更新队列
     *
     * @param entry
     */
    public void sendToPersistQueue(CacheEntry entry) {
        StopWatch watch = new Slf4JStopWatch();
        entry.getNumInUpdateQueue().incrementAndGet();
        queue.add(new CacheEntryInPersistQueue(entry));
        watch.stop("cache.sendToPersistQueue()");
    }

    /**
     * 修改队列内数据持久化
     * <p/>
     * 条件 或 关系：
     * 1.doNow=true
     * 2.修改队列内数据个数达到 Constants.MAX_UNITS_IN_UPDATE_QUEUE
     * 3.持久化次数达到 Constants.PERSIST_CHECK_INTERVA
     *
     * @param doNow 是否立即写入
     */
    public void consumePersistQueue(boolean doNow) {
        int persistNum = 0;

        long currentCounter = counter++;
        logger.error("定时[{}]检测：缓存存储数据队列大小：[{}]，doNow：[{}]", new Object[]{currentCounter, queue.size(), doNow});
        if (doNow || queue.size() >= Constants.MAX_UNITS_IN_UPDATE_QUEUE || (currentCounter) % Constants.PERSIST_CHECK_INTERVAL == 0) {
            if (queue.size() == 0) {
                logger.error("定时[{}]检测结束，queue内无数据", currentCounter);
                return;
            }
            logger.debug("缓存存储数据开始");

            final StopWatch watch = new Slf4JStopWatch();
            List<CacheEntryInPersistQueue> toBatch = new ArrayList<CacheEntryInPersistQueue>();

            CacheEntryInPersistQueue wrapper;
            while ((wrapper = queue.poll()) != null) {
                CacheEntry reference = wrapper.getReference();
                int num = reference.getNumInUpdateQueue().decrementAndGet();
                // 删除或者更新的num为0
                if (num == 0) {
                    IEntity entity = wrapper.getEntity();
                    if (entity != null && DBState.P != wrapper.getDbState()) {
                        toBatch.add(wrapper);
                    }
                }

                watch.lap("cache.loopPersisitQueue()");

                // 条件：在toBatch大于0的情况下 (或关系)：
                // 1.toBatch的大小达到 Constants.BATCH_TO_COMMIT
                // 2.queue为空
                if (toBatch.size() > 0 && (toBatch.size() % Constants.BATCH_TO_COMMIT == 0 || queue.size() == 0)) {
                    try {
                        logger.debug("批处理大小：{}", toBatch.size());
                        persistNum += toBatch.size();
                        batchPersistData(toBatch);
                    } catch (Exception e) {
                        logger.error("缓存批处理异常", e);
                    }
                    toBatch.clear();
                    watch.stop("cache.batchPersistData()");
                }
            }
        }
        logger.error("定时[{}]检测结束，本次存储大小为{}", currentCounter, persistNum);
    }

    /**
     * 批处理写入数据
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void batchPersistData(Collection<CacheEntryInPersistQueue> entities) {
        HibernateDao hibernate = ServiceManager.getInstance().getSpringBean("hibernateDao");
        Session session = hibernate.getSession();
        Transaction tx = session.beginTransaction();
        try {
            int idx = 0;

            for (CacheEntryInPersistQueue wrapper : entities) {
                DBState dbState = wrapper.getDbState();
                IEntity entity = wrapper.getEntity();

                if (DBState.U == dbState) {
                    session.merge(entity);
                } else if (DBState.D == dbState) {
                    // 为什么会有相同ID的对象进行删除，会报错
                    // 是否是对象被删除了，然后又新建了一个，然后又被删除了
                    // 因此在下面的exception中有此处理
                    session.delete(entity);
                }
                if ((++idx) % 50 == 0) {
                    session.flush(); // 清理缓存，执行批量插入20条记录的SQL insert语句
                    session.clear(); // 清空缓存中的Customer对象
                }
            }
            // 提交
            tx.commit();
            for (CacheEntryInPersistQueue wrapper : entities) {
                // recheck,有可能又有其他线程更新了对象，此时也不能重置为P
                ConcurrentCache.getInstance().changeDbStatePersist(wrapper.getReference());
            }
        } catch (HibernateException exception) {
            tx.rollback();
            if (!(exception instanceof NonUniqueObjectException)) {
                // 在删除时，如果同一个key的不同对象删除，会报NonUniqueObjectException，但这种情况比较少
                // 可以参考：http://stackoverflow.com/questions/6518567/org-hibernate-nonuniqueobjectexception
                logger.error("缓存批处理写入DB异常", exception);
            }
            for (CacheEntryInPersistQueue wrapper : entities) {
                try {
                    DBState dbState = wrapper.getDbState();
                    IEntity entity = wrapper.getEntity();

                    if (DBState.U == dbState) {
                        hibernate.saveOrUpdate(entity);
                    } else if (DBState.D == dbState) {
                        hibernate.delete(entity);
                    }
                    // recheck,有可能又有其他线程更新了对象，此时也不能重置为P
                    ConcurrentCache.getInstance().changeDbStatePersist(wrapper.getReference());
                } catch (DataAccessException e1) {
                    logger.error("批处理失败，单条更新失败", e1);
                }
            }
        } finally {
            try {
                session.close();
            } catch (HibernateException e) {
                logger.error("批处理失败，session.close()异常", e);
            }
        }
    }

    public static void main(String[] args) {
        ConcurrentLinkedQueue queue1 = new ConcurrentLinkedQueue();
        for (int i = 0; i < 80000; i++) {
            queue1.offer(i);
        }
        long start = System.currentTimeMillis();
        while (queue1.poll() != null){

        }
        long end = System.currentTimeMillis();

        System.out.println(end - start);
    }
}