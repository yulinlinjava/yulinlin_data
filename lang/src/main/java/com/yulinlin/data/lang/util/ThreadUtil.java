package com.yulinlin.data.lang.util;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;

/**
 * 线程工具
 */
@Slf4j
public class ThreadUtil {




    public static ThreadPoolExecutor threadPoolExecutor  =threadPoolExecutor();

    public static ScheduledExecutorService scheduledExecutorService =scheduledThreadPoolExecutor();



    public static void startThread(int sleepTime,
                                   int delayTime,
                                   Callable<Boolean> runnable){

        Thread thread = new Thread(() -> {
            while (true){
                boolean ok = true;
                try {
                    ok =  runnable.call();

                }catch (Exception e){
                    log.error("定时任务异常",e);
                }
                if(ok){
                    try {
                        Thread.sleep(sleepTime);
                    }catch (Exception e){

                    }

                }

            }
        });
        thread.setDaemon(true);

        if(delayTime > 0){
            submit(() -> {
                thread.start();
            },delayTime);
        }else {
            thread.start();
        }

    }

    public static void startThread(int time,Callable<Boolean> runnable){

        startThread(time,time,runnable);

    }

    private static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor(){
        int core =  Runtime.getRuntime().availableProcessors();
        ScheduledThreadPoolExecutor pool  = new ScheduledThreadPoolExecutor(core);
        return  pool;
    }

    private static ThreadPoolExecutor threadPoolExecutor(){
        int core =  Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor pool  = new ThreadPoolExecutor(core,core*4,30,TimeUnit.MINUTES,new LinkedBlockingDeque<>());
        return  pool;
    }




    public static <T> Future<T> submit(Callable<T> task) {
        Future future =  threadPoolExecutor.submit(task);

        return future;
    }
    public static <T> Future<T> submit(Runnable task) {
        Future future =  threadPoolExecutor.submit(task);

        return future;
    }
    public static void  submit(Runnable task,int delay) {
        ThreadDelayUtil.submit(task,delay);
    }



    public static ScheduledFuture<?> schedule(Runnable task, long delay) {
        return scheduledExecutorService.scheduleAtFixedRate(task, delay, delay, TimeUnit.MILLISECONDS);
    }



    public static ThreadLocal<Map<Object, Object>> threadLocal = ThreadLocal.withInitial(() -> {
        return new HashMap<>();
    });

    public static void set(Object key, Object value) {
        Map<Object, Object> map = threadLocal.get();
        map.put(key, value);
    }

    public static <E> E get(Object key,Callable<E> task){

        return (E)threadLocal.get().computeIfAbsent(key,(k) -> {
            try {
                return task.call();
            }catch (Exception e){
                throw new RuntimeException(e);
            }
        });

    }
    public static <E> E get(Object key){
        Map<Object, Object> map = threadLocal.get();

        return (E)map.get(key);
    }

    public static <E> E del(Object key){
        Map<Object, Object> map = threadLocal.get();
        if (map != null) {
            return (E)map.remove(key);
        }
        return null;
    }

    public static void clear(){
        threadLocal.get().clear();
    }

}