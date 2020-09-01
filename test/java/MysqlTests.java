
import entity.User;
import org.junit.Test;

import java.sql.SQLException;
import java.util.*;


public class MysqlTests {
    private MysqlUtil mysqlUtil = new MysqlUtil("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/test? characterEncoding=UTF8&serverTimezone=Asia/Shanghai&useLocalSessionState=true", "root", "root");

    /*
    mysql測試
     * 單表
     ** 查
     ** 增
     ** 改
     * 聯表
     ** 查
     ** 增
     ** 改
    對象測試
     * 取出對象
     * 放入對象
    資料更動測試
     * 不另外測試
     */
    @Test
    public void singleSelect() {
        try {
            System.out.println(mysqlUtil.select("select * from account"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void singleInsert() {
        Random r =new Random();
        List<Map<String,Object>> data = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            Map<String,Object> m = new HashMap<>();
//            m.put("id",r.nextInt(255));
            m.put("account",r.nextInt(255));
            m.put("score",r.nextInt(255));
            data.add(m);
        }
        System.out.println(mysqlUtil.insert(data, "account", true, false));
    }

    @Test
    public void singleInsertAndUpdate(){
        Random r =new Random();
        List<Map<String,Object>> data = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            Map<String,Object> m = new HashMap<>();
            m.put("id",i+1);
            m.put("account",r.nextInt(255));
            m.put("score",r.nextInt(255));
            data.add(m);
        }
        System.out.println(mysqlUtil.insertAndUpdate(data, "account", true));
    }

    @Test
    public void singleUpdate() {
        Random r =new Random();
        List<Map<String,Object>> data = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            Map<String,Object> m = new HashMap<>();
            m.put("id",i+1);
            m.put("account",r.nextInt(255));
            m.put("score",r.nextInt(255));
            data.add(m);
        }
        System.out.println(mysqlUtil.update(data, "account", true));
    }


    @Test
    public void multipleSelect() {
        try {
            System.out.println(mysqlUtil.select("select * from account"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * user.account_id=account.id
     * 不給定id，account沒唯一鍵
     */
    @Test
    public void multipleInsert1() {
        Random r =new Random();
        List<Map<String,Object>> data = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            Map<String,Object> m = new HashMap<>();
            m.put("user_name",r.nextInt(65535));
            m.put("account",r.nextInt(255));
            m.put("score",r.nextInt(255));
            data.add(m);
        }
        //先插入account表
        Object[] key = mysqlUtil.insertAndReturnKey(data, "account", true, false);

        //如果一對一成功的話(如果account表有唯一字段，或者是插入時就已經有設定好id了，那要另外處理)
        if(data.size()==key.length){
            for (int i = 0; i < data.size(); i++) {
                data.get(i).put("account_id",key[i]);
            }
            System.out.println(mysqlUtil.insert(data, "user", true, false));
        }
    }


    /**
     * user.account_id=account.id
     * 給定id，account沒唯一鍵
     */
    @Test
    public void multipleInsert2() {
        Random r =new Random();
        List<Map<String,Object>> data = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            Map<String,Object> m = new HashMap<>();
            m.put("user_name",r.nextInt(65535));
            m.put("account_id",i+1);
            m.put("account",r.nextInt(255));
            m.put("score",r.nextInt(255));
            data.add(m);
        }
        mysqlUtil.insert(data,"account","id=account_id,account,score",false);
        mysqlUtil.insert(data,"user",true,false);
    }

    /**
     * user.account_id=account.id
     */
    @Test
    public void multipleUpdate() {
        Random r =new Random();
        List<Map<String,Object>> data = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            Map<String,Object> m = new HashMap<>();
            m.put("user_name",r.nextInt(65535));
            m.put("account_id",i+1);
            m.put("account",r.nextInt(255));
            m.put("score",r.nextInt(255));
            data.add(m);
        }
        mysqlUtil.update(data,"account","id=account_id","account,score");
        mysqlUtil.update(data,"user",true);
    }

//--------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    public void getObject(){
        Random r =new Random();
        List<Map<String,Object>> data = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            Map<String,Object> m = new HashMap<>();
            m.put("id",i+1);
            m.put("user_name",r.nextInt(65535));
            m.put("account_id",i+1);
            m.put("account",r.nextInt(255));
            m.put("score",r.nextInt(255));
            data.add(m);
        }
        System.out.println(MysqlUtil.getObject(data, User.class));
    }
}
