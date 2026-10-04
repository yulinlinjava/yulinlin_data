package com.yulinlin.admin;

import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.esotericsoftware.kryo.Kryo;
import org.apache.commons.lang3.SerializationUtils;
import org.openjdk.jmh.annotations.*;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Each operation clones an entire batch, not a single user. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
@Threads(1)
@State(Scope.Thread)
public class DeepCloneBenchmark {
    /** Enable the jmh Maven profile and reload Maven before running from the IDE. */
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args.length == 0
                ? new String[]{DeepCloneBenchmark.class.getSimpleName(), "-prof", "gc"}
                : args);
    }

    @Param({"200000"})
    public int size;

    private List<User> source;
    private Kryo kryo;

    @Setup(Level.Trial)
    public void setup() {
        kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setCopyReferences(true);
        if (size < 1) throw new IllegalArgumentException("size must be positive");
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            User user = new User();
            user.id = i;
            user.name = "user-" + i;
            user.enabled = true;
            user.address = new Address();
            user.address.city = "Hangzhou";
            user.address.zip = i;
            user.tags = new ArrayList<>(List.of("active", "customer"));
            user.attributes = new HashMap<>();
            user.attributes.put("address", user.address);
            user.scores = new int[]{i, 2, 3};
            source.add(user);
        }
        // Small correctness check outside measurement. Full regressions live in src/test.
        List<User> sample = new ArrayList<>(source.subList(0, Math.min(size, 3)));
        verify(sample, ReflectionUtil.deepClone(sample));
        verify(sample, cloneIndividually(sample));
        verify(sample, kryo.copy(sample));
        verify(sample, SerializationUtils.clone(new ArrayList<>(sample)));
        for (User user : sample) {
            verify(List.of(user), List.of(kryo.copy(user)));
            verify(List.of(user), List.of(SerializationUtils.clone(user)));
            verify(List.of(user), List.of(manualCopy(user)));
        }
    }

    @Benchmark
    public List<User> kryoWholeList() {
        return kryo.copy(source);
    }

    @Benchmark
    public List<User> kryoPerUser() {
        List<User> result = new ArrayList<>(size);
        for (User user : source) result.add(kryo.copy(user));
        return result;
    }

    @Benchmark
    public List<User> serializationWholeList() {
        return SerializationUtils.clone((ArrayList<User>) source);
    }

    @Benchmark
    public List<User> serializationPerUser() {
        List<User> result = new ArrayList<>(size);
        for (User user : source) result.add(SerializationUtils.clone(user));
        return result;
    }

    @Benchmark
    public List<User> manualPerUser() {
        List<User> result = new ArrayList<>(size);
        for (User user : source) result.add(manualCopy(user));
        return result;
    }

    // Specialized baseline for this exact fixture, not a general graph-cloning utility.
    private static User manualCopy(User value) {
        User copy = new User();
        copy.id = value.id;
        copy.name = value.name;
        copy.enabled = value.enabled;
        copy.address = new Address();
        copy.address.city = value.address.city;
        copy.address.zip = value.address.zip;
        copy.tags = new ArrayList<>(value.tags);
        copy.attributes = new HashMap<>();
        copy.attributes.put("address", copy.address);
        copy.scores = value.scores.clone();
        return copy;
    }

    @Benchmark
    public List<User> wholeList() {
        return ReflectionUtil.deepClone(source);
    }

    @Benchmark
    public List<User> perUser() {
        return cloneIndividually(source);
    }

    private static List<User> cloneIndividually(List<User> users) {
        List<User> result = new ArrayList<>(users.size());
        for (User user : users) result.add(ReflectionUtil.deepClone(user));
        return result;
    }

    private static void verify(List<User> original, List<User> cloned) {
        if (original == cloned || original.size() != cloned.size()) {
            throw new IllegalStateException("Invalid cloned list");
        }
        for (int i = 0; i < original.size(); i++) {
            User a = original.get(i), b = cloned.get(i);
            if (a == b || a.id != b.id || !a.name.equals(b.name) || a.enabled != b.enabled
                    || a.address == b.address || !a.address.city.equals(b.address.city)
                    || a.address.zip != b.address.zip || a.tags == b.tags || !a.tags.equals(b.tags)
                    || a.attributes == b.attributes || a.attributes.size() != b.attributes.size()
                    || b.attributes.get("address") != b.address
                    || a.scores == b.scores || !Arrays.equals(a.scores, b.scores)) {
                throw new IllegalStateException("Deep clone correctness check failed at " + i);
            }
        }
    }

    public static class Address implements Serializable {
        private static final long serialVersionUID = 1L;
        public String city;
        public int zip;
    }

    public static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id;
        public String name;
        public boolean enabled;
        public Address address;
        public List<String> tags;
        public Map<String, Object> attributes;
        public int[] scores;
    }
}
