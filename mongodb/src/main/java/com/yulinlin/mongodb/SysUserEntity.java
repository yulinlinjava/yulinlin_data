package com.yulinlin.mongodb;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import lombok.Data;

import java.util.Date;
import java.util.List;


@Data

@JoinTable("sys_user")
public class SysUserEntity  {

        @JoinWhere
        @JoinMeta(primaryKey = true)
        @JoinField(name = "_id")
        private String id;

        @JoinWhere
        @JoinField
        private String username;

        @JoinWhere
        @JoinField
        private String nickName;
        @JoinWhere
        @JoinField
        private int money;
        @JoinWhere
        @JoinField
        private Date crtTime;

}
