package com.github.sample;

/**
 * Copyright (C), 2014-2015, 上海蓝娱信息技术有限公司
 * PackageName: com.github.sample
 * Author: 沈斌斌
 * Date: 16/2/16 16:02
 * Description:
 */
public class UserQueryResult {
	private Long    userId;
	private String  username;
	private Integer age;

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public Integer getAge() {
		return age;
	}

	public void setAge(Integer age) {
		this.age = age;
	}
}
