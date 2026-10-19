package com.yulinlin.admin.rbac.domain;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 同表的部分投影，不拥有 Schema；清理角色引用时避免加载密码和全部关联。 */
@Data
@EqualsAndHashCode(callSuper = true)
@JoinTable("rbac_user")
public class UserRoleProjection extends SuperEntity<UserRoleProjection> {
    private List<String> roleCodes;
}
