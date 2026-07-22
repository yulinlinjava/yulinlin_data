package com.yulinlin.admin;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.lang.util.ListString;
import com.yulinlin.data.core.event.IInitEvent;
import com.yulinlin.data.core.event.IProxyEvent;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;


@Data
@ApiModel("系统用户")
@JoinTable("account")
public class SysUserEntity extends SuperEntity<SysUserEntity> implements IInitEvent,IProxyEvent {


        @NotEmpty(message = "必填")
        @ApiModelProperty("昵称")
        @JoinWhere
        @JoinField(name = "nickname")
        private String nickname;




        @JoinWhere(and = false)
    private String username;



        @NotEmpty(message = "必填")
        @ApiModelProperty("密码")
        @JoinWhere
        @JoinField
        private String password;

    @NotEmpty(message = "必填")
    @ApiModelProperty("金币")
    @JoinWhere
    @JoinField
        private Integer gold;

        private ListString data;



   /*     @NotEmpty(message = "必填")
        @ApiModelProperty("角色集合")
        @JoinWhere
        @JoinField
        private ListString<String> sysRoleIds;
*/

        @Override
        public void init() {

        }

        @Override
        public void finishInjection() {

        }



}
