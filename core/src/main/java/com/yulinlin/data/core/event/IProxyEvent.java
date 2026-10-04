package com.yulinlin.data.core.event;

/**
 * 查询事件触发
 */
public interface IProxyEvent {


    /**
     * 加载数据触发事件
     * 懒加载 非懒加载触发
     * @param f
     */
    default void startInjection(String f, Object data){

    }

    /**
     * 对象初始化完毕触发事件
     * 非懒加载才触发
     */
    default void finishInjection(){

    }


}
