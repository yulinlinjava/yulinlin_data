package com.yulinlin.admin.rbac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("admin.rbac")
public class RbacProperties {
    private String bootstrapUsername = "admin";
    /** 为空时不创建初始管理员；已有账号不会被启动逻辑覆盖。 */
    private String bootstrapPassword;
}
