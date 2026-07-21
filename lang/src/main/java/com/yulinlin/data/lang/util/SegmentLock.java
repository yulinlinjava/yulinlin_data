package com.yulinlin.data.lang.util;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SegmentLock {

    // 锁段数组，管理多个段锁
    private final Lock[] locks;

    // 锁段的数量
    private final int segmentCount;

    // 默认分段数量，可以根据实际场景调整
    private static final int DEFAULT_SEGMENT_COUNT = 64;

    public SegmentLock() {
        this(DEFAULT_SEGMENT_COUNT);
    }

    public SegmentLock(int segmentCount) {
        this.segmentCount = segmentCount;
        this.locks = new ReentrantLock[segmentCount];

        for (int i = 0; i < segmentCount; i++) {
            locks[i] = new ReentrantLock();

        }
    }

    /**
     * 获取分段锁
     * @param key 锁的标识（可以是任意类型，通常是操作的数据）
     * @return 锁
     */
    public Lock getLock(Object key) {
        int index = getSegmentIndex(key);
        return locks[index];
    }

    /**
     * 获取分段索引，通过key的hash值映射到一个特定的段
     * @param key 锁的标识
     * @return 锁段索引
     */
    private int getSegmentIndex(Object key) {
        return Math.floorMod(key.hashCode(), segmentCount);

    }

    /**
     * 执行任务并锁定相关段
     * @param key 锁的标识
     * @param task 要执行的任务
     */
    public void execute(Object key, Runnable task) {
        Lock lock = getLock(key);
        lock.lock();
        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 执行带返回值的任务
     * @param key 锁的标识
     * @param task 要执行的任务
     * @param <T> 返回值类型
     * @return 返回值
     */
    public <T> T executeWithReturn(Object key, Task<T> task) {
        Lock lock = getLock(key);
        lock.lock();
        try {
            return task.run();
        } finally {
            lock.unlock();
        }
    }

    // 定义带返回值的任务接口
    public static interface Task<T> {
        T run();
    }


    public static void main(String[] args) {
        Integer a = 111;

        SegmentLock segmentLock = new SegmentLock();

        String ss = new  String("123");

        int segmentIndex = segmentLock.getSegmentIndex(-121);


        int as= 0;
    }
}
