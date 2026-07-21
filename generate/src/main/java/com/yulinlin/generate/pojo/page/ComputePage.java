package com.yulinlin.generate.pojo.page;

import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class ComputePage extends PagePo {


    @JoinWhere(condition = ConditionEnum.like)
    @ApiModelProperty("地址")
    private String host;

    @JoinWhere(condition = ConditionEnum.like)
    @ApiModelProperty("名称")
    private String title;
}
