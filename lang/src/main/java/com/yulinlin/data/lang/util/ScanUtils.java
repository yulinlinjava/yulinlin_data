package com.yulinlin.data.lang.util;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.SystemPropertyUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ScanUtils {

    private static final ResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();
    private static final MetadataReaderFactory METADATA_READER_FACTORY = new CachingMetadataReaderFactory(RESOLVER);



    public static Set<Class<?>> scanClass(String scanPath, boolean isInterface) {

        return scanClass(scanPath,isInterface,RESOLVER);
    }

        /**
         * 扫描指定路径下的所有类
         *
         * @param scanPath     扫描路径
         * @param isInterface 是否接口
         * @return 扫描到的类
         */
    public static Set<Class<?>> scanClass(String scanPath, boolean isInterface,ResourcePatternResolver resourcePatternResolver) {
        String path = ClassUtils.convertClassNameToResourcePath(SystemPropertyUtils.resolvePlaceholders(scanPath));
        String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + path + "/**/*.class";

        Set<Class<?>> classes = new HashSet<>();
        MetadataReaderFactory metadataReaderFactory = resourcePatternResolver == RESOLVER
                ? METADATA_READER_FACTORY : new CachingMetadataReaderFactory(resourcePatternResolver);
        ClassLoader classLoader = resourcePatternResolver.getClassLoader();
        try {
            Resource[] resources = resourcePatternResolver.getResources(packageSearchPath);
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                    ClassMetadata classMetadata = metadataReader.getClassMetadata();
                    if(isInterface){
                        if (classMetadata.isInterface() || classMetadata.isAbstract()) {
                            classes.add(Class.forName(classMetadata.getClassName(), false, classLoader));
                        }
                    }else {
                        if (classMetadata.isConcrete()) {
                            classes.add(Class.forName(classMetadata.getClassName(), false, classLoader));
                        }
                    }

                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    e
            );
        }
        return classes;
    }

    /**
     * 扫描指定路径下指定超类的所有子类
     *
     * @param scanPath     扫描路径
     * @param superclass   扫描的类的父类
     * @param mustConcrete 扫描的类是否是具体的类，即非接口、非抽象类
     * @return 扫描到的类
     */
    public static Set<Class<?>> scanSubclass(String scanPath, Class<?> superclass, boolean mustConcrete) {
        Set<Class<?>> classes = new HashSet<>(scanClass(scanPath, false));
        if (!mustConcrete) classes.addAll(scanClass(scanPath, true));
        return classes.stream().filter(type -> type != superclass && superclass.isAssignableFrom(type))
                .collect(Collectors.toSet());
    }

}
