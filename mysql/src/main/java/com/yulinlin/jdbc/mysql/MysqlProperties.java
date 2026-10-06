package com.yulinlin.jdbc.mysql;

import com.yulinlin.jdbc.JdbcSessionProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.mysql")
public class MysqlProperties extends JdbcSessionProperties {
}
