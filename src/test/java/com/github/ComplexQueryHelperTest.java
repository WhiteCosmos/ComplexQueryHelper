package com.github;

import com.github.sample.UserQueryBean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C), 2014-2015, 上海蓝娱信息技术有限公司
 * PackageName: com.github
 * Author: 沈斌斌
 * Date: 16/2/16 14:40
 * Description:
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {
	Configuration.class,
	ComplexQueryHelperDefault.class})
public class ComplexQueryHelperTest {
	@Autowired
	private ComplexQueryHelper helper;

	private UserQueryBean condition;

	@Before
	public void before() {
		condition = new UserQueryBean(null, "S");
	}

	@Test
	public void testQueryOneByMap() {
		Map<String, Object> condition = new HashMap<>();

		condition.put("userId", 1);

		String name = helper.queryOneByCondition(String.class,
		                                         "queryUserNameByUserBean",
		                                         condition);

		assert "BlackLotus".equals(name);
	}

	@Test
	public void testQueryOneByJavaBean() {
		UserQueryBean condition = new UserQueryBean(1L, null);

		String name = helper.queryOneByCondition(String.class,
		                                         "queryUserNameByUserBean",
		                                         condition);

		assert "BlackLotus".equals(name);

		condition = new UserQueryBean(null, "Sky");

		name = helper.queryOneByCondition(String.class,
		                                  "queryUserNameByUserBean",
		                                  condition);

		assert "SkyRaker".equals(name);
	}

	@Test
	public void testQueryList() {
		List<String> list = helper.queryListByCondition(String.class,
		                                                "queryUserNameByUserBean",
		                                                condition);
		assert list.size() == 2;
	}
}
