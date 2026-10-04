package com.yulinlin.admin;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import lombok.Data;

@Data
@JoinTable("cms_test")
public class CmsTest {

    @JoinField
    @JoinWhere
    @JoinMeta(primaryKey = true)
    private String id;

    private String name;


}
