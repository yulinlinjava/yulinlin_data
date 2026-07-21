package com.yulinlin.common.model;

import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;

/**
 * 基础查询模型
 * @param <E>
 */
public interface AbstractQueryModel<E extends AbstractQueryModel<E>> {


    //查询构造器
    default <W extends IConditionWrapper<E,W>> ModelSelectWrapper<E,W> createSelectWrapper(){

        return ModelSelectWrapper.newInstance((E)this);
    }

    //聚合构造器
    default  <W extends IConditionWrapper<E,W>> ModelGroupWrapper<E,W> createGroupWrapper(){
        return ModelGroupWrapper.newInstance((E)this);
    }

    /**
     * 创建同步代理
     * @return
     */
    default E createLazyProxy(){
        return (E) SessionUtil.route().getLazyProxy(this);
    }



}
