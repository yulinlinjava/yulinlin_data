package com.yulinlin.security.session;

import java.util.Optional;
import java.util.Set;

/**
 * 会话管理 SPI；操作真实登录会话，不签发凭证。调用方负责授权和审计。
 * 替换本接口不会自动替换登录/鉴权/限额策略；自定义存储必须接入同一会话生命周期。
 * reason 供业务审计扩展使用，内置适配器不另建审计库。
 */
public interface LoginSessionManager {
    SessionPage listSessions(SessionQuery query);
    Optional<LoginSessionView> getSession(String userId, String sessionId);
    long countSessions(String userId);
    /** 已失效/不存在的目标视为成功；存储故障必须传播，不能假装已撤销。 */
    void kickoutSession(String userId, String sessionId, String reason);
    void kickoutAll(String userId, String reason);
    default void kickoutByDeviceType(String userId, String deviceType, String reason) {
        throw new UnsupportedOperationException("当前会话管理器不支持按设备类型踢人");
    }
    Set<SessionCapability> capabilities();
}
