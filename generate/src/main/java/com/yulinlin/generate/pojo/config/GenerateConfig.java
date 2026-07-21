package com.yulinlin.generate.pojo.config;

import com.yulinlin.common.model.ModelSelectWrapper;

import com.yulinlin.generate.pojo.entity.TemplateEntity;
import com.yulinlin.generate.pojo.vo.SchemaVo;
import com.yulinlin.generate.pojo.vo.TableVo;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

@Data
public class GenerateConfig {

    @ApiModelProperty("数据库集合")
    private List<SchemaVo> schemaList;


    @ApiModelProperty("表集合")
    private List<TableVo> tableList;

    @ApiModelProperty("模板集合")
    private List<TemplateEntity> templateList;


    public  static GenerateConfig build(){
        GenerateConfig config = new GenerateConfig();
        config.schemaList =   ModelSelectWrapper.newInstance(SchemaVo.class)

                .nin("schemaName", Arrays.asList("information_schema","performance_schema"))
                .selectList();
        config.tableList =   ModelSelectWrapper.newInstance(TableVo.class)

                .nin("tableSchema", Arrays.asList("information_schema","performance_schema"))
                .selectList();
       return config;
    }

}
