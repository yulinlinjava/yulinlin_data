package com.yulinlin.admin.dao;

import com.yulinlin.admin.SysRoleEntity;
import com.yulinlin.admin.SysUserEntity;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.lang.util.ListString;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {




    public void test(){
        SysUserEntity user= ModelSelectWrapper.newInstance(SysUserEntity.class)

                .selectOne();

    }
}
