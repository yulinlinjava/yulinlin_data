package com.yulinlin.starter.util;

import com.yulinlin.data.lang.util.StringUtil;
import lombok.SneakyThrows;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;

/**
 * 非web不可用
 */
public class RequestUtil {


    public static String getBasePath(){

        HttpServletRequest request = getRequest();

        StringBuffer sb = new StringBuffer();
        sb.append( request.getScheme());
        sb.append("://");
        sb.append(request.getServerName());
        if(request.getServerPort() != 80){
            sb.append(":");
            sb.append(request.getServerPort());
        }
        sb.append(   request.getContextPath());

        return sb.toString();
    }


    @SneakyThrows
    public static byte[] getRequestBody(){


        HttpServletRequest request =  getRequest();


        InputStream inputStream = request.getInputStream();

       return StreamUtils.copyToByteArray(inputStream);




    }
    public static HttpServletRequest getRequest () {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();

        HttpServletRequest request = servletRequestAttributes.getRequest();
        return request;
    }

    public static String get(String key,String defaultValue){
        String value = get(key);
        if(StringUtil.isNull(value)){
          return defaultValue;
        }
        return value;
    }

    public static boolean isWeb(){
        try {
            getRequest();
            return true;
        }catch (Exception e){
            return false;
        }
    }
    public static String get(String key){
        if(isWeb() == false){
            return null;
        }
        HttpServletRequest request =   getRequest();

        String value =  request.getParameter(key);
        if(StringUtil.isNull(value)){
            value =  request.getHeader(key);
        }

        if(StringUtil.isNull(value)) {
            Cookie[] cs =  request.getCookies();
            if(cs != null){
                for (Cookie cookie : cs) {
                    if(cookie.getName().equals(key)){
                        return  cookie.getValue();
                    }
                }
            }
        }

        return value;
    }

    public static String getIp(){
        if(isWeb() == false){
            return null;
        }
        HttpServletRequest request  = RequestUtil.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if(!StringUtils.isEmpty(ip)) {
            ip = ip.split(",")[0];
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_CLUSTER_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_FORWARDED");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_VIA");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("REMOTE_ADDR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        return ip;
    }

    public static String getUrl(){
        HttpServletRequest request = ((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
        return request.getRequestURI();
    }


    public static String getHeader(String key){
        if(isWeb() == false){
            return null;
        }
        HttpServletRequest request = ((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
        return request.getHeader(key);

    }

    public static final String X_FORWARDED_HOST = "x-forwarded-host";
    public static final String X_FORWARDED_PROTO = "x-forwarded-proto";



    private static String getHeaderValue(String key){
        String proto = RequestUtil.getHeader(key);
        if(proto == null){
            return null;
        }
        String[] split = proto.split(",");
       return split[0];
    }

    public static String getRquestDomain(){

        String authority = getHeaderValue(":authority");
        String scheme = getHeaderValue(":scheme");

        if(StringUtil.isNull(authority)){
            authority =  getHeaderValue(X_FORWARDED_HOST);
        }
        if(StringUtil.isNull(scheme)){
            scheme = getHeaderValue(X_FORWARDED_PROTO);
        }

        return scheme+"://"+authority;
    }
}
