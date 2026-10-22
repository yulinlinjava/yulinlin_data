package com.yulinlin.security.session;

/** userId=null 查询全局；pageSize=null 使用默认值。游标仅适用于同一实现、同一查询条件。 */
public record SessionQuery(String userId, String deviceType, String cursor, Integer pageSize) {
    public SessionQuery {
        if (userId != null) SessionSupport.requireText(userId, "userId");
        if (deviceType != null) SessionSupport.requireText(deviceType, "deviceType");
        if (cursor != null && (cursor.isBlank() || cursor.length() > 256)) {
            throw new IllegalArgumentException("无效的会话分页游标");
        }
        if (pageSize != null && pageSize <= 0) throw new IllegalArgumentException("pageSize 必须大于 0");
    }
    public static SessionQuery all() { return new SessionQuery(null, null, null, null); }
    public static SessionQuery user(String userId) {
        return new SessionQuery(SessionSupport.requireText(userId, "userId"), null, null, null);
    }
}
