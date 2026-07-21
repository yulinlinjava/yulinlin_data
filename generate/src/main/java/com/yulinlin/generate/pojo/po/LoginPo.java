package com.yulinlin.generate.pojo.po;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;



@Data
public class LoginPo {


    @ApiModelProperty("账号")
    private String username;


    @ApiModelProperty("密码")
    private String password;
}
