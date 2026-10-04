package com.yulinlin.generate.pojo.page;

import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AccountPage extends PagePo {
    @JoinWhere(condition = ConditionEnum.like)
    @Schema(description = "账号")
    private String username;
    @Schema(description = "密码")
    @JoinWhere(condition = ConditionEnum.like)
    private String nickname;

}
