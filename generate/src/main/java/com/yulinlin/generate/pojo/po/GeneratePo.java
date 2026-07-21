package com.yulinlin.generate.pojo.po;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
public class GeneratePo {

    @ApiModelProperty("包空间")
    private String packageSpace;

    @ApiModelProperty("计算机id")
    private String computeId;

    @ApiModelProperty("数据库")
    private String tableSchema;

    @ApiModelProperty("表明集合")
    private List<String> tableNames;

    @ApiModelProperty("模板id集合")
    private List<String> templateIds;

    @ApiModelProperty("领域")
    private String domain;
}
