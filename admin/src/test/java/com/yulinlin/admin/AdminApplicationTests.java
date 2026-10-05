package com.yulinlin.admin;


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

    @Test
    public void go(){
        SysUserEntity user = new SysUserEntity();
        user.setUsername("admin");



        List<SysUserEntity> sqllite = ModelSelectWrapper.newInstance("sqlite", SysUserEntity.class)
                .selectList();



        int a = 0;


    }



}
