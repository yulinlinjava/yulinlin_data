package com.yulinlin.generate.pojo.entity;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinTable;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@JoinTable("template")
public class TemplateEntity extends SuperEntity<TemplateEntity> {

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("模板")
    private String content;

    @ApiModelProperty("前缀")
    private String prefix;

    @ApiModelProperty("后缀")
    private String suffix;


    @ApiModelProperty("文件类型")
    private String fileType;

    @ApiModelProperty("模块")
    private String moduleName;

}
