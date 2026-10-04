package com.yulinlin.admin;

import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class AuthService {



    @Transactional
    public void test(){



        SysUserEntity sys = ModelSelectWrapper.newInstance(SysUserEntity.class)
                .eq("1611305135436210176")
                .selectOne();


    }
}
