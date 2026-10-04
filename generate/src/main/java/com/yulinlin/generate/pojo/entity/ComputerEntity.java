package com.yulinlin.generate.pojo.entity;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JoinTable("computer")
public class ComputerEntity extends SuperEntity<ComputerEntity> {

    @Schema(description = "url")
    private String url;



    @Schema(description = "名称")
    private String title;

    @Schema(description = "账号")
    private String username;

    @Schema(description = "密码")
    private String password;

}
