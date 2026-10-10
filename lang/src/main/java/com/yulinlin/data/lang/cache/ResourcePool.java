package com.yulinlin.data.lang.cache;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
        this.semaphore = new Semaphore(maxSize);
        this.pool = new ConcurrentLinkedQueue<>();
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public T acquire() throws InterruptedException {
        semaphore.acquire();
        T resource = pool.poll();
        if (resource != null) return resource;
        try {
            return Objects.requireNonNull(factory.get(), "factory returned null");
        } catch (RuntimeException | Error error) {
            semaphore.release();
            throw error;
        }
    }

    public void release(T object) {
        pool.offer(Objects.requireNonNull(object, "object"));
        semaphore.release();
    }


    public void close(){

        T poll;
        while ((poll = pool.poll()) != null){
            close(poll);
        }
    }


    protected void close(T obj){

    }

}
