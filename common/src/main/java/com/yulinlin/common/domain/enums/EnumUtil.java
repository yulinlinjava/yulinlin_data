package com.yulinlin.common.domain.enums;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class EnumUtil {

    public static List<EnumItem> getEnumList(SuperEnum... arr){
        return Arrays.asList(arr).stream().map(row -> row.getEnumItem()).collect(Collectors.toList());
    }
    public static List<EnumItem> getEnumList(Collection<? extends SuperEnum> arr){
        return arr.stream().map(row -> row.getEnumItem()).collect(Collectors.toList());
    }
    public static List<EnumItem> getEnumList(Class clazz){
        SuperEnum[] enumConstants =(SuperEnum[]) clazz.getEnumConstants();

        return getEnumList(enumConstants);
    }

}
