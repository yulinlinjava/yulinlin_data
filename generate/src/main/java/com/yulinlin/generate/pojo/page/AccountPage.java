package com.yulinlin.generate.pojo.page;

import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AccountPage extends PagePo {
    @JoinWhere(condition = ConditionEnum.like)
    @ApiModelProperty("账号")
    private String username;
    @ApiModelProperty("密码")
    @JoinWhere(condition = ConditionEnum.like)
    private String nickname;

}
