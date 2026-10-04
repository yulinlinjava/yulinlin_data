package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.parse.IParamsContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlNode  {

    private String sql;

    private List<Object> list;


    private IDataBuffer dataBuffer;

    private Integer hash;


    private static final String pattern0 = "(\\$\\{)(.*?)(})";

    private static final String pattern1 = "(#\\{)(.*?)(})";

    private static final Pattern tagPattern0 = Pattern.compile(pattern0);

    private static final Pattern tagPattern1 = Pattern.compile(pattern1);




    public SqlNode(String sql, IDataBuffer dataBuffer) {
        this.sql = sql;
        this.dataBuffer = dataBuffer;
        init();
        this.equalityKey = buildEqualityKey();

        if(this.equalityKey != null){
          this.equalityKey =   this.equalityKey.replace("limit0,1","");
        }


    }

    public SqlNode(String sql) {
        this.sql = sql;
    }



    private String equalityKey;


    private String buildEqualityKey() {

        String str = sql.toLowerCase().replaceAll("[\\s\\n\\r]+", " ");

        int whereIndex = str.indexOf(" where ");
        if (whereIndex == -1) return null;

        String whereClause = str.substring(whereIndex + 7).trim();
        if (whereClause.endsWith(";")) {
            whereClause = whereClause.substring(0, whereClause.length() - 1).trim();
        }

        // 禁止复合条件或非等值比较
        if (whereClause.contains(" and ")
                || whereClause.contains(" or ")
                || whereClause.contains(" not ")
                || whereClause.contains("!=")
                || whereClause.contains("<>")
                || whereClause.matches(".*[><]=?.*")) {
            return null;
        }

        // 判断是单个等值条件
        int eqCount = countOccurrences(whereClause, "=");
        if (eqCount == 1) {
            return whereClause.replaceAll("\\s+", ""); // 如 id=1
        }
        return null;
    }

    private static int countOccurrences(String str, String sub) {
        int count = 0, idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /**
     * $处理
     * @param params
     */
    private void before(  Map params ){
        String sql = this.sql;
        Matcher matcher= tagPattern0.matcher(sql);
        while (matcher.find()) {
            String str = matcher.group().replace(" ","");
           String key = str.substring(2,str.length() - 1);

            String value =  params.get(key).toString();

            sql =  sql.replace(str,value);
        }
        this.sql = sql;
    }

    /**
     * #处理
     * @param params
     */
    private void after(    Map params ){
        String sql = this.sql;
        Matcher matcher= tagPattern1.matcher(sql);

        while (matcher.find()) {

            String str = matcher.group().replace(" ","");
            String key = str.substring(2,str.length() - 1);

            Object value =  params.get(key);
            list.add(value);

        }
        this.sql = sql.replaceAll(pattern1," ? ");

    }


    public void init(){
        Map map =  dataBuffer.toMap();
        this.list = new ArrayList<>();
        this.before(map);
        this.after(map);
    }


    public String getSql() {
        return sql;
    }

    public List<Object> getList() {
        return list;
    }

    @Override
    public int hashCode() {
        if (this.hash == null) {
            StringBuilder sb = new StringBuilder();

            if (equalityKey != null) {
                sb.append(equalityKey);
                sb.append(list.get(list.size() - 1)); // 安全拼接，避免 null

            } else {
                sb.append(sql);
                for (Object obj : list) {
                    sb.append(obj); // 安全拼接，避免 null
                }
            }


            this.hash = sb.toString().hashCode();
        }
        return this.hash;
    }



}
