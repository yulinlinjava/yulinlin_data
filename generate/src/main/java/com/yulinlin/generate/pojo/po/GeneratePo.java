package com.yulinlin.generate.pojo.po;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class GeneratePo {

    @Schema(description = "包空间")
    private String packageSpace;

    @Schema(description = "计算机id")
    private String computeId;

    @Schema(description = "数据库")
    private String tableSchema;

    @Schema(description = "表明集合")
    private List<String> tableNames;

    @Schema(description = "模板id集合")
    private List<String> templateIds;

    @Schema(description = "领域")
    private String domain;
}
