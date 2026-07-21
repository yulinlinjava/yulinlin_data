package com.yulinlin.starter.controller;

import com.yulinlin.data.lang.util.Page;
import com.yulinlin.data.lang.util.StringUtil;
import com.yulinlin.starter.domain.ResponseVo;
import com.yulinlin.starter.domain.IdPo;
import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.domain.vo.PageVo;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.common.service.ISuperService;
import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;


public class RESTfulController<E extends AbstractModel<E>,R extends PagePo>  {


    @Autowired
    protected ISuperService<E> service;


    @ApiOperation("根据id查询数据")
    @RequestMapping(value = "get",method = RequestMethod.POST)
    public ResponseVo<E> get(@RequestBody IdPo po) {
        E obj = null;
        if(StringUtil.isNull(po.getId())){
            obj = service.newInstance();
        }else {
            obj = service.findById(po.getId());
        }

        if(obj == null){
            obj = service.newInstance();
        }
        return ResponseVo.newInstance(obj);
    }

    @ApiOperation("分页查询")
    @RequestMapping(value = "gets",method = RequestMethod.POST)
    public ResponseVo<Page<E>> gets(@RequestBody R pagePo) {

        Page<E> vo =  service.page(pagePo);
        return ResponseVo.newInstance(vo);
    }

    @ApiOperation("删除数据")
    @RequestMapping(value = "delete",method = RequestMethod.POST)
    public ResponseVo<Integer> delete(@RequestBody IdPo po) {
        int total =  service.deleteById(po.getId());
        return ResponseVo.newInstance(total,total == 1 ?"操作成功":"操作失败");
    }

    @ApiOperation("批量删除数据")
    @RequestMapping(value = "deletes",method = RequestMethod.POST)
    public ResponseVo<Integer> delete(@RequestBody List<String> coll) {

       int total =  service.deleteByIdIn(coll);
        return ResponseVo.newInstance(total,total == coll.size() ?"操作成功":"操作失败");
    }

    @ApiOperation("保存数据")
    @RequestMapping(value = "put",method = RequestMethod.POST)
    public ResponseVo<Integer>  put(@RequestBody E obj) {

        int total = service.save(obj);
        return ResponseVo.newInstance(total,total> 0 ?"保存成功":"保存失败");
    }

    @ApiOperation("批量保存")
    @RequestMapping(value = "puts",method = RequestMethod.POST)
    public ResponseVo<Integer>  puts(@RequestBody List<E> obj) {

        int total = service.saveList(obj);
        return ResponseVo.newInstance(total,total == obj.size() ?"操作成功":"操作失败");
    }




    public Class<E> getEntityClass(){
        return (Class<E>)GenericUtil.getGeneric(this.getClass(),RESTfulController.class,0);
    }


}
