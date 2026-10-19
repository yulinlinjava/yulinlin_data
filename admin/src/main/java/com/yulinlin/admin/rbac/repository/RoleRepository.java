package com.yulinlin.admin.rbac.repository;

import com.yulinlin.admin.rbac.domain.RoleEntity;
import com.yulinlin.data.core.anno.JoinRepository;
import com.yulinlin.repository.dao.BaseRepository;

import java.util.Collection;
import java.util.List;

@JoinRepository("rbacRoleRepository")
public interface RoleRepository extends BaseRepository<RoleEntity> {
    RoleEntity findByCodeEq(String code);
    List<RoleEntity> findByCodeIn(Collection<String> codes);
}
