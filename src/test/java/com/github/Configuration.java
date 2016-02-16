package com.github;

import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Copyright (C), 2014-2015, 上海蓝娱信息技术有限公司
 * PackageName: com.github
 * Author: 沈斌斌
 * Date: 16/2/16 14:56
 * Description:
 */
@org.springframework.context.annotation.Configuration
public class Configuration {
	@Bean(destroyMethod = "shutdown")
	public EmbeddedDatabase dataSource() throws ClassNotFoundException {
		return new EmbeddedDatabaseBuilder().
			setType(EmbeddedDatabaseType.H2).
			addScript("schema/db-schema.sql").
			addScript("schema/db-test-data.sql").
			build();
	}
}
