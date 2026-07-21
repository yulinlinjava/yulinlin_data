package com.yulinlin.data.lang.cache;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public  class ResourcePool<T> {
    private final Semaphore semaphore;
    private final Queue<T> pool;
    private final Supplier<T> factory;

    public ResourcePool( Supplier<T> factory) {
        this(8,factory);
    }

    public ResourcePool(int maxSize, Supplier<T> factory) {
        this.semaphore = new Semaphore(maxSize);
        this.pool = new LinkedList<>();
        this.factory = factory;
    }

    public T acquire() throws InterruptedException {
        semaphore.acquire();
        synchronized (pool) {
            return pool.isEmpty() ? factory.get() : pool.poll();
        }
    }

    public void release(T object) {
        synchronized (pool) {
            pool.offer(object);
        }
        semaphore.release();
    }


    public void close(){

        while (!pool.isEmpty()){
            T poll = pool.poll();
            close(poll);
        }
    }


    protected void close(T obj){

    }

}