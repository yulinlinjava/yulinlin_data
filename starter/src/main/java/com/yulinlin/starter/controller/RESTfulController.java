package com.yulinlin.starter.controller;

import com.yulinlin.data.lang.util.Page;
import com.yulinlin.data.lang.util.StringUtil;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;


public class RESTfulController<E extends AbstractModel<E>, Q extends PagePo>  {


    @Autowired
    protected ISuperService<E> service;


    @Operation(summary = "根据id查询数据")
    @RequestMapping(value = "get",method = RequestMethod.POST)
    public R<E> get(@RequestBody IdPo po) {
        E obj = null;
        if(StringUtil.isNull(po.getId())){
            obj = service.newInstance();
        }else {
            obj = service.findById(po.getId());
        }

        if(obj == null){
            obj = service.newInstance();
        }
        return R.ok(obj);
    }

    @Operation(summary = "分页查询")
    @RequestMapping(value = "gets",method = RequestMethod.POST)
    public R<Page<E>> gets(@RequestBody Q pagePo) {

        Page<E> vo =  service.page(pagePo);
        return R.ok(vo);
    }

    @Operation(summary = "删除数据")
    @RequestMapping(value = "delete",method = RequestMethod.POST)
    public R<Integer> delete(@RequestBody IdPo po) {
        int total =  service.deleteById(po.getId());
        if (total != 1) throw new ApiException("操作失败", 500);
        return R.ok(total, "操作成功");
    }

    @Operation(summary = "批量删除数据")
    @RequestMapping(value = "deletes",method = RequestMethod.POST)
    public R<Integer> delete(@RequestBody List<String> coll) {

       int total =  service.deleteByIdIn(coll);
        if (total != coll.size()) throw new ApiException("部分数据删除失败", 500);
        return R.ok(total, "操作成功");
    }

    @Operation(summary = "保存数据")
    @RequestMapping(value = "put",method = RequestMethod.POST)
    public R<Integer> put(@RequestBody E obj) {

        int total = service.save(obj);
        if (total <= 0) throw new ApiException("保存失败", 500);
        return R.ok(total, "保存成功");
    }

    @Operation(summary = "批量保存")
    @RequestMapping(value = "puts",method = RequestMethod.POST)
    public R<Integer> puts(@RequestBody List<E> obj) {

        int total = service.saveList(obj);
        if (total != obj.size()) throw new ApiException("部分数据保存失败", 500);
        return R.ok(total, "操作成功");
    }




    public Class<E> getEntityClass(){
        return (Class<E>)GenericUtil.getGeneric(this.getClass(),RESTfulController.class,0);
    }


}
