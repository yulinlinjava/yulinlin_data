package com.yulinlin.admin;


import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import com.yulinlin.data.lang.util.ListString;

import lombok.Data;


import java.util.Date;


@Data

@JoinTable(value = "obj2ka", autoSchema = true)
public class SysUserEntity extends SuperEntity<SysUserEntity>  {



        @JoinWhere
        @JoinField
        private String secretKey2;


        @JoinWhere
        @JoinField
        private String username;


        public Object getO(){
                return 1;
        }

}
