package com.yulinlin.security.session;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.security.session")
public class SessionManagementProperties implements InitializingBean {
    /** null 表示采用当前安全实现的默认值，不把“未配置”当作显式关闭或开启。 */
    private Boolean enabled;
    private int maxSessions = -1;
    private final Query query = new Query();
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled(boolean implementationDefault) { return enabled == null ? implementationDefault : enabled; }
    public boolean isExplicitlyEnabled() { return Boolean.TRUE.equals(enabled); }
    public int getMaxSessions() { return maxSessions; }
    public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
    public Query getQuery() { return query; }
    @Override public void afterPropertiesSet() { validate(); }
    public void validate() {
        if (maxSessions != -1 && maxSessions <= 0) {
            throw new IllegalArgumentException("yulinlin.security.session.max-sessions 必须为 -1 或正整数");
        }
        if (query.defaultPageSize <= 0 || query.maxPageSize <= 0 || query.defaultPageSize > query.maxPageSize) {
            throw new IllegalArgumentException("会话分页大小必须为正数，default-page-size 不能超过 max-page-size");
        }
    }
    public int pageSize(SessionQuery request) {
        validate();
        int size = request.pageSize() == null ? query.defaultPageSize : request.pageSize();
        if (size > query.maxPageSize) throw new IllegalArgumentException("pageSize 超过会话分页上限");
        return size;
    }
    public static class Query {
        private int defaultPageSize = 20;
        private int maxPageSize = 100;
        public int getDefaultPageSize() { return defaultPageSize; }
        public void setDefaultPageSize(int value) { defaultPageSize = value; }
        public int getMaxPageSize() { return maxPageSize; }
        public void setMaxPageSize(int value) { maxPageSize = value; }
    }
}
