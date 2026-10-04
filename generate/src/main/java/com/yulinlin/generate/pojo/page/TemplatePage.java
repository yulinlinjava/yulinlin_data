package com.yulinlin.generate.pojo.page;

import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class TemplatePage extends PagePo {

    @Schema(description = "名称")
    @JoinWhere(condition = ConditionEnum.like)
    private String title;


    @Schema(description = "文件类型")
    @JoinWhere(condition = ConditionEnum.like)
    private String fileType;

}
