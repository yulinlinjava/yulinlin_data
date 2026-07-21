package com.yulinlin.data.lang.util;

import java.util.ArrayList;
import java.util.Collection;

/**
 *
 */
public class ListString<E> extends ArrayList<E>  implements CodeObject<String> {

    public static String splitStr=",";

    public ListString() {
    }


    public static ListString<String> ofString(String str){
        ListString<String> listString = new ListString();

            listString.decode(str,String.class);


        return listString;
    }


    public ListString(Collection<? extends E> c) {
        super(c);
    }

    public void decode(String o , Class<E> clazz){
        if(o == null){
            return;
        }
        for (String s : o.split(ListString.splitStr)) {
            if(s.isEmpty()){
                continue;
            }
            Object v = s;
            try {
                if(clazz == Integer.class){
                    v = Integer.parseInt(s);
                }else if(clazz == Long.class){
                    v = Long.parseLong(s);
                }else if(clazz == Double.class){
                    v = Double.parseDouble(s);
                }else if(clazz == Byte.class){
                    v = Byte.parseByte(s);
                }else if(clazz == Float.class){
                    v = Float.parseFloat(s);
                }else if(clazz == String.class){

                }
            }catch (Exception e){
                throw new RuntimeException("值转换失败,请检查字段泛型:"+s);
            }

            this.add((E)v);
        }

    }

    @Override
    public void decode(Object value) {
    }

    public String encode () {
        StringBuffer sb = new StringBuffer();
        for (Object s : this) {
            if(sb.length() > 0){
                sb.append(splitStr);
            }
            sb.append(s);
        }
        return sb.toString();
    }

}
