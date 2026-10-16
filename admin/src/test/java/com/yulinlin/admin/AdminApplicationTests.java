package com.yulinlin.admin;


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

    @Test
    public void go() throws Exception{
        SysUserEntity user = new SysUserEntity();
        user.setUsername("admin");

        EntityCounter.newInstance(SysUserEntity.class,1)
                        .inc(SysUserEntity::getAge,1)
                                .submit();
        EntityCounter.newInstance(SysUserEntity.class,1)
                .inc(SysUserEntity::getAge,1)
                .submit();
        EntityCounter.newInstance(SysUserEntity.class,2)
                .inc(SysUserEntity::getAge,1)
                .execute();
        Thread.sleep(110*1000);


        int a = 0;


    }



}
