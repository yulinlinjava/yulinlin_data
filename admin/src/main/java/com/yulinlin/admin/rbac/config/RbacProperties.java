package com.yulinlin.admin.rbac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties("admin.rbac")
public class RbacProperties {
    /** 固定有效期，读取会话不自动延长。 */
    private Duration tokenTtl = Duration.ofHours(2);
    private long maximumSessions = 10000;
    private String bootstrapUsername = "admin";
    /** 为空时不创建初始管理员；已有账号不会被启动逻辑覆盖。 */
    private String bootstrapPassword;
}
