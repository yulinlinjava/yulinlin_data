package com.yulinlin.generate.pojo.vo;

import com.yulinlin.common.model.AbstractQueryModel;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.lang.util.StringUtil;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@JoinTable("information_schema.tables")
@Data
public class TableVo   implements AbstractQueryModel<TableVo> {

    @ApiModelProperty("数据库")
    private String tableSchema;

    @ApiModelProperty("表明")
    private String tableName;

    @ApiModelProperty("表注释")
    private String tableComment;



    public List<ColumnVo> findColumnList(){

         return ModelSelectWrapper.newInstance(ColumnVo.class)
                .eq("tableSchema",tableSchema)
                .eq("tableName",tableName)
                .selectList();



    }

    public String getClassName(){
        return StringUtil.tableToClass(tableName);
    }


}
