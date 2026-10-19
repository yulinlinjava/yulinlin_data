package com.yulinlin.admin.rbac.domain;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.TextTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JoinTable(value = "rbac_role", autoSchema = true)
@JoinIndex(fields = "code", unique = true)
public class RoleEntity extends SuperEntity<RoleEntity> {
    @JoinField(textLength = 64, description = "稳定的角色编码，创建后不修改")
    private String code;

    @JoinField(textLength = 64, description = "角色名称")
    private String name;

    @JoinField(textType = TextTypeEnum.text, description = "权限编码集合，由 ORM 进行 JSON 编解码")
    private List<String> permissions;
}
