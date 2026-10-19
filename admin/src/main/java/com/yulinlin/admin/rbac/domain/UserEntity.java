package com.yulinlin.admin.rbac.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinQuery;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.TextTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JoinTable(value = "rbac_user", autoSchema = true)
@JoinIndex(fields = "username", unique = true)
public class UserEntity extends SuperEntity<UserEntity> {
    @JoinField(textLength = 64, description = "登录用户名，创建后不修改")
    private String username;

    @JsonIgnore
    @ToString.Exclude
    @JoinField(textLength = 255, description = "带盐密码哈希，不返回给客户端")
    private String passwordHash;

    @JoinField(textLength = 64, description = "昵称")
    private String nickname;

    @JoinField(description = "用户状态：enabled、disabled、locked")
    private UserStatus status;

    @JoinField(textType = TextTypeEnum.text, description = "角色编码集合，由 ORM 进行 JSON 编解码")
    private List<String> roleCodes;

    @JoinField(exist = false)
    @JoinQuery(primary = "code", value = "roleCodes")
    private List<RoleEntity> roles;
}
