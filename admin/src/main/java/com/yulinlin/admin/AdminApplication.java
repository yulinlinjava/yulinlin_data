package com.yulinlin.admin;


import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;


@SpringBootApplication

public class AdminApplication  {






	@ConfigurationProperties("spring.datasource") //绑定属性
	@Bean("dataSource")
	@Primary
	@Profile("!dev")
	public DataSource dataSource() {
		DruidDataSource druidDataSource = new DruidDataSource();

		return druidDataSource;
	}










	public static void main(String[] args) {

		SpringApplication.run(AdminApplication.class, args);
	}

}
