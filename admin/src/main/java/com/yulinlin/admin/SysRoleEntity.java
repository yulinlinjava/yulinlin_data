package com.yulinlin.admin;


import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;


@Data
@ApiModel("系统角色")
@JoinTable("sys_role")
public class SysRoleEntity extends SuperEntity<SysRoleEntity> {






        @NotEmpty(message = "必填")
        @ApiModelProperty("角色名称")
        @JoinWhere
        @JoinField
        private String roleName;

        @NotEmpty(message = "必填")
        @ApiModelProperty("排序数值")
        @JoinWhere
        @JoinField
        private Integer sortValue;







}
