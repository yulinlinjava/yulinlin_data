package com.yulinlin.generate.service.impl;

import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.service.impl2.ServiceImpl;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.lang.util.Page;
import com.yulinlin.generate.pojo.entity.ComputerEntity;
import com.yulinlin.generate.service.IComputerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
@Transactional
public class ComputeServiceImpl extends ServiceImpl<ComputerEntity> implements IComputerService {


    @Override
    public Page<ComputerEntity> page(PagePo po) {
        ComputerEntity e = new ComputerEntity();
        e.setTitle("本机");
        e.setId(SessionUtil.nowSession());
        return Page.of(Arrays.asList(e));
    }

}
