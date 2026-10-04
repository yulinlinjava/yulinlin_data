package com.yulinlin.data.lang.reflection;

import java.util.*;

/** Standalone regression checks; run with -ea. */
public class CopyPropertiesRegression {
    public static class Node {
        public String name;
        public Node next;
        public Node() { name = "default"; }
    }
    public static class Source {
        public Node child;
        public List<Object> list;
        public Set<Node> set;
        public Map<String, Object> map;
        public int[] numbers;
        public Node[] nodes;
        public Date date;
        public Source self;
        public String nullable;
        public String getComputed() { return "getter"; }
    }
    public static class Target {
        public Node child;
        public List<Object> list;
        public Set<Node> set;
        public Map<String, Object> map;
        public int[] numbers;
        public Node[] nodes;
        public Date date;
        public Target self;
        public String missing = "keep";
        public String nullable = "keep-null";
        public String computed;
    }
    public static class Invalid { public Integer computed; }
    public static class Reject {
        public String computed = "original";
        public void setComputed(String value) { throw new IllegalArgumentException("rejected"); }
    }
    public static void main(String[] args) {
        Source source = new Source();
        source.child = new Node();
        source.child.name = null;
        source.child.next = source.child;
        source.list = new ArrayList<>();
        source.list.add(source.child);
        source.list.add(source.list);
        source.set = new LinkedHashSet<>(List.of(source.child));
        source.map = new LinkedHashMap<>();
        source.map.put("node", source.child);
        source.map.put("self", source.map);
        source.numbers = new int[]{1, 2};
        source.nodes = new Node[]{source.child};
        source.date = new Date(123);
        source.self = source;
        Source cloned = ReflectionUtil.deepClone(source);
        assert cloned != source && cloned.self == cloned;
        assert cloned.child != source.child && cloned.child.next == cloned.child;
        assert cloned.child.name == null;
        assert cloned.list.get(0) == cloned.child && cloned.list.get(1) == cloned.list;
        assert cloned.map.get("node") == cloned.child && cloned.map.get("self") == cloned.map;
        assert cloned.nodes != source.nodes && cloned.nodes[0] == cloned.child;
        assert cloned.numbers != source.numbers;
        assert cloned.date != source.date && cloned.date.equals(source.date);
        assert ReflectionUtil.clone(source, true).self != source;
        List<Node> repeated = List.of(source.child, source.child);
        List<Node> repeatedClone = ReflectionUtil.deepClone(repeated);
        assert repeatedClone != repeated && repeatedClone.get(0) != source.child;
        assert repeatedClone.get(0) == repeatedClone.get(1);
        assert ReflectionUtil.deepClone(null) == null;
        assert ReflectionUtil.deepClone("immutable").equals("immutable");
        cloned.child.name = "clone-only";
        assert source.child.name == null;
        Target target = ReflectionUtil.copyProperties(source, new Target());
        assert target.child != source.child && target.child.next == target.child;
        assert target.child.name == null;
        assert target.self == target;
        assert target.list != source.list && target.list.get(0) == target.child;
        assert target.list.get(1) == target.list;
        assert target.set != source.set && target.set.iterator().next() == target.child;
        assert target.map != source.map && target.map.get("self") == target.map;
        assert target.map.get("node") == target.child;
        assert target.nodes != source.nodes && target.nodes[0] == target.child;
        assert target.numbers != source.numbers && target.numbers[1] == 2;
        assert target.date != source.date && target.date.equals(source.date);
        assert target.missing.equals("keep") && target.nullable.equals("keep-null");
        assert target.computed.equals("getter");
        target.child.name = "changed";
        target.numbers[0] = 99;
        assert source.child.name == null && source.numbers[0] == 1;
        source.list = List.of(source.child);
        assert ReflectionUtil.copyProperties(source, new Target()).list.get(0) != source.child;
        source.list = new LinkedList<>(List.of(source.child));
        assert ReflectionUtil.copyProperties(source, new Target()).list instanceof LinkedList;
        try { ReflectionUtil.copyProperties(source, new Invalid()); throw new AssertionError("Incompatible type accepted"); }
        catch (ClassCastException | IllegalArgumentException expected) { }
        Reject reject = new Reject();
        try { ReflectionUtil.copyProperties(source, reject); throw new AssertionError("Setter error swallowed"); }
        catch (IllegalArgumentException expected) { assert expected.getMessage().equals("rejected"); }
        assert reject.computed.equals("original");
        System.out.println("Deep copy regression checks passed");
    }
}
