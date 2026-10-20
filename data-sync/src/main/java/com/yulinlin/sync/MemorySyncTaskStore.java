package com.yulinlin.sync;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 内存实现；容量按节点而不是请求数计算，执行中与失败头任务仍占容量。 */
public final class MemorySyncTaskStore implements SyncTaskStore {
    private static final class Queue {
        final ArrayDeque<SyncTask> tasks = new ArrayDeque<>();
        int nodes;
    }
    private final Map<String, Queue> queues = new ConcurrentHashMap<>();

    private Queue queue(String rule) { return queues.computeIfAbsent(rule, key -> new Queue()); }

    @Override public boolean offer(String rule, SyncTask task, int capacity) {
        Queue queue = queue(rule);
        synchronized (queue) {
            if (task.nodes() > capacity - queue.nodes) return false;
            queue.tasks.addLast(task);
            queue.nodes += task.nodes();
            return true;
        }
    }
    @Override public SyncTask peek(String rule) {
        Queue queue = queue(rule);
        synchronized (queue) { return queue.tasks.peekFirst(); }
    }
    @Override public void acknowledge(String rule, String taskId) {
        Queue queue = queue(rule);
        synchronized (queue) {
            if (queue.tasks.isEmpty() || !queue.tasks.getFirst().id().equals(taskId))
                throw new IllegalStateException("同步任务确认顺序不一致：" + rule);
            queue.nodes -= queue.tasks.removeFirst().nodes();
        }
    }
    @Override public int pendingTasks(String rule) {
        Queue queue = queue(rule);
        synchronized (queue) { return queue.tasks.size(); }
    }
    @Override public int pendingNodes(String rule) {
        Queue queue = queue(rule);
        synchronized (queue) { return queue.nodes; }
    }
}
