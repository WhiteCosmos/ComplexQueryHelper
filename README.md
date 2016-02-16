# ComplexQueryHelper

-----

The primary goal of the ComplexQueryHelper is to make it easier to build complex sql,
reduce the redundant code for query java bean based results.

# Features
-----

* Java Bean based query
* Velocity syntax support
* Easy to use API

# Quick Start
-----

Add the ComplexQueryHelper into your service.

```java
  @serivce
  public class FooServiceImpl implements FooService {
    @Autowired
    private ComplexQueryHelper helper;
  }
```

Write a foo.query.xml under **resources/query** folder

```xml
  <!DOCTYPE querys SYSTEM "query.dtd">
  <querys>
    <query key="queryUserNameByUserId" processor="velocity">
        <value><![CDATA[
            select u.name from user u where u.id = $userId
          ]]></value>
          <comments>
            query user's name by userId
          </comments>
    </query>
  </querys>
  ```
then

  ```java
    @serivce
    public class FooServiceImpl implements FooService {
      @Autowired
      private ComplexQueryHelper helper;

      public void foo() {
        Map<String, Object> condition = new HashMap<>();

        condition.put("userId", 1234L);

        String username = helper.queryOneByConditon(String.class, "queryUserNameByUserId", condition);
      }
    }
  ```
