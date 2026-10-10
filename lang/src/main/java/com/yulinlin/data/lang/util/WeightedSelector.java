package com.yulinlin.data.lang.util;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;

public class WeightedSelector<T> {
    private final ConcurrentSkipListMap<Integer, T> weightMap = new ConcurrentSkipListMap<>();
    private volatile int totalWeight;

    public synchronized void add(T item, int weight) {
        if (weight <= 0) return;
        int nextTotal = Math.addExact(totalWeight, weight);
        weightMap.put(nextTotal, item);
        totalWeight = nextTotal;
    }


    public Collection<T> list(){
        return Collections.unmodifiableCollection(weightMap.values());
    }

    public T select() {
        int bound = totalWeight;
        if (bound == 0) throw new IllegalStateException("No positive-weight item registered");
        int r = ThreadLocalRandom.current().nextInt(bound) + 1; // [1, totalWeight]
        return weightMap.ceilingEntry(r).getValue();
    }

    public static void main(String[] args) {
        WeightedSelector<String> selector = new WeightedSelector<>();
        selector.add("A", 1);
        selector.add("B", 3);
        selector.add("C", 4);

        // 测试选中分布
        Map<String, Integer> counter = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            String selected = selector.select();
            counter.put(selected, counter.getOrDefault(selected, 0) + 1);
        }

        System.out.println("选择结果统计：" + counter);
    }
}
