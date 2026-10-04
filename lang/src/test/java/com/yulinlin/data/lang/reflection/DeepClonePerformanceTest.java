package com.yulinlin.data.lang.reflection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.management.ManagementFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in measurement, not a JMH benchmark. No timing assertions on shared CI hosts. */
public class DeepClonePerformanceTest {
    private static final int COUNT = 200_000;
    private static final int WARMUPS = 3;
    private static final int ROUNDS = 5;
    private static volatile Object sink;

    public static class Address {
        public String city;
        public int zip;
    }
    public static class User {
        public long id;
        public String name;
        public boolean enabled;
        public Address address;
        public List<String> tags;
        public Map<String, Object> attributes;
        public int[] scores;
    }

    @Test
    @EnabledIfSystemProperty(named = "reflection.perf", matches = "true")
    void cloneTwoHundredThousandUsers() {
        List<User> source = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            User user = new User();
            user.id = i; user.name = "user-" + i; user.enabled = true;
            user.address = new Address(); user.address.city = "Hangzhou"; user.address.zip = i;
            user.tags = new ArrayList<>(List.of("active", "customer"));
            user.attributes = new HashMap<>();
            user.attributes.put("address", user.address); // repeated reference inside each user
            user.scores = new int[]{i, 2, 3};
            source.add(user);
        }
        System.out.printf("JDK=%s maxHeapMiB=%d users=%d warmups=%d rounds=%d%n",
                System.getProperty("java.version"), Runtime.getRuntime().maxMemory() / 1048576, COUNT, WARMUPS, ROUNDS);
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocations = bean instanceof com.sun.management.ThreadMXBean b
                && b.isThreadAllocatedMemorySupported() ? b : null;
        if (allocations != null && !allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        double[][] times = new double[2][ROUNDS];
        double[][] bytes = new double[2][ROUNDS];
        for (int round = -WARMUPS; round < ROUNDS; round++) {
            // Alternate execution order to reduce first/second bias.
            for (int position = 0; position < 2; position++) {
                int mode = Math.floorMod(round + position, 2);
                sink = null;
                long beforeBytes = allocated(allocations);
                long beforeGc = gcCount();
                long start = System.nanoTime();
                List<User> result;
                if (mode == 0) result = ReflectionUtil.deepClone(source);
                else {
                    result = new ArrayList<>(COUNT);
                    for (User user : source) result.add(ReflectionUtil.deepClone(user));
                }
                long elapsed = System.nanoTime() - start;
                long allocated = allocations == null ? -1 : allocated(allocations) - beforeBytes;
                long collections = gcCount() - beforeGc;
                sink = result;
                // Verification is intentionally outside the timed/allocation interval.
                assertEquals(COUNT, result.size());
                for (int i = 0; i < COUNT; i++) {
                    User a = source.get(i), b = result.get(i);
                    assertNotSame(a, b);
                    assertEquals(a.id, b.id); assertEquals(a.name, b.name); assertEquals(a.enabled, b.enabled);
                    assertNotSame(a.address, b.address);
                    assertEquals(a.address.city, b.address.city); assertEquals(a.address.zip, b.address.zip);
                    assertNotSame(a.tags, b.tags); assertEquals(a.tags, b.tags);
                    assertNotSame(a.attributes, b.attributes);
                    assertSame(b.address, b.attributes.get("address"));
                    assertNotSame(a.scores, b.scores); assertArrayEquals(a.scores, b.scores);
                }
                result.get(0).address.city = "changed";
                result.get(0).tags.add("copy-only");
                result.get(0).scores[0] = -1;
                assertEquals("Hangzhou", source.get(0).address.city);
                assertEquals(2, source.get(0).tags.size());
                assertEquals(0, source.get(0).scores[0]);
                if (round >= 0) {
                    times[mode][round] = elapsed / 1e6;
                    bytes[mode][round] = allocated < 0 ? -1 : allocated / 1048576.0;
                    System.out.printf(Locale.ROOT, "%s round=%d ms=%.2f allocatedMiB=%.2f gc=%d%n",
                            mode == 0 ? "whole-list" : "per-user", round + 1, times[mode][round], bytes[mode][round], collections);
                }
            }
        }
        for (int mode = 0; mode < 2; mode++) {
            Arrays.sort(times[mode]);
            double median = times[mode][ROUNDS / 2];
            System.out.printf(Locale.ROOT, "%s medianMs=%.2f minMs=%.2f maxMs=%.2f usersPerSecond=%.0f avgAllocatedMiB=%.2f%n",
                    mode == 0 ? "whole-list" : "per-user", median, times[mode][0], times[mode][ROUNDS - 1],
                    COUNT * 1000.0 / median, Arrays.stream(bytes[mode]).average().orElse(-1));
        }
        sink = null;
    }

    private static long allocated(com.sun.management.ThreadMXBean bean) {
        return bean == null ? -1 : bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }
    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }

    /** Runs the identical assertions without Maven; useful when the wrapper is unavailable. */
    public static void main(String[] args) {
        new DeepClonePerformanceTest().cloneTwoHundredThousandUsers();
    }
}
