package com.yulinlin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

/** 默认 Token 请求头约定；由拦截器在初始化时读取，修改配置后需重启。 */
@ConfigurationProperties("yulinlin.security.auth")
public class SecurityAuthProperties {
    private static final Pattern HTTP_TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    /** 只读取此请求头；不自动读取同名 Cookie 或 URL 参数。 */
    private String tokenHeader = "Authorization";

    /** 前缀后由框架补一个空格；空字符串表示直接读取原始 Token。 */
    private String tokenPrefix = "Bearer";

    public String getTokenHeader() { return tokenHeader; }

    public void setTokenHeader(String tokenHeader) {
        if (tokenHeader == null || !HTTP_TOKEN.matcher(tokenHeader).matches()) {
            throw new IllegalArgumentException("yulinlin.security.auth.token-header 必须是合法请求头名称，不能包含空白或冒号");
        }
        this.tokenHeader = tokenHeader;
    }

    public String getTokenPrefix() { return tokenPrefix; }

    public void setTokenPrefix(String tokenPrefix) {
        if (tokenPrefix == null || (!tokenPrefix.isEmpty() && !HTTP_TOKEN.matcher(tokenPrefix).matches())) {
            throw new IllegalArgumentException("yulinlin.security.auth.token-prefix 必须是不含空白的前缀；无前缀请设置为空字符串");
        }
        this.tokenPrefix = tokenPrefix;
    }
}
