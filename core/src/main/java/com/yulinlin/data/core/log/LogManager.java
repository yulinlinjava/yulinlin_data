package com.yulinlin.data.core.log;


import com.yulinlin.data.core.parse.ParseResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志管理器
 */
public class LogManager {

    private List<LogPrint> list = new ArrayList<>();

    public LogManager register(List<LogPrint> list){
        for (LogPrint logPrint : list) {
            register(logPrint);
        }
        return this;
    }

    public LogManager register(LogPrint print){
        list.add(print);
        return this;
    }



    //执行成功
    public void success(long time,ParseResult request){

        for (LogPrint print : list) {
            if(print.isHandle(request)){
                print.success(time,request);
            }
        }

    }

    //执行失败
    public void error(Throwable e,  ParseResult request){

        for (LogPrint print : list) {
            if(print.isHandle(request)){
                print.error(e,request);
            }
        }
    }

}
