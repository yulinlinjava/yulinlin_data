package com.yulinlin.data.lang.cache;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.yulinlin.data.lang.util.ThreadUtil;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class ExpiryMap<K, V> {


    private volatile Queue<V> expiredQueue = new ConcurrentLinkedQueue<>();


    // 用于统计或执行更复杂的清理任务
    private final Consumer< Queue<V>> consumer;


    private final Cache<K, V> cache;

    private static int MaxSize = 2*10000;

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "caffeine-cleaner");
        t.setDaemon(true);
        return t;
    });
    public ExpiryMap(
            ) {
        this(1,TimeUnit.MINUTES,MaxSize,null);
    }

    public ExpiryMap(
            Consumer<Queue<V>> onRemoval) {

        this(1,TimeUnit.MINUTES,MaxSize,onRemoval);
    }

    public ExpiryMap(long duration, TimeUnit unit,
                     long maximumSize,
                     Consumer<Queue<V>> consumer) {
        this(duration,unit,maximumSize,0,consumer);
    }

    public ExpiryMap(long duration, TimeUnit unit,
                              long maximumSize,int randomTtl,
                              Consumer<Queue<V>> consumer) {

        this.consumer=consumer;

        this.cache = Caffeine.newBuilder()


                .expireAfter(new Expiry<K, V>() {
                    @Override
                    public long expireAfterCreate(K key, V value, long currentTime) {
                        long baseTtl = unit.toNanos(duration);            // 5 分钟
                        long jitter =0;
                        if(randomTtl > 0){
                            jitter =  ThreadLocalRandom.current().nextLong(0, TimeUnit.SECONDS.toNanos(randomTtl)); // 0~30s 抖动
                        }

                        return baseTtl + jitter;
                    }
                    @Override public long expireAfterUpdate(K key, V value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                    @Override public long expireAfterRead(K key, V value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .maximumSize(maximumSize)
                .removalListener((K key, V value, RemovalCause cause) -> {
                    if(this.consumer != null){
                        this.expiredQueue.add(value);
                    }
                })
                .build();

        scheduler.scheduleAtFixedRate(() -> {
            // 1) 强制触发过期淘汰
            cache.cleanUp();

            // 2) 批量处理过期条目
            if (consumer != null && expiredQueue.size() > 0) {
                Queue<V> toProcess = expiredQueue;
                this.expiredQueue = new ConcurrentLinkedQueue<>();
                ThreadUtil.submit(() -> {
                    consumer.accept(toProcess);
                });
            }
        }, 1, 1, TimeUnit.MINUTES);


    }




    public void put(K key, V value) {
        cache.put(key, value);
    }

    public V get(K key) {
        return cache.getIfPresent(key);
    }

    public V get(K key, Function<K,V> func) {
        return cache.get(key,func);
    }


    public void invalidate(K key) {
        cache.invalidate(key);
    }

    public void shutdown() {
        cache.cleanUp();

        scheduler.shutdownNow();

        expiredQueue.clear();
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }


    public void cleanUp(){
        cache.cleanUp();
    }


    public static void main(String[] args)throws Exception {
        ExpiryMap expiryMap = new ExpiryMap<>(2,TimeUnit.SECONDS,10,0,(v) -> {
            Collection c = v;
        });
        Object o = expiryMap.get("1", (k) -> {
            return "1";
        });

        Thread.sleep(3*1000);

        expiryMap.put("1",2);
         int s = 0;
    }
}
