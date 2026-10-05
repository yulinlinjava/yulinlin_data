package com.yulinlin.admin;


import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import com.yulinlin.data.lang.util.ListString;

import lombok.Data;


import java.util.Date;


@Data

@JoinTable("sys_user")
public class SysUserEntity extends SuperEntity<SysUserEntity>  {



        @JoinWhere
        @JoinField
        private String secretKey;


        @JoinWhere
        @JoinField
        private String username;



}
