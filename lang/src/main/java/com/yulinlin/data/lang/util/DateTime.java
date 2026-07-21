package com.yulinlin.data.lang.util;

import org.springframework.util.NumberUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * plus* 都是自增
 * with 都是设置
 */
public  class DateTime {

    private LocalDateTime localDateTime;

    private static List<String> formats = Arrays.asList(
            "yyyy-MM-dd HH:mm:ss",
            "yyyyMMddHHmmss",
            "yyyy-MM-dd",
            "yyyyMMdd",
            "yyyy-MM",
            "yyyyMM",
            "yyyy"
    );

    private DateTime() {
        this(LocalDateTime.now());
    }

    private DateTime(Date date) {


        this.localDateTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        // this.localDateTime =        date.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    private DateTime(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        this.localDateTime =  LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }


    private DateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }


    public LocalDateTime toLocalDateTime(){
        return localDateTime;
    }

    public long getTime(){
        ZoneId zone = ZoneId.systemDefault();
        long timestamp = localDateTime.atZone(zone).toInstant().toEpochMilli();
        return timestamp;
    }

    public long getTime(String offsetId){
        long time = localDateTime.atZone(ZoneOffset.of(offsetId)).toInstant().toEpochMilli();
        return time;
    }

    public Date toDate(){
        return new Date(getTime());
    }

    @Override
    public String toString() {
        return this.toString("yyyy-MM-dd HH:mm:ss");
    }

    public String toString(String format) {
        DateTimeFormatter formatter =  DateTimeFormatter.ofPattern(format);
        return formatter.format(localDateTime);
    }

    public  int  getYear(){
        return   localDateTime.getYear();
    }
    public  int  getMonth(){
        return   localDateTime.getMonthValue();
    }
    public  int  getHour(){
        return   localDateTime.getHour();
    }
    public  int  getMinute(){
        return   localDateTime.getMinute();
    }
    public  int  getSecond(){
        return   localDateTime.getSecond();
    }

    public  int  getDayOfYear(){
        return   localDateTime.getDayOfYear();
    }
    public  int  getDayOfMonth(){
        return   localDateTime.getDayOfMonth();
    }
    public  DayOfWeek  getDayOfWeek(){
        return   localDateTime.getDayOfWeek();
    }
    public DateTime plusYears(int add){
        return plus(add,ChronoUnit.YEARS);
    }

    public DateTime plusMonths(int add){
        return plus(add,ChronoUnit.MONTHS);
    }

    public DateTime plusDays(int add){
        return plus(add,ChronoUnit.DAYS);
    }
    public DateTime plusHours(int add){
        return plus(add,ChronoUnit.HOURS);
    }
    public DateTime plusMinutes(int add){
        return plus(add,ChronoUnit.MINUTES);
    }

    public DateTime plusSeconds(int add){
        return plus(add,ChronoUnit.SECONDS);
    }

    /**
     * 设置为今天第几月份
     * @param set
     * @return
     */
    public DateTime withDayOfYear(int set){
        LocalDateTime time = this.localDateTime.withDayOfYear(set);
        return new DateTime(time);
    }

    public DateTime withDayOfMonth(int set){
        LocalDateTime time = this.localDateTime.withDayOfMonth(set);
        return new DateTime(time);
    }

    public DateTime withHour(int set){
        LocalDateTime time = this.localDateTime.withHour(set);
        return new DateTime(time);
    }

    public DateTime withMinute(int set){
        LocalDateTime time = this.localDateTime.withMinute(set);
        return new DateTime(time);
    }

    public DateTime withNano(int set){
        LocalDateTime time = this.localDateTime.withNano(set);
        return new DateTime(time);
    }
    public DateTime withSecond(int set){
        LocalDateTime time = this.localDateTime.withSecond(set);
        return new DateTime(time);
    }

    public DateTime plus(int add, ChronoUnit unit){
        LocalDateTime plus = localDateTime.plus(add, unit);
        return new DateTime(plus);
    }

    public static ThreadLocal<Map<String,SimpleDateFormat>> formatMap = ThreadLocal.withInitial(HashMap::new);


    public boolean before(DateTime when){

        return getTime() < when.getTime();
    }


    public boolean after(DateTime when){
        return getTime() > when.getTime();
    }


    public DateTime beginOfDay(){
         return this.withHour(00)
                .withSecond(00)
                .withMinute(00);
    }


    public DateTime endOfDay(){
        return this.withHour(23)
                .withSecond(59)
                .withMinute(59);
    }

    public DateTime beginOfYear(){
        return this
                .withDayOfYear(1)
                .beginOfDay();
    }
    public DateTime endOfYear(){
        return this
                .beginOfYear()
                .plusYears(1)
                .plusDays(-1)
                .endOfDay()
                ;
    }

    public DateTime beginOfMonth(){
        return this.
                withDayOfMonth(1)
                .beginOfDay();
    }
    public DateTime endOfMonth(){
        return beginOfMonth().plusMonths(1).plusDays(-1).endOfDay();
    }



    public static DateTime parse(CharSequence charSequence, String format){


        try {
            SimpleDateFormat dateFormat = formatMap.get().computeIfAbsent(format, k -> new SimpleDateFormat(k));
            Date parse = dateFormat.parse(charSequence.toString());
            return new DateTime(parse);
        }catch (ParseException e){
            throw new RuntimeException(e);
        }


    }


    public static boolean isNumber(String str) {
        for(int i=0;i<str.length();i++){
            int chr=str.charAt(i);
            if(chr<48 || chr>57)
                return false;
        }
        return true;


    }

    public static boolean isDate(String str) {

        if(str.length() < 20){
            if(str.contains("-") && str.contains(":")){
                return true;
            }else if(str.contains("/") && str.contains(":")){
                return true;
            }
        }
        if(isNumber(str)){
            if(str.length() == 10){
                return true;
            }else if(str.length() == 13){
                return true;
            }
        }
        return false;
    }


    public static DateTime parse(String str){

        if(isNumber(str)){
            if(str.length() == 10){
                long time = Long.parseLong(str);
                return new DateTime(time*1000);
            }else if(str.length() == 13){
                long time = Long.parseLong(str);
                return new DateTime(time);
            }
        }

        for (String format : formats) {
            if(format.length() == str.length()){
                return parse(str,format);
            }
        }
        throw new RuntimeException("不支持解析日期："+str);
    }



    public static DateTime date(){
        return now();
    }
    public static DateTime date(LocalDateTime date){
        return new DateTime(date);
    }

    public static DateTime date(Date date){
        return new DateTime(date);
    }

    public static DateTime date(long date){
        return new DateTime(date);
    }

    public static DateTime now(){
        return new DateTime();
    }



    public static void main(String[] args) {

        DateTime dateTime1 = now().beginOfDay();
        DateTime dateTime3 = now().endOfDay();

        DateTime dateTime = now().plusMonths(1).beginOfMonth();

        DateTime dateTime4 = now().beginOfMonth();

        DateTime dateTime6 = now().endOfMonth();

        DateTime dateTime2 = now().beginOfYear();

        DateTime dateTime5 = now().endOfYear();

        int a = 0;
        //    DateTime parse = parse("0131","yyyyMMdd");

    }

}
