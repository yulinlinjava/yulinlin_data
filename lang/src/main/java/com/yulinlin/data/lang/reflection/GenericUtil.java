package com.yulinlin.data.lang.reflection;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GenericUtil {


		/**
         * 获得制定类的泛型参数
         * @param from 泛型子类
         * @param to 泛型父类
         * @param index 泛型参数下标
         * @return
         */
		public static Class<?> getGeneric(Class<?> from,Class<?> to,int... index){

			return forClass(from,to,index);
		}

		public static Class<?> forClass(Class<?> from,Class<?> to,int... index){
			ResolvableType rt=ResolvableType.forClass(from);
			return rt.as(to).getGeneric(index)
					.resolve();
		}

		public static Class<?> forField(Field field,int... index){
			ResolvableType resolvableType = ResolvableType.forField(field);
			return resolvableType.getGeneric(index).resolve();
		}


		public static Class<?> getFieldGeneric(Field field,int... index){

			return forField(field,index);
		}



  //获取方法参数泛型
	public static Class<?> forMethodParameter(Method method, int index,int... keys){
		MethodParameter methodParameter = new MethodParameter(method,index);
		ResolvableType resolvableType = ResolvableType.forMethodParameter(methodParameter);
		return resolvableType.getGeneric(keys).resolve();
	}


	public static Class<?> forMethodReturnType(Method method, int... index){
		ResolvableType resolvableType = ResolvableType.forMethodReturnType(method);

		return resolvableType.getGeneric(index).resolve();
	}



}
