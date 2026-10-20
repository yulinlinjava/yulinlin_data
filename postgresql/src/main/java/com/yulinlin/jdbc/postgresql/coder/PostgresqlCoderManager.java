package com.yulinlin.jdbc.postgresql.coder;

import com.yulinlin.jdbc.coder.JdbcCoderManager;

/** PostgreSQL 编解码管理器：公共类型复用 JDBC，时间类型由本模块独立注册。 */
public class PostgresqlCoderManager extends JdbcCoderManager {
    @Override
    protected void init() {
        super.init();
        registerCoder(new PostgresqlTemporalCoders.DateCoder());
        registerCoder(new PostgresqlTemporalCoders.DateTimeCoder());
        registerCoder(new PostgresqlTemporalCoders.LocalDateCoder());
        registerCoder(new PostgresqlTemporalCoders.LocalDateTimeCoder());
        registerCoder(new PostgresqlTemporalCoders.LocalTimeCoder());
    }
}
