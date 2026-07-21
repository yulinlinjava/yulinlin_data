package com.yulinlin.generate.pojo.vo;


import com.yulinlin.common.model.AbstractQueryModel;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.lang.util.StringUtil;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@JoinTable("information_schema.columns")
@Data
public class ColumnVo implements AbstractQueryModel<ColumnVo> {


    @ApiModelProperty("列明")
    @JoinField(name = "column_name")
    private String name;

    @ApiModelProperty("列类型")
    private String columnType;

    @ApiModelProperty("列描述")
    private String columnComment;

    @ApiModelProperty("数据库")
    private String tableSchema;

    @ApiModelProperty("表明")
    private String tableName;



    public String getFieldName(){
        return StringUtil.columnToJava(name);
    }


    public String getFieldType(){
        if(columnType.startsWith("varchar")){
            return "String";
        }else if(columnType.startsWith("date")){
            return  "Date";
        }else if(columnType.startsWith("tiny")){
            return  "Boolean";
        }else if(columnType.startsWith("int")){
            return  "Integer";
        }
        return  "String";
    }

}
