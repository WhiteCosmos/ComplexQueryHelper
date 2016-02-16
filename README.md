# ComplexQueryHelper

The primary goal of the ComplexQueryHelper is to make it easier to build complex sql,
reduce the redundant code for query java bean based results.

# Features

* Java Bean based query
* Velocity syntax support
* Easy to use API

# Quick Start

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
## Using Java Bean Or Map As Condition
You can write you own java bean as condition, notice that the bean property must correspond to the property you write in the sql.

```java
public class UserQueryBean {
  private Long userId;
  private String username;

  // ignore getter and setter
}
```
So you write sql like this:

```xml
<querys>
        <query key="queryUserNameByUserBean" processor="velocity">
            <value><![CDATA[
                SELECT
                    u.name
                FROM
                    user u
                WHERE
                    1 = 1
                    #if ($userId)
                    AND
                    u.id = $userId
                    #end
                    #if ($username)
                    AND
                    u.username LIKE $username
                    #end
                ]]></value>
            <comments>
                query user's name by UserBean
            </comments>
        </query>
    </querys>
  ```

then
```java
UserQueryBean bean = new UserQueryBean("a user");

String username = helper.queryOneByConditon(String.class, "queryUserNameByUserBean", bean);
```

Same as:

```java
Map<String, Object> condition = new HashMap<>();
condition.put("username", "a user");

String username = helper.queryOneByConditon(String.class, "queryUserNameByUserBean", condition);
```
> I suggested that when you have many properties, it's better to encapsulate them to a java bean.

## Deal with group statement
When you use `group by` statement, things are different, `select count(1)` will not work when sql has a `group by ` , in this case you should write another count sql.
