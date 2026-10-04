package com.yulinlin.generate.pojo.po;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;



@Data
public class LoginPo {


    @Schema(description = "账号")
    private String username;


    @Schema(description = "密码")
    private String password;
}
