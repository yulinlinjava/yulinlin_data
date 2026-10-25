package com.yulinlin.admin;


import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.service.RoleService;
import com.yulinlin.admin.rbac.service.UserService;
import com.yulinlin.common.model.EntityCounter;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.util.SnowflakeUtil;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.lang.util.Money;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;
import java.util.Map;

//
@Slf4j
// admin 示例使用 JWT；其他适配器和 Spring Security API 仅供独立回归，不改变示例的运行策略。
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "com.yulinlin.security.satoken.SaTokenAutoConfiguration,"
        + "com.yulinlin.security.spring.SpringSecurityAdapterAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration")
public class AdminApplicationTests {

    @Autowired
    RouteSession session;

    @Autowired
    UserService userService;

    @Test
    public void go() throws Exception{


        int count = ModelSelectWrapper.newInstance(UserInfoEntity.class)
                .count();

        List<UserInfoEntity> userInfoEntities = ModelSelectWrapper.newInstance(UserInfoEntity.class).selectList();


        int a = 0;
  /*      number.setId("1");
        number.setNum(1);


        EntityCounter.update(number).submit();

        number = new NumberEntity();


        number.setId("1");
        number.setNum(2);
        EntityCounter.update(number).submit();*/



    }



}
