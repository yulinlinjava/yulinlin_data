package com.yulinlin.data.core.event;

/**
 * 查询事件触发
 */
public interface IInitEvent {



    /**
     * 对象初始化完毕触发事件
     * 非懒加载才触发
     */
    default void init(){

    }


}
