package com.yulinlin.data.core.session;

import com.yulinlin.data.core.wrapper.IWrapperFactory;
import lombok.SneakyThrows;

import java.util.concurrent.Callable;

import java.util.concurrent.Callable;

public class SessionUtil {

    private static RouteSession routeSession;


    public SessionUtil(RouteSession loadBalance) {
        SessionUtil.routeSession = loadBalance;
    }


    public static RouteSession route(){
        return routeSession;
    }



    public static String nowSession(){
        return routeSession.session().group();

    }

    public static  <E> E callable(String code, Callable<E> callable){
        return routeSession.callable(code,callable);
    }

    public static IWrapperFactory getWrapperFactory(){
        return routeSession.getWrapperFactory();

    }

}
