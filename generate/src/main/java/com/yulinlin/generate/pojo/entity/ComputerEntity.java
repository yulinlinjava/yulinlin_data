package com.yulinlin.generate.pojo.entity;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinTable;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@JoinTable("computer")
public class ComputerEntity extends SuperEntity<ComputerEntity> {

    @ApiModelProperty("url")
    private String url;



    @ApiModelProperty("名称")
    private String title;

    @ApiModelProperty("账号")
    private String username;

    @ApiModelProperty("密码")
    private String password;

}
