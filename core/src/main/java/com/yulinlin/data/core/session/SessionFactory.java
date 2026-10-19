package com.yulinlin.data.core.session;

/** 创建并完整配置 Session；路由注册阶段不再补充编码器、缓存等依赖。 */
public interface SessionFactory<E> {


    EntitySession create(E client,String name);


}
