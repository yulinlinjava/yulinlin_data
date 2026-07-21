package com.yulinlin.admin;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;


@Data
@JoinTable("sys_dept")
public class SysElement extends IdEntity {

    private String deptName;


}
