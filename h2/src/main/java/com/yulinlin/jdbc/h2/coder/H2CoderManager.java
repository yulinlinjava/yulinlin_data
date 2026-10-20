package com.yulinlin.jdbc.h2.coder;

import com.yulinlin.jdbc.coder.JdbcCoderManager;

/** H2 编解码管理器：公共类型复用 JDBC，时间类型由本模块独立注册。 */
public class H2CoderManager extends JdbcCoderManager {
    @Override
    protected void init() {
        super.init();
        registerCoder(new H2TemporalCoders.DateCoder());
        registerCoder(new H2TemporalCoders.DateTimeCoder());
        registerCoder(new H2TemporalCoders.LocalDateCoder());
        registerCoder(new H2TemporalCoders.LocalDateTimeCoder());
        registerCoder(new H2TemporalCoders.LocalTimeCoder());
    }
}
