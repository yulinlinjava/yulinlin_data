package com.yulinlin.generate.pojo.entity;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JoinTable("template")
public class TemplateEntity extends SuperEntity<TemplateEntity> {

    @Schema(description = "标题")
    private String title;

    @Schema(description = "模板")
    private String content;

    @Schema(description = "前缀")
    private String prefix;

    @Schema(description = "后缀")
    private String suffix;


    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "模块")
    private String moduleName;

}
