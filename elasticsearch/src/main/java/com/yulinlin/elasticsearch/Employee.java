package com.yulinlin.elasticsearch;


import com.yulinlin.data.core.anno.*;
import lombok.Data;

@JoinTable("employee")
@Data
public class Employee {



    private String id;

    @JoinWhere(condition = ConditionEnum.like)
    @JoinMeta(params = {
            @MetaParam(name = "preTags",value = "<span>"),
            @MetaParam(name = "postTags",value = "</span>"),
    })
    private String name;

    private String job;

    private String age;


    private String sal;

    private String gender;


    private String time;



}
