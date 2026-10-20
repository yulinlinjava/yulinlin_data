package com.yulinlin.jdbc.coder;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.MapCoderManager;

/** JDBC 公共编解码；数据库子类在 init 中注册自己的编码器，同类型后注册者生效。 */
public class JdbcCoderManager extends MapCoderManager {
    @Override
    protected void init() {
        super.init();
        registerCoder(new CollectionCoder());
        registerCoder(new MapCoder());
        registerCoder(new ObjectCoder());
    }

    /** 仅在初始化阶段注册；不修改其他管理器，也不保留同类型的旧编码器。 */
    @Override
    @SuppressWarnings("rawtypes")
    public void registerCoder(ICoder coder) {
        replaceCoder(coder);
    }
}
