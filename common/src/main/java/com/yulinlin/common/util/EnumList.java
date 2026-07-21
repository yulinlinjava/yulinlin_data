package com.yulinlin.common.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j

public class EnumList {

    public static EnumNode getEnumItem(String code){


        return EnumNode.newInstance(code);
    }



}
