import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * @auth huyi
 * @date 2020/08/14 16:14
 * @Description
 *
 * MysqlUtil是我基於mysql-connector-java包裝的mysql連接工具
 * Mysqlutil is a MySQL connection tool packaged by mysql-connector-java
 *
 * 其核心思想是將mysql查詢來的資料，暫存於List<Map<String, Object>>這個中間媒介中
 * Its core idea is to temporarily store the data from MySQL query in the intermediate medium list<map<string,Object>>
 *
 * 可以輕鬆存放聯表查詢而來的數據
 * It can easily store the data from the joint table query
 *
 * 對於使用mysql函數查詢到的數據，也能輕鬆存放
 * It can also easily store the data found by using MySQL function
 *
 * 另外對於對象的存取，List<Map<String, Object>>的操作都有提供簡單的支援
 * In addition,list<map<string,Object>> provides simple support for object access
 *
 * 最重要的是，框架小，全集中於此檔案，接入修改維護都簡單，支持修改以便在地化
 * The most important thing is that the framework is small. All in this file, the access, modification and maintenance are simple. It supports modification for localization
 *
 * 唯一的缺點就是使用到了java8有的stream，所以java8以下的版本可能需要一翻修改
 * The only drawback is that it uses the stream of java8, so the version below java8 may need to be modified
 */
public class MysqlUtil {
    private String driverClassName;
    private String url;
    private String username;
    private String password;
    private Connection conn;

    public MysqlUtil(Connection conn) {
        this.conn = conn;
    }

    public MysqlUtil(String driverClassName, String url, String username, String password) {
        this.driverClassName = driverClassName;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public void getConn() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        try {
            Class.forName(driverClassName);
            conn = DriverManager.getConnection(url, username, password);
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    //--------------------------------------------------------------------------------------------------------------------------------------

    /**
     * 普通查詢
     * @param sql     sql語句
     * @param objects 與?對應的物件
     * @throws SQLException 查詢異常
     */
    synchronized public List<Map<String, Object>> select(String sql, Object... objects) throws SQLException {
        List<Map<String, Object>> data = new ArrayList<>();
        select(data, sql, objects);
        return data;
    }

    /**
     * 用map+convertString查詢
     * @param map   資料源
     * @param sql   sql
     * @param convertString "columnName=mapField"
     */
    synchronized public List<Map<String, Object>> select(Map<String, Object> map,String sql,String convertString) throws SQLException {
        Map<String, String> convertMap = convertStringToMap2(convertString);
        ArrayList<Object> paras = new ArrayList<>();
        convertMap.keySet().forEach(key->{paras.add(map.get(convertMap.get(key)));});
        return select(sql,paras.toArray());
    }

    /**
     * 自動生成sql語句
     * @param map   要比對的資料
     * @param table 要查詢的表
     * @return  查詢結果
     */
    synchronized public List<Map<String, Object>> select(Map<String, Object> map,String table) throws SQLException {
        List<String> columns = getTableColumns(table).stream().map(m ->(String) m.get("COLUMN_NAME")).collect(Collectors.toList());
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(map);
        String[] s = createWhereAndConvertString(list, table, true);
        StringBuilder sql = new StringBuilder("select ");
        sql.append(columns.toString().replaceAll("[\\[\\]]","`").replaceAll(", ","`,`"));
        sql.append(" from `").append(table).append("`");
        if(!s[0].equals("")){
            sql.append(" where ");
            //這邊要拼湊where
            ArrayList<Object> paras = new ArrayList<>();
            boolean isFirst = true;
            for (String s1 : s[0].split(",")) {
                if(isFirst){
                    isFirst=false;
                }else{
                    sql.append(" and ");
                }
                //大概都是單詞
                sql.append(s1).append("=?");
                paras.add(map.get(s1));
            }
            return select(sql.toString(),paras.toArray());
        }
        return select(sql.toString());
    }

    /**
     * 查詢並將結果放至data
     * @param data    資料要存放的地方
     * @param sql     sql語句
     * @param objects 與?對應的物件
     * @throws SQLException 查詢異常
     */
    synchronized public void select(List<Map<String, Object>> data, String sql, Object... objects) throws SQLException {
        checkConn();
        if (conn != null) {
            PreparedStatement psql = conn.prepareStatement(sql);
            for (int i = 0; i < objects.length; i++) {
                psql.setObject(i + 1, objects[i]);
            }
            ResultSet rs = psql.executeQuery();
            if (rs != null)
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    for (int i = 0; i < rs.getMetaData().getColumnCount(); i++) {
                        //這邊拿到資料了
                        String name = rs.getMetaData().getColumnName(i + 1);
                        String tag = name;
                        for (int j = 0; map.containsKey(tag); j++) {
                            tag = name + i;
                        }
                        try {
                            map.put(tag, rs.getObject(i + 1));
                        } catch (Exception e) {
                            map.put(tag, null);
                        }
                    }
                    data.add(map);
                }
        }
    }

    /**
     * 查詢，根據data的某些字段進行查詢，並把結果合併至data
     * @param data  資料
     * @param sql   sql
     * @param convertString 要查詢/整合的字段
     * @throws SQLException Exception
     */
    synchronized public void selectAndCombine(List<Map<String, Object>> data, String sql, String convertString) throws SQLException{
        Map<String, String> map = convertStringToMap2(convertString);
        //使用union嗎?那就使用union吧
        StringBuilder sqls = new StringBuilder();
        for (int i = 0; i < data.size(); i++) {
            sqls.append(" UNION ").append(sql);
        }

        sql = sqls.toString().replaceFirst(" UNION","");
        List<Object> paras = new ArrayList<>();
        data.forEach(i->{
            map.values().forEach(v->{
                paras.add(i.get(v));
            });
        });

        List<Map<String, Object>> data1 = select(sql, paras.toArray());
        data1=combineData(data,data1,convertString);
        data.clear();
        data.addAll(data1);
    }

    /**
     * insert自動處理，須要讀取information_schema.COLUMNS的權限
     * 只更新表所含有的欄位
     *
     * @param data                 要更新的資料
     * @param table                要更新的表
     * @param convertFirst         需要更新的columns是否以第一個資料為準(如果存放類型一樣，就用true)
     * @param onDuplicateKeyUpdate 是否在無法插入(重複主鍵、唯一鍵)時進行更新，如果資料含有多行唯一、主鍵，則容易造成死鎖
     * @return 更新成功數量
     */
    synchronized public int insert(List<Map<String, Object>> data, String table, boolean convertFirst, boolean onDuplicateKeyUpdate) {
        return insert(data, table, createConvertString(data, table, convertFirst), onDuplicateKeyUpdate);
    }

    /**
     * insert執行，需要插入權限
     *
     * @param data                 要插入的數據
     * @param table                要插入的表
     * @param convertString        要插入的字段、轉換字段,"要插入的欄位名=資料的欄位名"，同名可省略微"要插入的欄位名"
     *                             若是null，空字段，自動把所有的資料欄位拿去更新(注意，很可能會報錯)
     * @param onDuplicateKeyUpdate 是否在無法插入(重複主鍵、唯一鍵)時進行更新，如果資料含有多行唯一、主鍵，則容易造成死鎖
     * @return 更新成功數量
     */
    synchronized public int insert(List<Map<String, Object>> data, String table, String convertString, boolean onDuplicateKeyUpdate) {
        Object[] o = makeInsertSql(data, table, convertString, onDuplicateKeyUpdate);
        String sql = (String) o[0];
        //noinspection unchecked
        ArrayList<Object> paras = (ArrayList<Object>) o[1];
        try {
            PreparedStatement psql = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < paras.size(); i++) {
                psql.setObject(i + 1, paras.get(i));
            }
            return psql.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 執行insert，並且返回GeneratedKeys
     * @param data  要插入的資料
     * @param table 要插入的表
     * @param convertFirst 需要更新的columns是否以第一個資料為準(如果存放類型一樣，就用true)
     * @param onDuplicateKeyUpdate  是否在無法插入(重複主鍵、唯一鍵)時進行更新，如果資料含有多行唯一、主鍵，則容易造成死鎖
     * @return  GeneratedKeys
     */
    synchronized public Object[] insertAndReturnKey(List<Map<String, Object>> data, String table, boolean convertFirst, boolean onDuplicateKeyUpdate){
        return insertAndReturnKey(data, table, createConvertString(data, table, convertFirst), onDuplicateKeyUpdate);
    }

    /**
     * 執行insert，並且返回GeneratedKeys
     * @param data  要插入的資料
     * @param table 要插入的表
     * @param convertString 要插入的字段、轉換字段,"要插入的欄位名=資料的欄位名"，同名可省略微"要插入的欄位名"
     *                      若是null，空字段，自動把所有的資料欄位拿去更新(注意，很可能會報錯)
     * @param onDuplicateKeyUpdate  是否在無法插入(重複主鍵、唯一鍵)時進行更新，如果資料含有多行唯一、主鍵，則容易造成死鎖
     * @return  GeneratedKeys
     */
    synchronized public Object[] insertAndReturnKey(List<Map<String, Object>> data, String table, String convertString, boolean onDuplicateKeyUpdate){
        Object[] o = makeInsertSql(data, table, convertString, onDuplicateKeyUpdate);
        String sql = (String) o[0];
        //noinspection unchecked
        ArrayList<Object> paras = (ArrayList<Object>) o[1];
        try {
            PreparedStatement psql = conn.prepareStatement(sql.toString(), PreparedStatement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < paras.size(); i++) {
                psql.setObject(i + 1, paras.get(i));
            }
            psql.executeUpdate();
            ArrayList<Object> objects = new ArrayList<>();
            ResultSet rs = psql.getGeneratedKeys();
            if(rs!=null)
                while (rs.next()){
                    objects.add(rs.getObject(1));
                }
            return objects.toArray();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new Object[0];
    }

    /**
     * insert+update自動處理，須要讀取information_schema.COLUMNS的權限
     *
     * @param data         要插入與更新的資料
     * @param table        要插入與更新的表
     * @param convertFirst 需要更新的columns是否以第一個資料為準(如果存放類型一樣，就用true)
     * @return 更新成功的數量
     */
    synchronized public int insertAndUpdate(List<Map<String, Object>> data, String table, boolean convertFirst) {
        String[] s = createWhereAndConvertString(data, table, convertFirst);
        return insertAndUpdate(data, table, s[0], s[1]);
    }

    /**
     * insert+update，返回成功數量
     *
     * @param data          要插入與更新的資料
     * @param table         要插入與更新的表
     * @param whereString   作為where的column與對應的key(column=key,column2=key2),若是左右相同，可省略為(column,column2)
     * @param convertString 要更新的字段column與對應的key(column=key,column2=key2),若是左右相同，可省略為(column,column2)
     * @return 更新成功的數量
     */
    synchronized public int insertAndUpdate(List<Map<String, Object>> data, String table, String whereString, String convertString) {
        insert(data, table, whereString + "," + convertString, false);
        return update(data, table, whereString, convertString);
    }

    /**
     * update自動處理，須要讀取information_schema.COLUMNS的權限
     *
     * @param data  要插入與更新的資料
     * @param table 要插入與更新的表
     * @param convertFirst 需要更新的columns是否以第一個資料為準(如果存放類型一樣，就用true)
     * @return 成功更新的資料量
     */
    synchronized public int update(List<Map<String, Object>> data, String table, boolean convertFirst) {
        String[] s = createWhereAndConvertString(data, table, convertFirst);
        return update(data, table, s[0], s[1]);
    }

    /**
     * updata執行，須要更新權限
     *
     * @param data          要插入與更新的資料
     * @param table         要插入與更新的表
     * @param whereString   作為where的column與對應的key(column=key,column2=key2),若是左右相同，可省略為(column,column2)
     * @param convertString 要更新的字段column與對應的key(column=key,column2=key2),若是左右相同，可省略為(column,column2)
     * @return 更新成功的數量
     */
    synchronized public int update(List<Map<String, Object>> data, String table, String whereString, String convertString) {
        checkConn();
        Map<String, String> whereMap = convertStringToMap(whereString);
        Map<String, String> convertMap = convertStringToMap(convertString);
        StringBuilder sql = new StringBuilder("update `").append(table).append("` a join (");
        StringBuilder sb = new StringBuilder();
        List<Object> paras = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            sb.append(" union select ");
            if (i == 0) {
                sb.append(customToString(whereMap, "? as '@key'", ",")).append(",").append(customToString(convertMap, "? as '@key'", ","));
            } else {
                sb.append(customToString(whereMap, "?", ",")).append(",").append(customToString(convertMap, "?", ","));
            }
            int finalI = i;
            whereMap.keySet().forEach(key -> paras.add(data.get(finalI).get(whereMap.get(key))));
            convertMap.keySet().forEach(key -> paras.add(data.get(finalI).get(convertMap.get(key))));
        }
        sql.append(sb.toString().replaceFirst(" union ", "")).append(") b using(").append(customToString(whereMap, "@key", ","));
        sql.append(") set ").append(customToString(convertMap, "a.@key = if(isnull(b.@key),a.@key,b.@key)", ","));
        try {
            PreparedStatement psql = conn.prepareStatement(sql.toString());
            for (int i = 0; i < paras.size(); i++) {
                psql.setObject(i + 1, paras.get(i));
            }
            return psql.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 用Batch的方式來實現批量插入
     * @param data  要插入的data
     * @param table 要插入的表
     * @param convertFirst  需要更新的columns是否以第一個資料為準(如果存放類型一樣，就用true)
     * @return  每個Batch的成功數量
     */
    synchronized public int[] insertByBatch(List<Map<String, Object>> data, String table, boolean convertFirst) {
        return insertByBatch(data, table, createConvertString(data, table, convertFirst));
    }

    /**
     * 用Batch的方式來實現批量插入
     * @param data  要插入的data
     * @param table 要插入的表
     * @param convertString 要插入的字段、轉換字段,"要插入的欄位名=資料的欄位名"，同名可省略微"要插入的欄位名"
     *                      若是null，空字段，自動把所有的資料欄位拿去更新(注意，很可能會報錯)
     * @return  每個Batch的成功數量
     */
    synchronized public int[] insertByBatch(List<Map<String, Object>> data, String table, String convertString) {
        Map<String, String> convertMap = convertStringToMap(convertString);
        StringBuilder sql = new StringBuilder("insert into `").append(table).append("`(");
        sql.append(customToString(convertMap, "`@key`", ","));
        sql.append(") values (").append(customToString(convertMap, "?", ",")).append(");");
        try {
            PreparedStatement psql = conn.prepareStatement(sql.toString());
            for (int i = 0; i < data.size() * convertMap.size(); i++) {
                psql.clearParameters();
                for (String key : convertMap.keySet()) {
                    psql.setObject(i + 1,data.get(i).get(convertMap.get(key)));
                    i++;
                }
                psql.addBatch();
            }
            return psql.executeBatch();

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return new int[]{};
    }

    //--------------------------------------------------------------------------------------------------------------------------------------

    /**
     * 把對象轉為data
     *
     * @param objects 要存入的對象
     * @return data
     */
    public static List<Map<String, Object>> putObject(Object... objects) {
        List<Map<String, Object>> data = new ArrayList<>();
        putObject(data, objects);
        return data;
    }

    /**
     * 把對象存入data
     *
     * @param data    要保存的data
     * @param objects 要存入的對象列表
     */
    public static void putObject(List<Map<String, Object>> data, Object... objects) {
        for (Object obj : objects) {
            Map<String, Object> map = new HashMap<>();
            for (Field field : getFields(obj.getClass())) {
                String name = field.getName();
                String tag = name;
                for (int i = 0; map.containsKey(tag); i++) {
                    tag = name + i;
                }
                try {
                    map.put(tag, field.get(obj));
                } catch (IllegalAccessException e) {
                    map.put(tag, null);
                }
            }
            data.add(map);
        }
    }

    /**
     * 取出對象
     * @param data  資料源
     * @param clazz 類型
     */
    public static <C> List<C> getObject(List<Map<String, Object>> data,Class<C> clazz){
        Set<Field> fields = getFields(clazz);
        ArrayList<C> list = new ArrayList<>();
        data.forEach(map->{
            try {
                C c = clazz.newInstance();
                fields.forEach(field -> {
                    field.setAccessible(true);
                    try {
                        if(field.getType().equals(map.get(field.getName()).getClass())){
                            field.set(c,map.get(field.getName()));
                        }else {
                            if (field.getType().equals(String.class)) {
                                field.set(c, String.valueOf(map.get(field.getName())));
                            }
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                });
                list.add(c);
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        });
        return list;
    }

    /**
     * 把資料經過convertString轉換，輸出新的資料
     *
     * @param data          輸入的資料
     * @param convertString 轉換字串
     * @return 輸出結果資料
     */
    public static List<Map<String, Object>> convertData(List<Map<String, Object>> data, String convertString) {
        Map<String, String> convertMap = convertStringToMap(convertString);
        /*
        接下來怎麼做?這data不好轉換
        這樣吧，查看所有object，查看所有key，如果convertMap.get(key)是object有的值，就返回數值，否則返回Null
         */
        List<Map<String, Object>> newData = new ArrayList<>();
        for (Map<String, Object> datum : data) {
            Map<String, Object> map = new HashMap<>();
            for (String key : convertMap.keySet()) {
                map.put(key, datum.get(convertMap.get(key)));
            }
            newData.add(map);
        }
        return newData;
    }

    /**
     * 用默認的方式把大寫字母換成_小寫
     */
    public static void autoConvertData1(List<Map<String, Object>> data){
        Set<String> keySet = new HashSet<>();
        data.forEach(map -> keySet.addAll(map.keySet()));
        Map<String,String> convertMap = new HashMap<>();
        Pattern p = Pattern.compile("[A-Z]");
        keySet.forEach(key->{
            //把需要轉換的拿出來，不用轉換的留著
            Matcher m = p.matcher(key);
            String convertKey = key;
            while (m.find()){
                convertKey=convertKey.replaceAll(m.group(),"_"+String.valueOf((char)(m.group().charAt(0)-'A'+'a')));
            }
            convertMap.put(convertKey,key);
        });
        List<Map<String, Object>> newMap = convertData(data, customToString(convertMap, "@key=@value", ","));
        data.clear();
        data.addAll(newMap);
    }

    /**
     * 用默認的方法把_小寫換成大寫
     */
    public static void autoConvertData2(List<Map<String, Object>> data){
        Set<String> keySet = new HashSet<>();
        data.forEach(map -> keySet.addAll(map.keySet()));
        Map<String,String> convertMap = new HashMap<>();
        Pattern p = Pattern.compile("_[a-z]");
        keySet.forEach(key->{
            //把需要轉換的拿出來，不用轉換的留著
            Matcher m = p.matcher(key);
            String convertKey = key;
            while (m.find()){
                convertKey=convertKey.replaceAll(m.group(),String.valueOf((char)(m.group().charAt(1)+'A'-'a')));
            }
            convertMap.put(convertKey,key);
        });
        List<Map<String, Object>> newMap = convertData(data,customToString(convertMap,"@key=@value",","));
        data.clear();
        data.addAll(newMap);
    }

    /**
     * 把data轉為自定義的String
     *
     * @param data       要轉換的data
     * @param pattern    每一個key與value之間的表達語句，要放key的地方用@key表示，要放value的地方用@value表示
     *                   例如資料{name:ali}在"@key=?"的轉換下成為"name=?"
     * @param connector1 在Map<String,Object>中的每個key-value之間用甚麼連接
     * @param connector2 在List<Map>中的每個Map用甚麼來連接
     * @return 轉換完成的字串
     */
    public static String customToString(List<Map<String, Object>> data, String pattern, String connector1, String connector2) {
        if (pattern == null)
            pattern = "@key=@value";
        if (connector1 == null)
            connector1 = ",";
        if (connector2 == null)
            connector2 = ";";
        boolean isFirst = true;
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> datum : data) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(connector2);
            }
            boolean isFirst2 = true;
            StringBuilder sb2 = new StringBuilder();
            for (String key : datum.keySet()) {
                if (isFirst2) {
                    isFirst2 = false;
                } else {
                    sb2.append(connector1);
                }
                try {
                    sb2.append(pattern.replaceAll("@key", key).replaceAll("@value", datum.get(key).toString()));
                } catch (NullPointerException ignored) {
                    sb2.append(pattern.replaceAll("@key", key).replaceAll("@value", "null"));
                }
            }
            sb.append(sb2);
        }
        return sb.toString();
    }

    /**
     * 把map轉為自定義的String
     *
     * @param map       要轉換的map
     * @param pattern   每一個key與value之間的表達語句，要放key的地方用@key表示，要放value的地方用@value表示
     *                  例如資料{name:ali}在"@key=?"的轉換下成為"name=?"
     * @param connector 在Map<String,Object>中的每個key-value之間用甚麼連接
     * @return 轉換完成的字串
     */
    public static String customToString(Map<String, ?> map, String pattern, String connector) {
        if (pattern == null)
            pattern = "@key=@value";
        if (connector == null)
            connector = ",";
        boolean isFirst2 = true;
        StringBuilder sb2 = new StringBuilder();
        for (String key : map.keySet()) {
            if (isFirst2) {
                isFirst2 = false;
            } else {
                sb2.append(connector);
            }
            sb2.append(pattern.replaceAll("@key", key).replaceAll("@value", map.get(key).toString()));
        }
        return sb2.toString();
    }

    /**
     * 對data進行去重
     * @param data  要去重的資料
     */
    public static void deduplicateData(List<Map<String, Object>> data){
        HashSet<Map<String, Object>> set = new HashSet<>(data);
        data.clear();
        data.addAll(set);
    }

    /**
     * 對data的每個map進行挑選，返回挑選結果
     * @param data  資料源
     * @param action    對map進行篩選
     * @return  篩選為true的集合
     */
    public static List<Map<String, Object>> filterData(List<Map<String, Object>> data,MapAction action){
        return data.stream().filter(action::doAction).collect(Collectors.toList());
    }

    //--------------------------------------------------------------------------------------------------------------------------------------

    /**
     * 把convertString轉成map
     *
     * @param convertString 要轉換的convertString
     * @return map
     */
    private static Map<String, String> convertStringToMap(String convertString) {
        String[] strings = convertString.split(",");
        Map<String, String> map = new HashMap<>();
        putMap(map,strings);
        return map;
    }

    /**
     * 把convertString轉成map
     * @param convertString 要轉換的convertString
     * @return  有序map
     */
    private static Map<String, String> convertStringToMap2(String convertString){
        String[] strings = convertString.split(",");
        Map<String, String> map = new LinkedHashMap<>();
        putMap(map,strings);
        return map;
    }

    private static void putMap(Map<String, String> map,String[] strings){
        for (String string : strings) {
            String[] s = string.split("=");
            if (s.length != 2 || s[1].equals("")) {
                map.put(s[0], s[0]);
            } else {
                map.put(s[0], s[1]);
            }
        }
    }
    /**
     * 用cross的方法把兩個data組合
     * @param data1 資料1
     * @param data2 資料2
     * @param convertString 不同的資料轉換,"資料1字段=資料2字段"
     * @return  合併結果
     */
    private static List<Map<String, Object>> combineData(List<Map<String, Object>> data1,List<Map<String, Object>> data2,String convertString){
        Map<String, String> convertMap = convertStringToMap2(convertString);
        List<Map<String, Object>> data = new ArrayList<>();
        data1.forEach(m1->{
            data2.forEach(m2->{
                boolean flag=true;
                for (Map.Entry<String, String> entry : convertMap.entrySet()) {
                    if(m1.get(entry.getKey())==null){
                        if(m2.get(entry.getValue())!=null)
                            flag=false;
                    }else if(!m1.get(entry.getKey()).equals(m2.get(entry.getValue())))
                        flag=false;
                }
                if(flag){
                    HashMap<String, Object> newMap = new HashMap<>();
                    newMap.putAll(m1);
                    newMap.putAll(m2);
                    data.add(newMap);
                }
            });
        });
        return data;
    }

    private static Set<Field> getFields(Class clazz) {
        Set<Field> fields = new HashSet<>();
        fields.addAll(Arrays.asList(clazz.getFields()));
        fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
        fields = fields.parallelStream().filter(a -> !Modifier.isStatic(a.getModifiers()) && !Modifier.isFinal(a.getModifiers())).collect(Collectors.toSet());
        fields.forEach(a -> {
            a.setAccessible(true);
        });
        return fields;
    }

    private void checkConn() {
        if (conn == null)
            getConn();
    }

    private List<Map<String, Object>> getTableColumns(String tableName) {
        String sql = "SELECT COLUMN_NAME,COLUMN_KEY FROM information_schema.`COLUMNS` INNER JOIN (select database() as 'TABLE_SCHEMA') a  USING(TABLE_SCHEMA) WHERE table_name = ?;";
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            list = select(sql, tableName);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private String createConvertString(List<Map<String, Object>> data, String table, boolean convertFirst) {
        List<Map<String, Object>> list = getUseColumns(data, table, convertFirst);
        StringBuilder sb = new StringBuilder();
        list.forEach(map -> sb.append(",").append(map.get("COLUMN_NAME")));
        return sb.toString().replaceFirst(",", "");
    }

    private String[] createWhereAndConvertString(List<Map<String, Object>> data, String table, boolean convertFirst) {
        List<Map<String, Object>> list = getUseColumns(data, table, convertFirst);
        StringBuilder priSb = new StringBuilder();
        StringBuilder uniSb = new StringBuilder();
        StringBuilder sb = new StringBuilder();
        list.forEach(map -> {
            switch ((String) map.get("COLUMN_KEY")) {
                case "PRI":
                    priSb.append(",").append(map.get("COLUMN_NAME"));
                    break;
                case "UNI":
                    uniSb.append(",").append(map.get("COLUMN_NAME"));
                    break;
                default:
                    sb.append(",").append(map.get("COLUMN_NAME"));
            }
        });
        if (priSb.toString().equals("")) {
            return new String[]{uniSb.toString().replaceFirst(",", ""), sb.toString().replaceFirst(",", "")};
        } else {
            uniSb.append(sb.toString());
            return new String[]{priSb.toString().replaceFirst(",", ""), uniSb.toString().replaceFirst(",", "")};
        }
    }

    private List<Map<String, Object>> getUseColumns(List<Map<String, Object>> data, String table, boolean convertFirst) {
        checkConn();
        List<Map<String, Object>> list = new ArrayList<>();
        if (convertFirst) {
            list = getTableColumns(table).stream().filter(a -> data.get(0).containsKey((String) a.get("COLUMN_NAME"))).collect(Collectors.toList());
        } else {
            list = getTableColumns(table).stream().filter(a -> data.stream().anyMatch(b -> b.containsKey((String) a.get("COLUMN_NAME")))).collect(Collectors.toList());
        }
        return list;
    }

    private void readResultSet(ResultSet rs) throws SQLException {
        if (rs != null)
            while (rs.next()) {
                for (int i = 0; i < rs.getMetaData().getColumnCount(); i++) {
                    try {
                        System.out.println(rs.getMetaData().getColumnName(i + 1) + ":" + rs.getObject(i + 1));
                    } catch (NullPointerException ignored) {
                        System.out.println(rs.getMetaData().getColumnName(i + 1) + ":null");

                    }
                }
            }
    }

    private Object[] makeInsertSql(List<Map<String, Object>> data, String table, String convertString, boolean onDuplicateKeyUpdate){
        checkConn();
        Map<String, String> convertMap = MysqlUtil.convertStringToMap(convertString);
        StringBuilder sql = new StringBuilder("insert ignore into `").append(table).append("`(");
        ArrayList<Object> paras = new ArrayList<>();


//      這邊是insert-select的插入
//        sql.append(MysqlUtil.customToString(convertMap, "`@key`", ",")).append(") ");
//        StringBuilder select = new StringBuilder();
//        data.forEach(m->{
//            if(select.toString().equals("")){
//                select.append(" union select ").append(customToString(convertMap,"? as '@key'",","));
//            }else {
//                select.append(" union select ").append(customToString(convertMap,"?",","));
//            }
//            convertMap.forEach((k,v)->{paras.add(m.get(v));});
//        });
//        sql.append(select.toString().replaceFirst(" union ",""));

//        這邊是values (),()的插入
        sql.append(MysqlUtil.customToString(convertMap, "`@key`", ",")).append(") values ");
        for (int i = 0; i < data.size(); i++) {
            if (i != 0)
                sql.append(",");
            StringBuilder sb2 = new StringBuilder("(");
            for (String key : convertMap.keySet()) {
                sb2.append(",?");
                paras.add(data.get(i).get(convertMap.get(key)));
            }
            sb2.append(")");
            sql.append(sb2.toString().replaceFirst(",", ""));
        }


        if (onDuplicateKeyUpdate) {
            sql.append(" ON DUPLICATE KEY UPDATE ").append(MysqlUtil.customToString(convertMap, "@key=IF(ISNULL(VALUES(@key)),@key,VALUES(@key))", ","));
        }
        return new Object[]{sql.toString(),paras};
    }

    //--------------------------------------------------------------------------------------------------------------------------------------

    public interface MapAction{
        <K,V> boolean doAction(Map<K, V> map);
    }
}
