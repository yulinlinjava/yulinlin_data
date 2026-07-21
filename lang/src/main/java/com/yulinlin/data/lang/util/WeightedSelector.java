package com.yulinlin.data.lang.util;

import java.util.*;

public class WeightedSelector<T> {
    private final NavigableMap<Integer, T> weightMap = new TreeMap<>();
    private final Random random = new Random();
    private int totalWeight = 0;

    public void add(T item, int weight) {
        if (weight <= 0) return;
        totalWeight += weight;
        weightMap.put(totalWeight, item);
    }


    public Collection<T> list(){
        return weightMap.values();
    }

    public T select() {
        int r = random.nextInt(totalWeight) + 1; // [1, totalWeight]
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
