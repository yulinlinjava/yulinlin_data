package com.yulinlin.generate.pojo.page;

import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class TemplatePage extends PagePo {

    @ApiModelProperty("名称")
    @JoinWhere(condition = ConditionEnum.like)
    private String title;


    @ApiModelProperty("文件类型")
    @JoinWhere(condition = ConditionEnum.like)
    private String fileType;

}
