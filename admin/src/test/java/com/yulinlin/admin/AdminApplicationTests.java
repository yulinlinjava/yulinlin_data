package com.yulinlin.admin;


import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

//
@Slf4j
@SpringBootTest
public class AdminApplicationTests {

    @Autowired
    RouteSession session;

    @Test
    public void go(){
        RouteSession route = SessionUtil.route();
        EntitySession session1 = route.session("");
        System.out.println(1);
    }



}
