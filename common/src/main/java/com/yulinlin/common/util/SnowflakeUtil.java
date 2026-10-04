package com.yulinlin.common.util;


import com.yulinlin.data.lang.util.RandomUtil;
import com.yulinlin.data.lang.util.Snowflake;

public class SnowflakeUtil {


/*    private static  String codes="";

    static {

        for(int i = 'A';i<='Z';i++){
            codes+=(char)i;
        }
        for(int i = 'a';i<='z';i++){
            codes+=(char)i;
        }
        for(int i = 0;i<=9;i++){
            codes+=i;
        }
    }*/

    private  static Snowflake snowflake;

    static {



        snowflake= new Snowflake(RandomUtil.randomInt(31),RandomUtil.randomInt(31));
    }


    public static void setSnowflake(Snowflake snowflake) {
        SnowflakeUtil.snowflake = snowflake;
    }

    public static Snowflake getSnowflake(){

        return snowflake;
    }

    //随机编码
    public static String ramdonCode(int size){
        StringBuffer sb = new StringBuffer();
        for(int i =0;i<size;i++){
            sb.append(RandomUtil.randomChar());
        }
        return sb.toString();
    }


    public static String nextIdStr(){
        return getSnowflake().nextIdStr();
   }

    public static long nextId(){
        return getSnowflake().nextId();
    }

    public static void main(String[] args) {

        Snowflake snowflake = getSnowflake();
        System.out.println(snowflake.toString());
    }

}
