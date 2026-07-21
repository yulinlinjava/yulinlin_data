package com.yulinlin.generate.controller;


import com.yulinlin.generate.pojo.entity.TemplateEntity;
import com.yulinlin.generate.pojo.page.TemplatePage;
import com.yulinlin.starter.controller.SuperController;
import com.yulinlin.starter.domain.ResponseVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@Api(tags = "模板")
@RestController
@RequestMapping("template")
public class TemplateController extends SuperController<TemplateEntity, TemplatePage> {


    @ApiOperation("配置参数")
    @RequestMapping(value = "config",method = RequestMethod.POST)
    public ResponseVo<HashMap> config() {
        return ResponseVo.newInstance(new HashMap());
    }
}
