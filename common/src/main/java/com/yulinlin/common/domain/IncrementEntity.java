package com.yulinlin.common.domain;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.UpdateTypeEnum;
import lombok.Data;

import java.util.concurrent.atomic.LongAdder;

/**
 * @deprecated 使用 {@link com.yulinlin.common.model.EntityCounter}。新接口基于实体字段和主键，
 * 支持单行多字段、立即执行和进程内聚合执行，不再暴露动态表名与列名。
 */
@Deprecated(since = "3.0", forRemoval = false)
@JoinTable("${table}")
@Data
public class IncrementEntity {

    @JoinField(exist = false)
    private String table;


    @JoinMeta
    @JoinField(name = "${column}",updateType = UpdateTypeEnum.inc)
    private LongAdder columnValue = new LongAdder();

    @JoinField(exist = false)
    private String column;

    @JoinField(exist = false)
    private String idColumn="id";


    @JoinMeta(primaryKey = true)
    @JoinField(name = "${idColumn}")
    private String idValue;


    @JoinField(exist = false)
    private boolean delay = true;


    public IncrementEntity(String table, String column, String idValue) {
        this.table = table;
        this.column = column;
        this.idValue = idValue;
    }
}
