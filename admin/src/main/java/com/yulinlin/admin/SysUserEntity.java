package com.yulinlin.admin;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.lang.util.ListString;
import com.yulinlin.data.core.event.IInitEvent;
import com.yulinlin.data.core.event.IProxyEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;


@Data
@Schema(description = "系统用户")
@JoinTable("account")
public class SysUserEntity extends SuperEntity<SysUserEntity> implements IInitEvent,IProxyEvent {


        @NotEmpty(message = "必填")
        @Schema(description = "昵称")
        @JoinWhere
        @JoinField(name = "nickname")
        private String nickname;




        @JoinWhere(and = false)
    private String username;



        @NotEmpty(message = "必填")
        @Schema(description = "密码")
        @JoinWhere
        @JoinField
        private String password;


        private ListString data;



   /*     @NotEmpty(message = "必填")
        @Schema(description = "角色集合")
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
