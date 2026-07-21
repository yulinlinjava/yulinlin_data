package com.yulinlin.admin;


import com.alibaba.druid.pool.DruidDataSource;
import com.yulinlin.common.filter.TableLogicFilter;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import com.yulinlin.repository.JoinRepositoryScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.sql.DataSource;


@SpringBootApplication
@JoinRepositoryScan
@EnableSwagger2

//@EnableRedisHttpSession
public class AdminApplication  {



/*
	@ConfigurationProperties("oss.datasource") //绑定属性
	@Bean("ossDataSource")
	public DataSource ossDataSource() {
		DruidDataSource druidDataSource = new DruidDataSource();

		return druidDataSource;
	}
*/


	@ConfigurationProperties("spring.datasource") //绑定属性
	@Bean("dataSource")
	@Primary
	public DataSource dataSource() {
		DruidDataSource druidDataSource = new DruidDataSource();

		return druidDataSource;
	}



/*	@Bean
	public EntitySession ossSession(JdbcSessionFactory factory,
									@Qualifier(value = "ossDataSource")
											DataSource ossDataSource){
		JdbcSession jdbcSession = factory.create(ossDataSource, "oss");
		return jdbcSession;
	}*/

	/*@Bean
	public RouterHashAction routerHashAction(){
		RouterHashAction routerHashAction = new RouterHashAction();

		routerHashAction.add("sys_user","id");
		return routerHashAction;
	}
*/




	public static void main(String[] args) {

		SpringApplication.run(AdminApplication.class, args);
	}

}
