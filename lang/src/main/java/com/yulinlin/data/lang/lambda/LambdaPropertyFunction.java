package com.yulinlin.data.lang.lambda;

import com.yulinlin.data.lang.reflection.AnnotationUtil;

import java.io.Serializable;
import java.lang.reflect.Field;

@FunctionalInterface
public interface LambdaPropertyFunction<E>  extends Serializable {

    Object apply(E source);




}
