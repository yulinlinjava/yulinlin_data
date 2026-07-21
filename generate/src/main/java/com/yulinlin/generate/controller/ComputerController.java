package com.yulinlin.generate.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ZipUtil;
import com.yulinlin.common.model.ModelSelectWrapper;

import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.generate.pojo.config.GenerateConfig;
import com.yulinlin.generate.pojo.entity.ComputerEntity;
import com.yulinlin.generate.pojo.entity.TemplateEntity;
import com.yulinlin.generate.pojo.page.ComputePage;
import com.yulinlin.generate.pojo.po.GeneratePo;
import com.yulinlin.generate.service.ITemplateService;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import com.yulinlin.starter.controller.SuperController;
import com.yulinlin.starter.domain.IdPo;
import com.yulinlin.starter.domain.ResponseVo;
import com.yulinlin.starter.util.ResponseUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;


@Api(tags = "计算机")
@RestController
@RequestMapping("compute")
public class ComputerController extends SuperController<ComputerEntity, ComputePage> {

    @Autowired
    ITemplateService templateService;

    @Autowired
     JdbcSessionFactory jdbcSessionFactory;



    @ApiOperation("代码生成配置参数")
    @RequestMapping(value = "getGenerateConfig",method = RequestMethod.POST)
    public ResponseVo<GenerateConfig> getGenerateConfig(@RequestBody IdPo po) {


        GenerateConfig config =  GenerateConfig.build();

        List<TemplateEntity> templateList = ModelSelectWrapper.newInstance(TemplateEntity.class).selectList();

        config.setTemplateList(templateList);
        return ResponseVo.newInstance(config);
    }



    @RequestMapping(value = "generateCode",method = RequestMethod.POST)
    public void generateCode( GeneratePo po) throws Exception{

        String folder = System.currentTimeMillis()+"";
        String path = "/"+folder;

        File root =  new File(path);
        root.mkdirs();

        templateService.generateCodeFile(po,root);
        File f =  ZipUtil.zip(root);



        ResponseUtil.downloadFile(f);

        FileUtil.del(f);
        FileUtil.del(root);




    }
}
