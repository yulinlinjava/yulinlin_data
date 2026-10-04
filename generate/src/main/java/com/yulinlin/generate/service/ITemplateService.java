package com.yulinlin.generate.service;



import com.yulinlin.common.service.ISuperService;
import com.yulinlin.generate.pojo.entity.TemplateEntity;
import com.yulinlin.generate.pojo.po.GeneratePo;

import java.io.File;

public interface ITemplateService extends ISuperService<TemplateEntity> {

    //代码生成到指定文件夹
     void generateCodeFile(GeneratePo po, File root);

}
