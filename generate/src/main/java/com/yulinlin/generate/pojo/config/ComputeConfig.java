package com.yulinlin.generate.pojo.config;

import com.yulinlin.common.model.ModelSelectWrapper;

import com.yulinlin.generate.pojo.entity.TemplateEntity;
import lombok.Data;

import java.util.List;

@Data
public class ComputeConfig {

    private List<TemplateEntity> templateList;


    public static ComputeConfig build(){
        ComputeConfig config = new ComputeConfig();
        config.templateList = ModelSelectWrapper.newInstance(TemplateEntity.class).selectList();
        return config;
    }
}
