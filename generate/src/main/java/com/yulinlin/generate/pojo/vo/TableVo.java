package com.yulinlin.generate.pojo.vo;

import com.yulinlin.common.model.AbstractQueryModel;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.lang.util.StringUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@JoinTable("information_schema.tables")
@Data
public class TableVo   implements AbstractQueryModel<TableVo> {

    @Schema(description = "数据库")
    private String tableSchema;

    @Schema(description = "表明")
    private String tableName;

    @Schema(description = "表注释")
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
