package com.cm4j.taobao.api.google.urlshorter;

import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Test;

import com.cm4j.taobao.utils.HttpClientUtils.HttpException;

public class GoogleURLShorterTest {

	@Test
	public void testShorterAndLonger() throws HttpException {
		String longUrl = "http://www.sina.com.cn/";
		String shorted = GoogleURLShorter.shorten(longUrl);
		Assert.assertThat(shorted, Is.is("http://goo.gl/rN7W"));

		String longer = GoogleURLShorter.longen(shorted);
		Assert.assertThat(longer, Is.is(longUrl));
	}
}
