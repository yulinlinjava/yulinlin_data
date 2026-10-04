package com.yulinlin.jdbc.mysql.parse;

import com.yulinlin.data.core.wrapper.IChildrenWrapper;

import java.util.Arrays;
import java.util.LinkedList;

import java.util.List;
import java.util.StringJoiner;

public class MysqlJsonUtil {

    private static ThreadLocal<LinkedList<IChildrenWrapper>> threadLocal = ThreadLocal.withInitial(() -> new LinkedList<>());

    public static String symbol="->";


    public static void push(IChildrenWrapper p){
        threadLocal.get().add(p);
    }

    public static void pop(){
        threadLocal.get().removeLast();
    }

    public static IChildrenWrapper get(){
      return    threadLocal.get().getLast();
    }

    public static boolean isEmpty(){
        return    threadLocal.get().isEmpty();
    }

    public static int size(){
        return    threadLocal.get().size();
    }
    private static String buildContext(){
        StringJoiner joiner = new StringJoiner(symbol);
        LinkedList<IChildrenWrapper> list = threadLocal.get();
        for (IChildrenWrapper wrapper : list) {
            if(wrapper.getName() != null){
                joiner.add(wrapper.getName());
            }
        }
        if(joiner.length() > 0){
            return joiner.toString();
        }
        return null;
    }



    public      static List<String> json_path(String path){




        String[] split = path.split(symbol);

        StringJoiner sb = new StringJoiner(".");
        sb.add("$");
        for(int i = 1;i<split.length;i++){
            String s = split[i];
            sb.add(s);
        }


        return Arrays.asList(split[0],sb.toString());

    }


    public    static    String  json_where(String path){
        String s = buildContext();
        if(s == null){
            return path;
        }
        path = buildContext()+symbol+path;
        List<String> strings = json_path(path);
        return "JSON_EXTRACT("+strings.get(0)+",'"+strings.get(1)+"')";

    }

    public static void main(String[] args) {


    }

}
