package com.yulinlin.elasticsearch;


import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@JoinTable("sys_dept")
@Data
public class SysDept {

    @JoinField
    @JoinMeta(primaryKey = true)
    private String id;

    @JoinField(name = "_score")
    @JoinMeta(rw = false)
    private String meta;

    private String title;

    private List<User> users = new ArrayList<>();

    public void addUser(String name,String value){
        users.add(new User(name,value));
    }

    @Data
    public static class  User{
        private String name;
        private String value;

        public User(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

}
