package com.github;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Author: WhiteCosmos
 * Description: ComplexQueryInterface
 */
public interface ComplexQueryHelper {

	/**
	 * Query for one object.
	 * @param resultClass query result
	 * @param key         the sql key defined in the xml file
	 * @param condition   query condition(Java Bean)
	 * @return the result class instance.
	 */
	<T> T queryOneByCondition(Class<T> resultClass,
	                          String key,
	                          Object condition);

	/**
	 * Query for one object.
	 * @param key       the sql key defined in the xml file
	 * @param condition query condition(Java Bean)
	 * @return map contains key-value pair.
	 */
	Map<String, Object> queryOneByCondition(String key,
	                                        Object condition);

	/**
	 * Query for a list.
	 * @param resultClass query result
	 * @param key         the sql key defined in the xml file
	 * @param condition   query condition(Java Bean)
	 * @return the list contains result class instances.
	 */
	<T> List<T> queryListByCondition(Class<T> resultClass,
	                                 String key,
	                                 Object condition);

	/**
	 * Query for a list.
	 * @param key       the sql key defined in the xml file
	 * @param condition query condition(Java Bean)
	 * @return the list contains the map result.
	 */
	List<Map<String, Object>> queryListByCondition(String key,
	                                               Object condition);

	/**
	 * Query for page.
	 * @param resultClass query result
	 * @param pageable    PageRequest
	 * @param key         the sql key defined in the xml file
	 * @param condition   query condition(Java Bean)
	 * @return Page
	 */
	<T> Page<T> queryPageByCondition(Class<T> resultClass,
	                                 Pageable pageable,
	                                 String key,
	                                 Object condition);

	/**
	 * Query for page, has another sql for count.
	 * @param resultClass query result
	 * @param pageable    PageRequest
	 * @param key         the sql key defined in the xml file
	 * @param countKey    another sql key for count
	 * @param condition   query condition(Java Bean)
	 * @return Page
	 */
	<T> Page<T> queryPageByCondition(Class<T> resultClass,
	                                 Pageable pageable,
	                                 String key,
	                                 String countKey,
	                                 Object condition);

	/**
	 * Query for page.
	 * @param pageable  PageRequest
	 * @param key       the sql key defined in the xml file
	 * @param condition query condition(Java Bean)
	 * @return Page contains the map result
	 */
	Page<Map<String, Object>> queryPageByCondition(Pageable pageable,
	                                               String key,
	                                               Object condition);

	/**
	 * Query for page, has another sql for count.
	 * @param pageable  PageRequest
	 * @param key       the sql key defined in the xml file
	 * @param countKey  another sql key for count
	 * @param condition query condition(Java Bean)
	 * @return Page contains the map result
	 */
	Page<Map<String, Object>> queryPageByCondition(Pageable pageable,
	                                               String key,
	                                               String countKey,
	                                               Object condition);

	/**
	 * Query for page, has another sql for count.
	 * @param resultClass query result
	 * @param pageable    PageRequest
	 * @param key         the sql key defined in the xml file
	 * @param countKey    another sql key for count
	 * @param condition   query condition(Java Bean)
	 * @return Page
	 */
	<T> Page<T> queryPageByCondition(Class<T> resultClass,
	                                 Pageable pageable,
	                                 String key,
	                                 Object condition,
	                                 String countKey,
	                                 Object countCondition);
}
