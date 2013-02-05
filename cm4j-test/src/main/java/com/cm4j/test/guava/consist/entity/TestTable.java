package com.cm4j.test.guava.consist.entity;

// Generated 2013-1-19 16:21:24 by Hibernate Tools 3.2.4.GA

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cm4j.test.guava.consist.DBState;
import com.cm4j.test.guava.consist.value.SingleValue;

/**
 * TestTable generated by hbm2java
 */
@Entity
@Table(name = "test_table")
public class TestTable extends SingleValue implements IEntity {

	private static final long serialVersionUID = 1L;

	private Integer NId;
	private Long NValue;

	public TestTable() {
	}

	public TestTable(Integer NId) {
		this.NId = NId;
	}

	public TestTable(Integer NId, Long NValue) {
		this.NId = NId;
		this.NValue = NValue;
	}

	@Id
	@Column(name = "n_id", unique = true, nullable = false)
	public Integer getNId() {
		return this.NId;
	}

	public void setNId(Integer NId) {
		this.NId = NId;
	}

	@Column(name = "n_value")
	public Long getNValue() {
		return this.NValue;
		// try {
		// getLock().lock();
		// return this.NValue;
		// } finally {
		// getLock().unlock();
		// }
	}

	public void setNValue(Long NValue) {
		this.NValue = NValue;
	}

	@Override
	public IEntity parseEntity() {
		return this;
	}

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void increaseValue() {
		getLock().lock();

		this.NValue++;
		logger.debug("" + this);

		getLock().unlock();
		setDbState(DBState.U);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + ":" + this.hashCode() + "#" + getNId() + "-" + getNValue();
	}
}
