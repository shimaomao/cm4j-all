package com.cm4j.taobao.service.async.quartz;

import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:spring/applicationContext.xml" })
public class QuartzServiceTest {

	@Autowired
	private QuartzService quartzService;
	
	public void testStartJobs (){
		quartzService.startJobsWithExecutor();
	}
}
