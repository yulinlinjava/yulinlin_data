package com.yulinlin.generate.pojo.vo;

import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinTable;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@JoinTable("information_schema.schemata")
public class SchemaVo {

    @ApiModelProperty("数据库")

    private String schemaName;


    public List<TableVo> findTable(){
        return ModelSelectWrapper.newInstance(TableVo.class)
                .eq("tableSchema",schemaName)
                .selectList();
    }

}
