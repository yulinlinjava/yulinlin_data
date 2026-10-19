package com.yulinlin.admin;


import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.service.RoleService;
import com.yulinlin.admin.rbac.service.UserService;
import com.yulinlin.common.model.EntityCounter;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

//
@Slf4j
@SpringBootTest
public class AdminApplicationTests {

    @Autowired
    RouteSession session;

    @Autowired
    UserService userService;

    @Test
    public void go() throws Exception{


        UserEntity userEntity = new UserEntity();
        userEntity.setNickname("2215546");
        userEntity.setUsername("admi222121n");
        userEntity.setPasswordHash("21213");
        userService.insertOne(userEntity);

        int a = 0;


    }



}
