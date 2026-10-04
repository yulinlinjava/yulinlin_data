package com.yulinlin.data.lang.reflection;

/** Standalone regression checks; run with assertions enabled. */
public class ReflectionUtilRegression {
    private static class PrivateBean {
        private long value;
        private PrivateBean() {}
        private long getValue() { return value; }
        private void setValue(long value) { this.value = value; }
        private void nothing() {}
    }
    public static class Parent {
        private String hidden = "parent";
        public String name() { return "parent"; }
    }
    public interface Named { default String label() { return "default"; } }
    public static class Bean extends Parent implements Named {
        private String hidden = "child";
        private int age = 1;
        public String name() { return "child"; }
        private String secret() { return "secret"; }
        public static String staticValue() { return "static"; }
        public String pick() { return "zero"; }
        public String pick(Object value) { return "object"; }
        public String pick(String value) { return "string"; }
        public String wide(long value) { return "long"; }
        public String ambiguous(String value) { return "string"; }
        public String ambiguous(Integer value) { return "integer"; }
        public void setAge(int age) { throw new IllegalArgumentException("rejected"); }
    }
    public static void main(String[] args) throws Exception {
        PrivateBean privateBean = ReflectionUtil.newInstance(PrivateBean.class);
        ReflectionUtil.PropertyAccess access = ReflectionUtil.property(PrivateBean.class, "value");
        access.set(privateBean, 42);
        assert Long.valueOf(42).equals(access.get(privateBean));
        assert ReflectionUtil.invokeMethod(privateBean, "nothing") == null;
        assert ReflectAsmUtil.newInstance(PrivateBean.class) != null;
        ReflectAsmUtil.invokeSetter(privateBean, "value", 99L);
        assert Long.valueOf(99).equals(ReflectAsmUtil.invokeGetter(privateBean, "value"));
        assert ReflectionUtil.invokeMethod("hello", "length").equals(5);
        java.util.Set<String> original = new java.util.HashSet<>(java.util.List.of("a"));
        java.util.Set<String> copy = ReflectionUtil.clone(original);
        assert copy != original && copy.equals(original);
        java.util.Date date = new java.util.Date();
        assert ReflectionUtil.clone(date) != date;
        java.util.Map<String, Object> nested = new java.util.HashMap<>();
        nested.put("bean", privateBean);
        ReflectionUtil.invokeSetter(nested, "bean.value", 7);
        assert Long.valueOf(7).equals(ReflectionUtil.invokeGetter(nested, "bean.value"));
        Bean bean = new Bean();
        assert "zero".equals(ReflectionUtil.invokeMethod(bean, "pick"));
        assert "string".equals(ReflectionUtil.invokeMethod(bean, "pick", "x"));
        assert "string".equals(ReflectionUtil.invokeMethod(bean, "pick", (Object) null));
        try { ReflectionUtil.invokeMethod(bean, "ambiguous", (Object) null); throw new AssertionError("Ambiguous overload accepted"); }
        catch (IllegalArgumentException expected) { }
        assert "long".equals(ReflectionUtil.invokeMethod(bean, "wide", 1));
        assert "secret".equals(ReflectionUtil.invokeMethod(bean, "secret"));
        assert "default".equals(ReflectionUtil.invokeMethod(bean, "label"));
        assert "child".equals(ReflectionUtil.invokeMethod(bean, Parent.class.getMethod("name")));
        assert "static".equals(ReflectionUtil.invokeMethod(null, Bean.class.getMethod("staticValue")));
        assert "child".equals(ReflectionUtil.invokeGetter(bean, "hidden"));
        assert "parent".equals(ReflectionUtil.invokeGetter(bean, Parent.class.getDeclaredField("hidden")));
        ReflectionUtil.invokeSetter(bean, "hidden", "changed");
        assert "changed".equals(ReflectionUtil.invokeGetter(bean, "hidden"));
        try { ReflectionUtil.invokeSetter(bean, "age", -1); throw new AssertionError("Setter exception swallowed"); }
        catch (IllegalArgumentException expected) { assert "rejected".equals(expected.getMessage()); }
        assert Integer.valueOf(1).equals(ReflectionUtil.invokeGetter(bean, "age"));
        int[] array = {1};
        ReflectionUtil.invokeSetter(array, "0", 2);
        assert Integer.valueOf(2).equals(ReflectionUtil.invokeGetter(array, "0"));
        assert ReflectionUtil.findMethod(Bean.class, "absent") == null;
        try { ReflectionUtil.parse(bean, "${hidden"); throw new AssertionError("Malformed expression accepted"); }
        catch (IllegalArgumentException expected) { }
        assert ReflectionUtil.clone(null, true) == null;
        System.out.println("Reflection regression checks passed");
    }
}
