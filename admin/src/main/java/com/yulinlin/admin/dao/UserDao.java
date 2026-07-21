package com.yulinlin.admin.dao;

import com.yulinlin.admin.SysUserEntity;
import com.yulinlin.data.lang.util.ListString;
import com.yulinlin.repository.anno.JoinRepository;
import com.yulinlin.repository.dao.BaseRepository;

@JoinRepository
public interface UserDao extends BaseRepository<SysUserEntity> {


}
