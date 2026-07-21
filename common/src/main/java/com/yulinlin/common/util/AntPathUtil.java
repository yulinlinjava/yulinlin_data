package com.yulinlin.common.util;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.Map;

/**
 * ant 路径工具类
 */
public class AntPathUtil {

    private static PathMatcher pathMatcher = new AntPathMatcher();


    /**
     * 是否匹配 指定模式
     * @param patten
     * @param path
     * @return
     */
    public static boolean match(String patten,String path){
        return pathMatcher.match(patten,path);
    }


    /**
     * 获取路径参数
     * @param patten
     * @param path
     * @return
     */
    public static Map<String,String> extractUriTemplateVariables(String patten,String path){
        return pathMatcher.extractUriTemplateVariables(patten,path);
    }
}
