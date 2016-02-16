package com.github;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogChute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.beans.BeansException;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.sql.DataSource;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: WhiteCosmos
 * Description: ComplexQuery Default Impl
 */
@Service
@Configuration
public class ComplexQueryHelperDefault implements ComplexQueryHelper, ApplicationContextAware {
	private static final Logger LOGGER = Logger.getLogger(ComplexQueryHelperDefault.class);

	private ApplicationContext applicationContext;

	/** Create Transaction Manager For Every DataSource */
	@Bean
	public List<DataSourceTransactionManager> transactionManager() {
		List<DataSourceTransactionManager> managers = new LinkedList<>();

		Map<String, DataSource> dataSources = applicationContext.getBeansOfType(DataSource.class);

		for (DataSource dataSource : dataSources.values()) {
			DataSourceTransactionManager manager = new DataSourceTransactionManager();
			manager.setDataSource(dataSource);
			managers.add(manager);
		}

		return managers;
	}

	/** Initialize JdbcTemplate */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		/** Initialize Velocity and disable log functionality */
		Velocity.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogChute());

		Velocity.init();

		/** Initialize all sql definition */
		this.initializeSQL(applicationContext);
		/** Initialize all datasource */
		this.initializeDataSource(applicationContext);
	}

	private void initializeSQL(ApplicationContext applicationContext) {
		SAXReader saxReader = new SAXReader();
		/** Ignore DTD verify */
		saxReader.setEntityResolver(new EntityResolver() {
			@SuppressWarnings("deprecation")
			@Override
			public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
				return new InputSource(new StringBufferInputStream(""));
			}
		});

		Resource[] resources;
		try {
			resources = RESOURCE_PATTERN_RESOLVER.getResources(QUERY_FILE_PATH);
		} catch (IOException e) {
			throw new ComplexQueryException("获取SQL文件失败: " + e.getMessage());
		}

		/** Iterate every resource file */
		for (Resource resource : resources) {
			try {
				this.buildSQLCacheTask(saxReader, resource);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		LOGGER.info("SQL初始化完毕 共初始化 " + SQL_CACHE.size() + " 个SQL");
	}

	private void buildSQLCacheTask(SAXReader saxReader,
	                               Resource resource) throws SQLException {
		try {
			Document document = saxReader.read(resource.getInputStream());
			List elements = document.getRootElement().elements("query");
			for (Object o : elements) {
				Element element = (Element) o;

				/** SQL KEY */
				String key = element.attributeValue("key");
				/** SQL Processor */
				String processor = element.attributeValue("processor");
				/** SQL Body */
				String sql = element.elementText("value").trim();

				/** If we meet wrong processor, continue. */
				if (!VELOCITY_PROCESSOR.equals(processor)) {
					continue;
				}

				/** Check duplicate SQL */
				if (SQL_CACHE.containsKey(key)) {
					LOGGER.error("SQL初始化失败, " + key + " 已存在");
					throw new ComplexQueryException("SQL initialize failed " + key + " already exists");
				}

				/** Put SQL into cache */
				SQL_CACHE.put(key, sql);

				/** put key to datasource into map */
				MAP_QUERY_TO_DATASOURCE.put(key, DEFAULT_DATASOURCE);
			}
		} catch (IOException
			| DocumentException e) {
			LOGGER.error(e.getMessage());
			throw new SQLException("SQL加载失败 原因: " + e.getMessage());
		}
	}

	/** Initialize DataSource */
	private void initializeDataSource(ApplicationContext applicationContext) {
		/** Read DataSource from ApplicationContext */
		Map<String, DataSource> dataSources = applicationContext.getBeansOfType(DataSource.class);

		/** Create JdbcTemplate for every datasource */
		for (String s : dataSources.keySet()) {
			DataSource dataSource = dataSources.get(s);
			MAP_DATASOURCE_TO_JDBCTEMPLATE.put(s, new JdbcTemplate(dataSource));
		}

		/** 建立查询与JdbcTemplate的对应关系 */
		for (String key : MAP_QUERY_TO_DATASOURCE.keySet()) {
			MAP_QUERY_TO_JDBCTEMPLATE.put(key, MAP_DATASOURCE_TO_JDBCTEMPLATE.get(MAP_QUERY_TO_DATASOURCE.get(key)));
		}
	}

	// ----------------------- CACHE MAP

	/** SQL TO JdbcTemplate */
	private static final Map<String, JdbcTemplate> MAP_QUERY_TO_JDBCTEMPLATE      = new HashMap<>();
	/** 每个查询和DataSource名称的对应关系 中间缓存 */
	private static final Map<String, String>       MAP_QUERY_TO_DATASOURCE        = new HashMap<>();
	/** 每个DataSource和JdbcTemplate的对应关系 中间缓存 */
	private static final Map<String, JdbcTemplate> MAP_DATASOURCE_TO_JDBCTEMPLATE = new HashMap<>();
	/** 每个实体名称到jdbcTemplate的对应关系 */
	private static final Map<String, JdbcTemplate> MAP_ENTITY_TO_JDBCTEMPLATE     = new HashMap<>();

	/** SQL Cache */
	private static Map<String, String> SQL_CACHE = new HashMap<>();

	// ----------------------- CONSTANT

	/** Default Datasource name */
	private static final String DEFAULT_DATASOURCE = "dataSource";
	/** MAX_LO in Hilo */
	private static final Long   MAX_LO             = 1L;
	/** Default Query File Path */
	private static final String QUERY_FILE_PATH    = "classpath*:/query/*.query.xml";
	/** VELOCITY Processor Name */
	private static final String VELOCITY_PROCESSOR = "velocity";

	// ----------------------- CONSTANT OBJECT

	private static final PathMatchingResourcePatternResolver RESOURCE_PATTERN_RESOLVER = new PathMatchingResourcePatternResolver();

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@SuppressWarnings("unchecked")
	@Override
	public <T> T queryOneByCondition(Class<T> resultClass, String key, Object condition) {
		this.buildEntityCache(resultClass);

		JdbcTemplate jdbcTemplate = this.getJdbcTemplate(key);
		/** Get SQL statement from cache */
		String sql = this.getCachedSQL(key);
		/** Format by velocity */
		String realSql = this.velocityFormatter(sql, condition);
		/** 返回查询结果 */
		if (ENTITY_NAME_CACHE.containsKey(resultClass.getCanonicalName())) {
			try {
				return this.buildEntity(resultClass, jdbcTemplate.queryForMap(realSql));
			} catch (EmptyResultDataAccessException ignored) {
				return null;
			}
		}

		try {
			if (Long.class.equals(resultClass)) {
				return (T) jdbcTemplate.queryForObject(realSql, Long.class);
			}

			if (Integer.class.equals(resultClass)) {
				return (T) jdbcTemplate.queryForObject(realSql, Integer.class);
			}

			if (Double.class.equals(resultClass)) {
				return (T) jdbcTemplate.queryForObject(realSql, Double.class);
			}

			if (String.class.equals(resultClass)) {
				return (T) jdbcTemplate.queryForObject(realSql, String.class);
			}

			return jdbcTemplate.queryForObject(realSql,
			                                   this.buildRowMapper(resultClass));
		} catch (EmptyResultDataAccessException ignored) {

		}

		return null;
	}

	@Override
	public Map<String, Object> queryOneByCondition(String key,
	                                               Object condition) {
		JdbcTemplate jdbcTemplate = this.getJdbcTemplate(key);

		String sql = this.getCachedSQL(key);

		String realSql = this.velocityFormatter(sql, condition);

		try {
			return jdbcTemplate.queryForMap(realSql);
		} catch (EmptyResultDataAccessException ignored) {

		}

		return new HashMap<>();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> List<T> queryListByCondition(Class<T> resultClass,
	                                        String key,
	                                        Object condition) {
		this.buildEntityCache(resultClass);

		JdbcTemplate jdbcTemplate = this.getJdbcTemplate(key);

		String sql = this.getCachedSQL(key);

		String realSql = this.velocityFormatter(sql, condition);

		if (ENTITY_NAME_CACHE.containsKey(resultClass.getCanonicalName())) {
			try {
				List<Map<String, Object>> rs = jdbcTemplate.queryForList(realSql);

				List<T> result = new LinkedList<>();

				for (Map<String, Object> r : rs) {
					result.add(this.buildEntity(resultClass, r));

				}

				return result;
			} catch (EmptyResultDataAccessException ignored) {
				return new LinkedList<>();
			}
		}

		try {
			if (Long.class.equals(resultClass)) {
				return (List<T>) jdbcTemplate.queryForList(realSql, Long.class);
			}

			if (Integer.class.equals(resultClass)) {
				return (List<T>) jdbcTemplate.queryForList(realSql, Integer.class);
			}

			if (Double.class.equals(resultClass)) {
				return (List<T>) jdbcTemplate.queryForList(realSql, Double.class);
			}

			if (String.class.equals(resultClass)) {
				return (List<T>) jdbcTemplate.queryForList(realSql, String.class);
			}

			return jdbcTemplate.query(realSql,
			                          this.buildRowMapper(resultClass));
		} catch (EmptyResultDataAccessException ignored) {
			/** 没有查出来不作处理 */
		}

		return new LinkedList<>();
	}

	@Override
	public List<Map<String, Object>> queryListByCondition(String key,
	                                                      Object condition) {
		JdbcTemplate jdbcTemplate = this.getJdbcTemplate(key);

		String sql = this.getCachedSQL(key);

		String realSql = this.velocityFormatter(sql, condition);

		try {
			return jdbcTemplate.queryForList(realSql);
		} catch (EmptyResultDataAccessException ignored) {

		}
		return new LinkedList<>();
	}

	@Override
	public <T> Page<T> queryPageByCondition(Class<T> resultClass,
	                                        Pageable pageable,
	                                        String key,
	                                        Object condition) {
		this.buildEntityCache(resultClass);

		JdbcTemplate jdbcTemplate = this.getJdbcTemplate(key);

		String sql = this.getCachedSQL(key);

		String realSql = this.velocityFormatter(sql, condition);

		List<T> content = new LinkedList<>();

		if (ENTITY_NAME_CACHE.containsKey(resultClass.getCanonicalName())) {
			try {
				List<Map<String, Object>> rs = jdbcTemplate.queryForList(realSql);

				for (Map<String, Object> r : rs) {
					content.add(this.buildEntity(resultClass, r));
				}
			} catch (EmptyResultDataAccessException ignored) {

			}
		} else {
			try {
				content = jdbcTemplate.query(this.pageableFormatter(realSql, pageable),
				                             this.buildRowMapper(resultClass));
			} catch (EmptyResultDataAccessException ignored) {

			}
		}

		Long count = 0L;

		try {
			count = jdbcTemplate.queryForObject(this.countFormatter(realSql), Long.class);
		} catch (EmptyResultDataAccessException ignored) {

		}
		return new PageImpl<>(content, pageable, count);
	}

	@Override
	public Page<Map<String, Object>> queryPageByCondition(Pageable pageable,
	                                                      String key,
	                                                      Object condition) {
		JdbcTemplate jdbcTemplate = this.getJdbcTemplate(key);

		String sql = this.getCachedSQL(key);

		String realSql = this.velocityFormatter(sql, condition);

		List<Map<String, Object>> content = new LinkedList<>();

		try {
			content = jdbcTemplate.queryForList(this.pageableFormatter(realSql, pageable));
		} catch (EmptyResultDataAccessException ignored) {

		}

		Long count = 0L;

		try {
			count = jdbcTemplate.queryForObject(this.countFormatter(realSql), Long.class);
		} catch (EmptyResultDataAccessException ignored) {

		}

		return new PageImpl<>(content, pageable, count);
	}

	@Override
	public <T> Page<T> queryPageByCondition(Class<T> resultClass,
	                                        Pageable pageable,
	                                        String key,
	                                        String countKey,
	                                        Object condition) {
		this.buildEntityCache(resultClass);

		JdbcTemplate jdbcTemplate = this.getJdbcTemplate(key);

		String sql = this.getCachedSQL(key);

		String realSql = this.velocityFormatter(sql, condition);

		List<T> content = new LinkedList<>();

		if (ENTITY_NAME_CACHE.containsKey(resultClass.getCanonicalName())) {
			try {
				List<Map<String, Object>> rs = jdbcTemplate.queryForList(realSql);

				for (Map<String, Object> r : rs) {
					content.add(this.buildEntity(resultClass, r));
				}
			} catch (EmptyResultDataAccessException ignored) {

			}
		} else {
			try {
				content = jdbcTemplate.query(this.pageableFormatter(realSql, pageable),
				                             this.buildRowMapper(resultClass));
			} catch (EmptyResultDataAccessException ignored) {

			}
		}

		String countSql = this.getCachedSQL(countKey);

		String realCountSql = this.velocityFormatter(countSql, condition);

		Long count = 0L;

		try {
			count = jdbcTemplate.queryForObject(realCountSql, Long.class);

		} catch (EmptyResultDataAccessException ignored) {

		}

		return new PageImpl<>(content, pageable, count);
	}

	@Override
	public Page<Map<String, Object>> queryPageByCondition(Pageable pageable,
	                                                      String key,
	                                                      String countKey,
	                                                      Object condition) {
		JdbcTemplate jdbcTemplate = this.getJdbcTemplate(key);

		String sql = this.getCachedSQL(key);

		String realSql = this.velocityFormatter(sql, condition);

		List<Map<String, Object>> content = new LinkedList<>();

		try {
			content = jdbcTemplate.queryForList(this.pageableFormatter(realSql, pageable));
		} catch (EmptyResultDataAccessException ignored) {

		}

		String countSql = this.getCachedSQL(countKey);

		String realCountSql = this.velocityFormatter(countSql, condition);

		Long count = 0L;

		try {
			count = jdbcTemplate.queryForObject(realCountSql, Long.class);
		} catch (EmptyResultDataAccessException ignored) {

		}

		return new PageImpl<>(content, pageable, count);
	}

	@Override
	public <T> Page<T> queryPageByCondition(Class<T> resultClass,
	                                        Pageable pageable,
	                                        String key,
	                                        Object condition,
	                                        String countKey,
	                                        Object countCondition) {
		this.buildEntityCache(resultClass);

		JdbcTemplate jdbcTemplate = this.getJdbcTemplate(key);

		String sql = this.getCachedSQL(key);

		String realSql = this.velocityFormatter(sql, condition);

		List<T> content = new LinkedList<>();

		if (ENTITY_NAME_CACHE.containsKey(resultClass.getCanonicalName())) {
			try {
				List<Map<String, Object>> rs = jdbcTemplate.queryForList(realSql);

				for (Map<String, Object> r : rs) {
					content.add(this.buildEntity(resultClass, r));
				}
			} catch (EmptyResultDataAccessException ignored) {

			}
		} else {
			try {
				content = jdbcTemplate.query(this.pageableFormatter(realSql, pageable),
				                             this.buildRowMapper(resultClass));
			} catch (EmptyResultDataAccessException ignored) {

			}
		}

		String countSql = this.getCachedSQL(countKey);

		String realCountSql = this.velocityFormatter(countSql, countCondition);

		Long count = 0L;

		try {
			count = jdbcTemplate.queryForObject(realCountSql, Long.class);

		} catch (EmptyResultDataAccessException ignored) {

		}

		return new PageImpl<>(content, pageable, count);
	}

	/**
	 * 把原SQL改为COUNT型.
	 * @param realSql 原SQL
	 * @return String
	 * @since 15/3/8 下午2:04
	 */
	private String countFormatter(String realSql) {
		/** 对ORDER BY进行处理 */
		Pattern orderBy = Pattern.compile("(?i)(.*ORDER BY)", Pattern.DOTALL);
		Matcher matcher = orderBy.matcher(realSql);
		if (matcher.find()
		    && !StringUtils.upperCase(realSql.substring(matcher.group(1).length())).contains("WHERE")) {
			realSql = realSql.substring(0, StringUtils.lastIndexOf(StringUtils.upperCase(realSql), "ORDER BY"));
		}

		return realSql.replaceFirst("(?i)SELECT[\\s\\S]*?FROM",
		                            "SELECT COUNT(1) FROM");
	}

	/**
	 * 对SQL进行分页的格式化.
	 * @param realSql  原SQL
	 * @param pageable 分页
	 * @return String
	 * @since 15/3/8 下午2:08
	 */
	private String pageableFormatter(String realSql, Pageable pageable) {
		return realSql
		       + " LIMIT "
		       + pageable.getOffset()
		       + ", "
		       + pageable.getPageSize();
	}

	/**
	 * 对原始SQL进行Velocity格式化.
	 * @param originSql 原始SQL
	 * @param condition 查询条件
	 * @return String
	 * @since 15/3/8 下午1:58
	 */
	private String velocityFormatter(String originSql, Object condition) {
		StringWriter writer = new StringWriter();

		Velocity.evaluate(this.buildVelocityContext(condition), writer, "", originSql);

		return writer.toString();
	}

	/**
	 * 填充条件到VelocityContext当中.
	 * @param condition 查询条件
	 * @return VelocityContext
	 * @throws ComplexQueryException
	 * @since 15/3/8 下午1:57
	 */
	@SuppressWarnings("unchecked")
	private VelocityContext buildVelocityContext(Object condition) throws ComplexQueryException {
		VelocityContext context = new VelocityContext();

		if (condition == null) {
			return context;
		}

		Map map = new HashMap();

		if (condition instanceof Map) {
			map = (Map) condition;
		} else {
			PropertyDescriptor[] propertyDescriptors = ReflectUtils.getBeanGetters(condition.getClass());

			for (PropertyDescriptor descriptor : propertyDescriptors) {
				Method getter = descriptor.getReadMethod();

				if (getter != null) {
					map.put(descriptor.getDisplayName(),
					        ReflectionUtils.invokeMethod(descriptor.getReadMethod(), condition));
				}
			}
		}

		for (Object o : map.keySet()) {
			Object data = map.get(o);

			if (data instanceof String) {
				context.put(String.valueOf(o), this.escapeSQL(String.valueOf(data)));
				continue;
			}

			if (data instanceof Date) {
				context.put(String.valueOf(o), this.escapeSQL(DATE_FORMAT.format(data)));
				continue;
			}

			/** 转换为SQL IN 格式 */
			if (data instanceof Collection) {
				context.put(String.valueOf(o), this.buildINStatement((Collection) data));
				continue;
			}

			context.put(String.valueOf(o), data);
		}

		return context;
	}

	/** 列表转换为IN格式 */
	private String buildINStatement(Collection list) {
		StringBuilder builder = new StringBuilder();

		int i = list.size();

		for (Object o : list) {
			i--;

			if (o instanceof String) {
				o = this.escapeSQL((String) o);
			}

			builder.append(o);

			if (i == 0) {
				continue;
			}

			builder.append(",");


		}

		return builder.toString();
	}

	/** 转义符号, 防止SQL注入 */
	private String escapeSQL(String s) {
		return "'" + StringUtils.replace(s, "\'", "\'\'") + "'";
	}

	/**
	 * 把resultSet转换到实体
	 * @param resultClass 要返回的实体类
	 * @return RowMapper
	 * @since 15/3/8 下午1:55
	 */
	private <T> RowMapper<T> buildRowMapper(final Class<T> resultClass) {
		return new RowMapper<T>() {
			@Override
			public T mapRow(ResultSet rs, int rowNum) throws SQLException {
				return this.row(rs, resultClass);
			}

			/** 实际使用的属性映射方法 */
			private <F> F row(ResultSet rs, Class<F> resultClass) {
				/** 只拿SET方法 */
				Method[] methods = ReflectionUtils.getAllDeclaredMethods(resultClass);

				try {
					F t = resultClass.newInstance();

					for (Method set : methods) {
						if (!isSetMethod(set)) {
							continue;
						}

						/** 默认SET方法只有一个参数 */
						Class<?> aClass = set.getParameterTypes()[0];

						/** 目前无法封装列表数据 */
						if (Collection.class.isAssignableFrom(aClass)) {
							continue;
						}

						/** 普通的递归 */
						if (!this.isPrimitiveType(aClass)) {
							ReflectionUtils.invokeMethod(set, t, this.row(rs, aClass));
							continue;
						}

						String className = aClass.getSimpleName();
						String propertyName = this.getPropertyName(set);

						try {
							Object value;
							if ("Date".equals(className)) {
								value = rs.getTimestamp(propertyName);
							} else {
								value = rs.getObject(propertyName);
							}

							if (rs.wasNull()) {
								continue;
							}

							set.invoke(t, this.translate(aClass, value));
						} catch (SQLException ignored) {
							/** 忽略不存在的属性 */
						}
					}

					return t;

				} catch (InstantiationException
					| IllegalAccessException
					| InvocationTargetException e) {
					LOGGER.error(e.getMessage());
					throw new ComplexQueryException("实体转换发生异常: " + e.getMessage());
				}
			}

			/** 检查是否是基本类型 */
			@SuppressWarnings("RedundantIfStatement")
			private boolean isPrimitiveType(Class clazz) {
				if (Number.class.isAssignableFrom(clazz)) {
					return true;
				}

				if (String.class.equals(clazz)) {
					return true;
				}

				if (Date.class.equals(clazz)) {
					return true;
				}

				if (Timestamp.class.equals(clazz)) {
					return true;
				}

				if (Object.class.equals(clazz)) {
					return true;
				}

				if (Boolean.class.equals(clazz)) {
					return true;
				}

				return false;
			}

			private boolean isSetMethod(Method method) {
				return method.getName().startsWith("set");
			}

			private String getPropertyName(Method method) {
				return org.springframework.util.StringUtils.uncapitalize(method.getName().replaceFirst("set", ""));
			}

			private Object translate(Class setParam, Object value) {
				if (String.class.equals(setParam)) {
					return String.valueOf(value);
				}

				if (Long.class.equals(setParam)) {
					return Long.valueOf(String.valueOf(value));
				}

				if (Integer.class.equals(setParam)) {
					return Integer.valueOf(String.valueOf(value));
				}

				if (Double.class.equals(setParam)) {
					return Double.valueOf(String.valueOf(value));
				}

				if (Float.class.equals(setParam)) {
					return Float.valueOf(String.valueOf(value));
				}

				if (Boolean.class.equals(setParam)) {
					return Boolean.valueOf(String.valueOf(value));
				}

				if (Short.class.equals(setParam)) {
					return Short.valueOf(String.valueOf(value));
				}

				return value;
			}
		};
	}

	/**
	 * 得到被缓存的SQL.
	 * @param key SQL key
	 * @return String
	 * @throws ComplexQueryException
	 * @since 15/3/8 下午1:54
	 */
	private String getCachedSQL(String key) throws ComplexQueryException {
		String sql = SQL_CACHE.get(key);

		if (sql == null) {
			throw new ComplexQueryException("该 " + key + " SQLKey不存在, 请检查");
		}

		return sql;
	}


	// ----------------------- THE ANCIENT GOD'S BLOOD

	/** 实体名称和主键的对应关系 */
	private static final Map<String, String>              PRIMARY_KEY_CACHE = new HashMap<>();
	/** 实体名称和表名的对应关系 */
	private static final Map<String, String>              ENTITY_NAME_CACHE = new HashMap<>();
	/** 实体里面属性和列名的对应关系 */
	private static final Map<String, Map<String, String>> COLUMN_NAME_CACHE = new HashMap<>();

	/** 检查实体的主键是否有值 */
	@SuppressWarnings("unchecked")
	private boolean hasPrimaryKey(Class entity, Object target) {
		if (target instanceof List) {
			List<Object> targets = (List<Object>) target;

			return this.hasPrimaryKey(entity, targets);
		}

		String idProperty = PRIMARY_KEY_CACHE.get(entity.getCanonicalName());

		PropertyDescriptor descriptor =
			org.springframework.beans.BeanUtils.getPropertyDescriptor(entity, this.translate(idProperty));

		Method get = descriptor.getReadMethod();

		Object o = ReflectionUtils.invokeMethod(get, target);

		return o != null;
	}

	/** 检查实体的主键是否有值 */
	private boolean hasPrimaryKey(Class entity, List<Object> targets) {
		boolean init = true;

		for (Object target : targets) {
			init = init & this.hasPrimaryKey(entity, target);
		}

		return init;
	}

	/** 把数据库字段名转换为属性名称 */
	private String translate(String source) {
		String[] split = source.split("_");

		if (split.length == 1) {
			return source;
		}

		String id = split[0];

		for (int i = 1; i < split.length; i++) {
			id += StringUtils.capitalize(split[i]);
		}

		return id;
	}

	/** 建立有关实体的缓存 */
	private void buildEntityCache(Class entity) {
		String canonicalName = entity.getCanonicalName();

		/** 跳过已经建立过缓存的实体 */
		if (ENTITY_NAME_CACHE.containsKey(canonicalName)) {
			return;
		}

		Table entityAnnotation = (Table) entity.getAnnotation(Table.class);

		if (entityAnnotation == null) {
			return;
		}

		ENTITY_NAME_CACHE.put(canonicalName, entityAnnotation.name());

		Field[] fields = entity.getDeclaredFields();

		Method[] methods = entity.getMethods();

		Map<String, String> columnMap = COLUMN_NAME_CACHE.get(canonicalName);
		if (columnMap == null) {
			columnMap = new HashMap<>();
			COLUMN_NAME_CACHE.put(canonicalName, columnMap);
		}

		for (Field field : fields) {
			Id id = field.getAnnotation(Id.class);
			Column column = field.getAnnotation(Column.class);
			Version version = field.getAnnotation(Version.class);

			/** 跳过没有@COLUMN和@VERSION的属性 */
			if (id != null && column != null) {
				PRIMARY_KEY_CACHE.put(canonicalName, column.name());
			}

			if (version != null) {
				columnMap.put("version", "version");
			}

			if (column != null) {
				columnMap.put(field.getName(), column.name());
			}
		}

		for (Method method : methods) {
			Id id = method.getAnnotation(Id.class);
			Column column = method.getAnnotation(Column.class);
			Version version = method.getAnnotation(Version.class);

			/** 跳过没有@COLUMN和@VERSION的属性 */
			if (id != null && column != null) {
				PRIMARY_KEY_CACHE.put(canonicalName, column.name());
			}

			if (version != null) {
				columnMap.put("version", "version");
			}

			if (column != null) {
				columnMap.put(StringUtils.uncapitalize(method.getName().substring(3)), column.name());
			}
		}
	}

	private JdbcTemplate getJdbcTemplate(String sqlKey) {
		JdbcTemplate jdbcTemplate = MAP_QUERY_TO_JDBCTEMPLATE.get(sqlKey);

		if (jdbcTemplate == null) {
			return MAP_DATASOURCE_TO_JDBCTEMPLATE.get(DEFAULT_DATASOURCE);
		}

		return jdbcTemplate;
	}

	private JdbcTemplate getJdbcTemplate(Class entity) {
		JdbcTemplate jdbcTemplate = MAP_ENTITY_TO_JDBCTEMPLATE.get(entity.getCanonicalName());

		if (jdbcTemplate == null) {
			return MAP_DATASOURCE_TO_JDBCTEMPLATE.get(DEFAULT_DATASOURCE);
		}

		return jdbcTemplate;
	}

	private <T> T buildEntity(Class<T> entity, Map<String, Object> resultSet) {
		Map<String, String> columnMap = COLUMN_NAME_CACHE.get(entity.getCanonicalName());

		T target;

		try {
			target = entity.newInstance();

		} catch (InstantiationException
			| IllegalAccessException e) {
			return null;
		}

		PropertyDescriptor[] descriptors = ReflectUtils.getBeanSetters(entity);

		for (PropertyDescriptor descriptor : descriptors) {
			try {

				Method setter = descriptor.getWriteMethod();

				Class setParam = setter.getParameterTypes()[0];

				Object value = resultSet.get(columnMap.get(descriptor.getName()));

				if (value != null) {
					setter.invoke(target, this.translate(setParam, value));
				}
			} catch (Exception ignored) {
				ignored.printStackTrace();
			}
		}

		return target;
	}

	private Object translate(Class setParam, Object value) {
		if (String.class.equals(setParam)) {
			return String.valueOf(value);
		}

		if (Long.class.equals(setParam)) {
			return Long.valueOf(String.valueOf(value));
		}

		if (Integer.class.equals(setParam)) {
			return Integer.valueOf(String.valueOf(value));
		}

		if (Double.class.equals(setParam)) {
			return Double.valueOf(String.valueOf(value));
		}

		if (Float.class.equals(setParam)) {
			return Float.valueOf(String.valueOf(value));
		}

		if (Boolean.class.equals(setParam)) {
			return Boolean.valueOf(String.valueOf(value));
		}

		if (Short.class.equals(setParam)) {
			return Short.valueOf(String.valueOf(value));
		}

		return value;
	}
}
