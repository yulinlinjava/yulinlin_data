package com.yulinlin.data.lang.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yulinlin.data.lang.util.DateTime;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Date;


public class DateTimeStrJsonSerializer extends JsonSerializer<Object> {


    public static String getTimestampStr(long time){

        long d =time;
        long now = System.currentTimeMillis();

        long diff = (now - d) / 1000;

        long minute = 60;

        long hour = 60*minute;
        long day = 24*hour;
        long week = 7*day;
        long month = 4*week;
        long year = 12*month;
        if (diff < minute) {
            return "刚刚";
        } else if (diff < hour) {
            return Math.round(diff / minute) + "分钟前";
        } else if (diff < day) {
            return Math.round(diff / hour) + "小时前";
        } else if (diff < week) {
            return Math.round(diff / day)+"天前";
        } else if (diff < month) {
            return Math.round(diff / week)+"周前";
        } else if (diff < year) {
            return Math.round(diff / month)+"月前";
        }else {
            long i = DateTime.now().getYear() -DateTime.date(time).getYear();
            return i+"年前";
        }



    }



    @Override
    public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        String timestampStr="";
        if(o instanceof String){
             o =DateTime.parse(o.toString());
        }
        if(o instanceof Date){
            Date date = (Date) o;
            timestampStr = getTimestampStr(date.getTime());
        }else if(o instanceof DateTime){
            DateTime date = (DateTime) o;
            timestampStr = getTimestampStr(date.getTime());
        }else if(o instanceof LocalDateTime){
            LocalDateTime date = (LocalDateTime) o;
            timestampStr = getTimestampStr(DateTime.date(date).getTime());
        }

        jsonGenerator.writeString(timestampStr);
    }
}
