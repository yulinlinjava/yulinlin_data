package com.yulinlin.data.lang.util;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程工具
 */
@Slf4j
public class ThreadUtil {

    private static final AtomicInteger CPU_THREAD_SEQUENCE = new AtomicInteger();
    private static final AtomicInteger SCHEDULER_THREAD_SEQUENCE = new AtomicInteger();

    /** Bounded platform-thread executor for CPU-intensive work and explicitly limited concurrency. */
    public static final ThreadPoolExecutor threadPoolExecutor = threadPoolExecutor();
    /** Lightweight timer; normal scheduled work is dispatched from here to a virtual or CPU executor. */
    public static final ScheduledExecutorService scheduledExecutorService = scheduledThreadPoolExecutor();
    private static final ExecutorService virtualExecutorService = Executors.newVirtualThreadPerTaskExecutor();



    public static void startThread(int sleepTime,
                                   int delayTime,
                                   Callable<Boolean> runnable){
        if (sleepTime < 0) throw new IllegalArgumentException("sleepTime must not be negative");
        if (delayTime < 0) throw new IllegalArgumentException("delayTime must not be negative");
        Objects.requireNonNull(runnable, "runnable");

        Thread.ofVirtual().name("thread-util-loop").start(() -> {
            if (!sleep(delayTime)) return;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (!Boolean.TRUE.equals(runnable.call())) return;
                } catch (Exception error) {
                    log.error("循环任务异常", error);
                }
                if (!sleep(sleepTime)) return;
            }
        });
    }

    public static void startThread(int time,Callable<Boolean> runnable){

        startThread(time,time,runnable);

    }

    private static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor(){
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "thread-util-scheduler-" + SCHEDULER_THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        pool.setRemoveOnCancelPolicy(true);
        return pool;
    }

    private static ThreadPoolExecutor threadPoolExecutor(){
        int core = Math.max(1, Runtime.getRuntime().availableProcessors());
        ThreadPoolExecutor pool = new ThreadPoolExecutor(core, core, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "thread-util-cpu-" + CPU_THREAD_SEQUENCE.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }




    public static <T> Future<T> submit(Callable<T> task) {
        return submitVirtual(task);
    }

    public static <T> Future<T> submit(Runnable task) {
        return submitVirtual(task);
    }

    /**
     * Explicit alias for {@link #submit(Callable)}. Runs one task on one virtual thread.
     */
    public static <T> Future<T> submitVirtual(Callable<T> task) {
        return virtualExecutorService.submit(Objects.requireNonNull(task, "task"));
    }

    /** Runs a blocking or high-wait task on its own virtual thread. */
    @SuppressWarnings("unchecked")
    public static <T> Future<T> submitVirtual(Runnable task) {
        return (Future<T>) virtualExecutorService.submit(Objects.requireNonNull(task, "task"));
    }

    /** Runs CPU-intensive work on the bounded platform-thread executor. */
    public static <T> Future<T> submitCpu(Callable<T> task) {
        return threadPoolExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    /** Runs CPU-intensive work on the bounded platform-thread executor. */
    @SuppressWarnings("unchecked")
    public static <T> Future<T> submitCpu(Runnable task) {
        return (Future<T>) threadPoolExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    /** Schedules one blocking task and dispatches it to a virtual thread. */
    public static ScheduledFuture<?> submitVirtual(Runnable task, long delay) {
        if (delay < 0) throw new IllegalArgumentException("delay must not be negative");
        Objects.requireNonNull(task, "task");
        return scheduledExecutorService.schedule(() -> submitVirtual(task), delay, TimeUnit.MILLISECONDS);
    }

    public static void submit(Runnable task, int delay) {
        submitVirtual(task, delay);
    }

    /** Periodically runs work on virtual threads without overlapping invocations. */
    public static ScheduledFuture<?> schedule(Runnable task, long delay) {
        return scheduleVirtual(task, delay);
    }

    /**
     * Periodically dispatches blocking work to a virtual thread. A still-running invocation is not overlapped.
     */
    public static ScheduledFuture<?> scheduleVirtual(Runnable task, long delay) {
        if (delay <= 0) throw new IllegalArgumentException("delay must be positive");
        Objects.requireNonNull(task, "task");
        AtomicBoolean running = new AtomicBoolean();
        return scheduledExecutorService.scheduleAtFixedRate(() -> {
            if (!running.compareAndSet(false, true)) return;
            submitVirtual(() -> {
                try {
                    task.run();
                } catch (Throwable error) {
                    log.error("虚拟线程定时任务异常", error);
                } finally {
                    running.set(false);
                }
            });
        }, delay, delay, TimeUnit.MILLISECONDS);
    }

    /** Periodically runs CPU-intensive work on the bounded platform pool without overlapping invocations. */
    public static ScheduledFuture<?> scheduleCpu(Runnable task, long delay) {
        if (delay <= 0) throw new IllegalArgumentException("delay must be positive");
        Objects.requireNonNull(task, "task");
        AtomicBoolean running = new AtomicBoolean();
        return scheduledExecutorService.scheduleAtFixedRate(() -> {
            if (!running.compareAndSet(false, true)) return;
            submitCpu(() -> {
                try {
                    task.run();
                } catch (Throwable error) {
                    log.error("CPU 定时任务异常", error);
                } finally {
                    running.set(false);
                }
            });
        }, delay, delay, TimeUnit.MILLISECONDS);
    }

    private static boolean sleep(long millis) {
        if (millis == 0) return true;
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }


    public static final ThreadLocal<Map<Object, Object>> threadLocal = ThreadLocal.withInitial(HashMap::new);

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
        threadLocal.remove();
    }

}
