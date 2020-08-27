# MysqlUtil
##### 我本身不喜欢mybatis，而这个MysqlUtil就是一直以来陪伴我的小帮手，
##### 最近手头上终于有两周的空闲给他重新包装，看着重新包装过后的它，我迫不及待想与大家分享这个好帮手

### 在我眼中他好处:
##### 1.好修改:代码少量，问题好抓，有需求改就是了
##### 2.好配合:与现在主流的对象储存相反，使用list+map来装物件，突然来甚么都不怕
##### 3.不再区分资料量:不管查询结果是单还是多，都不用分开，就算没资料，也不会回传null，减少nullpointerException的可能
##### 4.好自由:对象与表不再死绑，不用再1对1，抛开该死的注解，一切都好自由

### 在我眼中还需要修改的地方:
##### 1.我希望在进行能够返回ID，但是目前只有慢速的批量插入可以办到
##### 2.我希望Map<String,Object>可以有个判断唯一或是去重或是条件插入的标准，可是双重回圈下来，至少是O(n^2)，不适合使用在大资料量
##### 3.总觉得在List<Map<String,Object>的处理上可以更加支持，象是我现在还没办法给key重新命名

寒轩也该结束了,现在开始介绍如何使用MysqlUtil
-----------------------------------------------------------------------------------------------------

#### 创造mysql联接:
  ##### MysqlUtil mysqlUtil = new MysqlUtil("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/test?        characterEncoding=UTF8&serverTimezone=Asia/Shanghai&useLocalSessionState=true","root", "root");

##### sql四件套，各位不陌生的吧，创造对象创造联接，多个对象联接多个数据库

  ##### List<Map<String, Object>> data = mysqlUtil.select("select * from entity_friend");
  ##### System.out.println(data);
  
#### 查询并打印对象

  ##### MysqlUtil mysqlUtil = new MysqlUtil("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/test?characterEncoding=UTF8&serverTimezone=Asia/Shanghai&useLocalSessionState=true","root", "root");
  ##### List<Map<String, Object>> data = mysqlUtil.select("select * from entity_friend");
  ##### List<Friend> list = MysqlUtil.getObject(data, Friend.class);
  ##### System.out.println(list);
  
#### 从data取出对象并打印

  ##### MysqlUtil mysqlUtil = new MysqlUtil("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/test?characterEncoding=UTF8&serverTimezone=Asia/Shanghai&useLocalSessionState=true","root", "root");
  ##### Friend friend = new Friend();
  ##### List<Map<String, Object>> data = MysqlUtil.putObject(friend, friend);
  ##### data=MysqlUtil.autoConvertData1(data);
  ##### System.out.println(mysqlUtil.insert(data, "entity_friend", true, false));
  
#### 把两个Friend对象放入data，把key的命名从object换成column之后，对entity_friend进行插入

#### 更多的自行翻翻，相信能给各位不少惊喜~
