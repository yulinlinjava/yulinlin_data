package com.yulinlin.starter.controller;

import com.yulinlin.data.lang.util.Page;
import com.yulinlin.starter.domain.IdPo;
import com.yulinlin.starter.domain.R;
import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.exception.ApiException;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.common.service.ISuperService;
import com.yulinlin.data.lang.reflection.GenericUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;


public class SuperController<E extends AbstractModel<E>, Q extends PagePo>  {


    @Autowired
    protected ISuperService<E> service;


    @Operation(summary = "根据id查询数据")
    @PostMapping("getById")
    public R<E> getById(@RequestBody IdPo po) {

        E obj = service.findById(po.getId());
        if(obj == null){
            obj = service.newInstance();
        }
        return R.ok(obj);
    }

    @Operation(summary = "删除数据")
    @PostMapping("deleteByIdIn")
    public R<Integer> deleteByIdIn(@RequestBody List<String> coll) {

       int total =  service.deleteByIdIn(coll);
        if (total != coll.size()) throw new ApiException("部分数据删除失败", 500);
        return R.ok(total, "成功删除：" + total + "条数据");
    }

    @Operation(summary = "保存数据")
    @PostMapping("saveOne")
    public R<Integer> saveOne(@RequestBody E obj) {

        int total = service.save(obj);

        if (total <= 0) throw new ApiException("保存失败", 500);
        return R.ok(total, "成功保存：" + total + "条数据");
    }

    @Operation(summary = "批量保存")
    @PostMapping("saveList")
    public R<Integer> saveList(@RequestBody List<E> obj) {

        int total = service.saveList(obj);
        if (total != obj.size()) throw new ApiException("部分数据保存失败", 500);
        return R.ok(total, "成功保存：" + total + "条数据");
    }


    @Operation(summary = "分页查询")
    @PostMapping("page")
    public R<Page<E>> page(@RequestBody Q pagePo) {

        Page<E> vo =  service.page(pagePo);
        return R.ok(vo);
    }


    public Class<E> getEntityClass(){
        return (Class<E>)GenericUtil.getGeneric(this.getClass(),SuperController.class,0);
    }


}
