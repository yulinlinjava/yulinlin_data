package com.yulinlin.mongodb;


import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Date;


@SpringBootTest
@Slf4j
public class MongodbApplicationTests {

    @Autowired
    private MongoClient client;

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    UserService userService;

    @Test
    public void insert() throws Exception{


        for(int i = 0;i<5;i++){
            SysUserEntity sysUserEntity = new SysUserEntity();

            sysUserEntity.setUsername("ldl");
            sysUserEntity.setNickName("小林"+i);
            sysUserEntity.setCrtTime(new Date());
            sysUserEntity.setMoney((int)(Math.random()*100));

        }

        int a = 0;
    }

    @Test
    public void update(){

        SysUserEntity sysUserEntity = new SysUserEntity();

        sysUserEntity.setUsername("ab33c");
        sysUserEntity.setId("1");

    }


    @Test
    public void query() throws Exception{





        int a = 0;

    }

    @Test
    public void delete() throws Exception{




        for(int i = 0 ;i<3;i++){
            SysUserEntity sysUserEntity = new SysUserEntity();
            sysUserEntity.setMoney((int)(Math.random()*100));
            sysUserEntity.setUsername("ab33c");
            sysUserEntity.setId("0");

        }


        int a = 0;
    }
    @Test
    public void group2() throws Exception{


    }
    @Test
    public void group() throws Exception{


        BsonDocument document =    BsonDocument.parse("\n" +
                        "{\"aggregate\": \"sys_user\", \"pipeline\": [{\"$group\": {\"_id\": {\"username\": \"$username\"}, \"total\": {\"$avg\": \"$money\"}}}, {\"$match\": {\"total\": {\"$gt\": 1}}}], \"cursor\": {}}");

        Document documents =
                mongoTemplate.getDb().runCommand(document);


      /*  List<SysUserGroup> sysUserGroups = new BaseModelGroupWrapper<>(SysUserGroup.class)
                .selectList();*/

        int a = 0;

    }

    @Test
    public void nested(){

    }


}
