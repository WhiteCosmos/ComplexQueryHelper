package com.github.sample;

/**
 * Copyright (C), 2014-2015, 上海蓝娱信息技术有限公司
 * PackageName: com.github
 * Author: 沈斌斌
 * Date: 16/2/16 15:53
 * Description:
 */
public class UserQueryBean {
	private Long   userId;
	private String username;

	public UserQueryBean(Long userId, String username) {
		this.userId = userId;
		this.username = username;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getUsername() {
		return username == null ? null : username + "%";
	}

	public void setUsername(String username) {
		this.username = username;
	}
}
