package com.yulinlin.data.lang.reflection;

import java.io.*;

public class SerializableUtil {


    public static <E> E clone(E data){
        byte[] serialize = serialize(data);
        return (E)unSerialize(serialize);

    }

    public static <T> byte[] serialize(T obj) {
        if (obj == null) {
            return null;
        }
        byte[] bytes;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(1024); ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            bytes = bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("serialize failed.", e);
        }
        return bytes;
    }

    public static Object unSerialize(byte[] bs) {
        try (ObjectInputStream objIn = new ObjectInputStream(new ByteArrayInputStream(bs)) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
                Class<?> clazz = super.resolveClass(objectStreamClass);
                return clazz;
            }
        }) {
            return objIn.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException("This is impossible to happen", e);
        }
    }
}
