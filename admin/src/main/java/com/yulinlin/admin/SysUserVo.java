package com.yulinlin.admin;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinQuery;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;


@Data
@ApiModel("系统用户")
@JoinTable(left = "sys_user a",right = "sys_dept b",on = "a.sys_dept_id = b.id")
public class SysUserVo{




        @JoinField(exist = false)
        @JoinQuery(model = SysRoleEntity.class,value = "${username}")
        private Integer roleTotal;



        @NotEmpty(message = "必填")
        @ApiModelProperty("账号")
        @JoinWhere
        @JoinField(name = "a.username")
        private String username;




        @NotEmpty(message = "必填")
        @ApiModelProperty("账号")
        @JoinWhere
        @JoinField(name = "b.deptName")
        private String deptName;

}
