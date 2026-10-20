package com.yulinlin.jdbc.mysql.coder;

import com.yulinlin.jdbc.coder.JdbcCoderManager;

/** MySQL 编解码管理器：公共类型复用 JDBC，时间类型由本模块独立注册。 */
public class MysqlCoderManager extends JdbcCoderManager {
    @Override
    protected void init() {
        super.init();
        registerCoder(new MysqlTemporalCoders.DateCoder());
        registerCoder(new MysqlTemporalCoders.DateTimeCoder());
        registerCoder(new MysqlTemporalCoders.LocalDateCoder());
        registerCoder(new MysqlTemporalCoders.LocalDateTimeCoder());
        registerCoder(new MysqlTemporalCoders.LocalTimeCoder());
    }
}
