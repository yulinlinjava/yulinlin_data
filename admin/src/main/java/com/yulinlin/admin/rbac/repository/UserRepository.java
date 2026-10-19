package com.yulinlin.admin.rbac.repository;

import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinRepository;
import com.yulinlin.repository.dao.BaseRepository;

/** 认证信息直接查库；这里不对密码和账号状态使用查询缓存。 */
@JoinRepository("rbacUserRepository")
public interface UserRepository extends BaseRepository<UserEntity> {
    UserEntity findByUsernameEq(String username);

    /** 提交后读取普通快照，避免继承外层 @JoinSync 策略而请求已结束的事务代理。 */
    default UserEntity findSnapshotById(String id) {
        return ModelSelectWrapper.newInstance(UserEntity.class).autoUpdate(false).eq("id", id).selectOne();
    }
}
