package com.yulinlin.starter.controller;

import com.yulinlin.data.lang.util.Page;
import com.yulinlin.starter.domain.ResponseVo;
import com.yulinlin.starter.domain.IdPo;
import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.domain.vo.PageVo;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.common.service.ISuperService;
import com.yulinlin.data.lang.reflection.GenericUtil;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;


public class SuperController<E extends AbstractModel<E>,R extends PagePo>  {


    @Autowired
    protected ISuperService<E> service;


    @ApiOperation("根据id查询数据")
    @RequestMapping(value = "getById",method = RequestMethod.POST)
    public ResponseVo<E> getById(@RequestBody IdPo po) {

        E obj = service.findById(po.getId());
        if(obj == null){
            obj = service.newInstance();
        }
        return ResponseVo.newInstance(obj);
    }



/*    @ApiOperation("根据id查询数据")
    @RequestMapping(value = "findById",method = RequestMethod.POST)
    public ResponseVo<E> findById(@RequestBody String id) {

        E obj = service.findById(id);
        if(obj == null){
            obj = service.newInstance();
        }
        return ResponseVo.newInstance(obj);
    }*/


    @ApiOperation("删除数据")
    @RequestMapping(value = "deleteByIdIn",method = RequestMethod.POST)
    public ResponseVo<Integer> deleteByIdIn(@RequestBody List<String> coll) {

       int total =  service.deleteByIdIn(coll);
        return ResponseVo.newInstance(total,"成功删除："+total+"条数据");
    }

    @ApiOperation("保存数据")
    @RequestMapping(value = "saveOne",method = RequestMethod.POST)
    public ResponseVo<Integer>  saveOne(@RequestBody E obj) {

        int total = service.save(obj);

        return ResponseVo.newInstance(total,"成功保存："+total+"条数据");
    }

    @ApiOperation("批量保存")
    @RequestMapping(value = "saveList",method = RequestMethod.POST)
    public ResponseVo<Integer>  saveList(@RequestBody List<E> obj) {

        int total = service.saveList(obj);
        return ResponseVo.newInstance(total,"成功保存："+total+"条数据");
    }


    @ApiOperation("分页查询")
    @RequestMapping(value = "page",method = RequestMethod.POST)
    public ResponseVo<Page<E>> page(@RequestBody R pagePo) {

        Page<E> vo =  service.page(pagePo);
        return ResponseVo.newInstance(vo);
    }


    public Class<E> getEntityClass(){
        return (Class<E>)GenericUtil.getGeneric(this.getClass(),SuperController.class,0);
    }


}
