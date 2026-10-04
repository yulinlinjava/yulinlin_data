package com.yulinlin.data.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DbCache {

    long duration;

    TimeUnit unit;
    long maximumSize;

    int randomTtl;


    public DbCache() {
        this(15,TimeUnit.MINUTES,5*10000,60);
    }

    public DbCache(long duration, TimeUnit unit, long maximumSize, int randomTtl) {
        this.duration = duration;
        this.unit = unit;
        this.maximumSize = maximumSize;
        this.randomTtl = randomTtl;
    }

     final   Map<Class,Cache> map = new ConcurrentHashMap();

   public   <K,V> Cache<K,V> getCache(Class clazz){

      return map.computeIfAbsent(clazz,k -> {
           return Caffeine.newBuilder()
                   .maximumSize(maximumSize)
                   .expireAfter(randomExpiry(duration,unit,randomTtl))
                   .build();
       });
   }

   public static Expiry<Object,Object> randomExpiry(long duration, TimeUnit unit, int randomTtl){
       return new Expiry<Object,Object>() {
           @Override
           public long expireAfterCreate(Object key, Object value, long currentTime) {
               long baseTtl = unit.toNanos(duration);            // 5 分钟
               long jitter =0;
               if(randomTtl > 0){
                   jitter =  ThreadLocalRandom.current().nextLong(0, TimeUnit.SECONDS.toNanos(randomTtl)); // 0~30s 抖动
               }

               return baseTtl + jitter;
           }
           @Override public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
               return currentDuration;
           }
           @Override public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
               return currentDuration;
           }
       };
   }


    public void  putCache(Class<?> clazz, Cache cache){
        map.put(clazz,cache);
    }


   public <E> E get(Class<?> clazz, CacheKey key){

       E val =  (E)getCache(clazz).getIfPresent(key.getKey());
       return val;
   }

    public void put(Class<?> clazz,CacheKey key,Object val){

        getCache(clazz).put( key.getKey(),val);
    }

    public void update(Class clazz){
        Cache cache = map.get(clazz);
        if(cache != null){
            cache.invalidateAll();
        }
    }

}
