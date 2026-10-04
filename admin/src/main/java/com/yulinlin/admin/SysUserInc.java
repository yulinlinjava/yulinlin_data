package com.yulinlin.admin;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;

@Data
@JoinTable("sys_user")
public class SysUserInc {

    @JoinMeta(primaryKey = true)
    private String id;


    @JoinField(version = true)
    private long crtTime;
}
