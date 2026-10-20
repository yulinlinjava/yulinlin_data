package com.yulinlin.jdbc.sqlite.coder;

import com.yulinlin.jdbc.coder.JdbcCoderManager;

/** SQLite 编解码管理器：公共类型复用 JDBC，时间类型由本模块独立注册。 */
public class SqliteCoderManager extends JdbcCoderManager {
    @Override
    protected void init() {
        super.init();
        registerCoder(new SqliteTemporalCoders.DateCoder());
        registerCoder(new SqliteTemporalCoders.DateTimeCoder());
        registerCoder(new SqliteTemporalCoders.LocalDateCoder());
        registerCoder(new SqliteTemporalCoders.LocalDateTimeCoder());
        registerCoder(new SqliteTemporalCoders.LocalTimeCoder());
    }
}
