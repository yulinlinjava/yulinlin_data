package com.yulinlin.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.yulinlin.data.lang.json.JsonUtil;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

@Service
@Transactional
public class UserService {


    @Autowired
    private MongoClient client;

    public void insert(){



        MongoCollection<Document> collection = client.getDatabase("yulinlin").getCollection("sys_user");



        HashMap<Object, Object> map = new HashMap<>();
        int i = 5;
        map.put("_id",""+i);
        map.put("username","ldl"+i);
        map.put("nickname","ldl"+i);
        String json =  JsonUtil.toJson(map);

        collection.insertOne(Document.parse(json));

        throw new RuntimeException();
    }
}
