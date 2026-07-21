package com.yulinlin.admin;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;

@Data
@JoinTable("oss_file")
public class OssFileEntity extends SuperEntity<OssFileEntity> {

    private String url;

    private String mappingUrl;


}
