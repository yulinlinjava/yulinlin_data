package com.yulinlin.starter.controller;

import com.yulinlin.common.domain.TreeEntity;
import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.domain.vo.PageVo;
import com.yulinlin.common.service.ITreeService;
import com.yulinlin.starter.domain.ResponseVo;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

public abstract class TreeController<E extends TreeEntity<E>,R extends PagePo> extends SuperController<E,R> {

    @Autowired
    private ITreeService<E> service;



    @ApiOperation("获取树")
    @RequestMapping(value = "getTree",method = RequestMethod.POST)
    public ResponseVo<PageVo<E>> getTree(@RequestBody E po) {
        List<E> tree =  service.getTree(po);
        return ResponseVo.newInstance(PageVo.newInstance(tree));
    }





}
