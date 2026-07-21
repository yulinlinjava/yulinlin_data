package com.yulinlin.mongodb;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import lombok.Data;

import java.util.List;


@Data

@JoinTable("sys_role")
public class SysRoleEntity {



        @JoinWhere
        @JoinField
        private String nickName;


        public SysRoleEntity(String nickName) {
                this.nickName = nickName;
        }

        public SysRoleEntity() {

        }
}
