package com.yulinlin.data.core.model;

public interface BaseModel {

    /**
     * 插入前执行
     * 模型解析前执行
     */
    default void insertBefore(){}
    /**
     * 更新前执行
     * 模型解析前执行
     */
    default void updateBefore(){}



    /**
     * 删除前
     */
    default void deleteBefore(){}

    /**
     * 查询前
     */
    default void selectBefore(){}

    /**
     * 聚合前
     */
    default void groupBefore(){}
}
