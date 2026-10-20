package com.yulinlin.security.satoken;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.strategy.SaStrategy;
import com.yulinlin.security.auth.AbstractUserSecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginResult;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.UserStore;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 会话、设备隔离、踢人和存储均委托 Sa-Token，配置沿用 sa-token.*。
 * 统一入口通过 Authorization: Bearer 恢复 SecurityContext，不另建 Token 缓存。
 * 本适配器不读 Cookie、不写响应 Cookie；若使用原生 StpUtil HTTP 入口，需另行配置其 Token 读取规则。
 */
public final class SaTokenSecurityManager extends AbstractUserSecurityManager {
    private final Supplier<StpLogic> logicSupplier;

    public SaTokenSecurityManager(UserStore users, Supplier<StpLogic> logicSupplier) {
        super(users);
        this.logicSupplier = Objects.requireNonNull(logicSupplier, "StpLogic 提供者不能为空");
    }

    /** 可访问底层能力；默认跟随 StpUtil 配置，避免在 Spring 注入完成前捕获旧 StpLogic。 */
    public StpLogic stpLogic() {
        return Objects.requireNonNull(logicSupplier.get(), "StpLogic 不能为空");
    }

    @Override
    public LoginResult login(String userId, String deviceType) {
        SecurityContext.clear();
        requireText(deviceType, "设备类型");
        IUserDetails user = loadRequiredUser(userId);
        StpLogic logic = stpLogic();
        String token = logic.createLoginSession(userId,
                logic.createSaLoginParameter().setDeviceType(deviceType));
        try {
            Instant checkedAt = Instant.now();
            long timeout = logic.getTokenTimeout(token);
            if (timeout != SaTokenDao.NEVER_EXPIRE && timeout <= 0) {
                throw new LoginException("登录会话未生效，请检查 Sa-Token 的有效期和存储配置");
            }
            LoginSession session = snapshot(token, checkedAt, timeout, user);
            SecurityContext.set(session);
            return LoginResult.of(session);
        } catch (RuntimeException | Error error) {
            try { logic.logoutByTokenValue(token); }
            catch (RuntimeException cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
            throw error;
        }
    }

    @Override
    public LoginSession resolveSession(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) return null;
        StpLogic logic = stpLogic();
        Instant checkedAt = Instant.now();
        Object loginId;
        long timeout;
        try {
            // 不能只读 Token -> ID 映射，否则会绕过踢人、顶替和活跃超时校验。
            loginId = logic.getLoginIdByToken(token);
            if (loginId == null) return null;
            timeout = logic.getTokenTimeout(token);
            if (timeout != SaTokenDao.NEVER_EXPIRE && timeout <= 0) return null;
        } catch (NotLoginException error) {
            return null;
        }
        IUserDetails user = getUserDetails(String.valueOf(loginId));
        if (user == null) {
            logic.logoutByTokenValue(token);
            return null;
        }
        // 用户加载耗时不能被算成新的有效期，也不能恢复一个加载期间已经过期的快照。
        LoginSession session = snapshot(token, checkedAt, timeout, user);
        if (session.expired()) return null;
        if (logic.isOpenCheckActiveTimeout() && SaStrategy.instance.autoRenew.apply(logic)) {
            logic.updateLastActiveToNow(token);
        }
        return session;
    }

    @Override
    public void logout() {
        try {
            String token = SecurityContext.getToken();
            if (token != null) stpLogic().logoutByTokenValue(token);
        } finally {
            SecurityContext.clear();
        }
    }

    private static LoginSession snapshot(String token, Instant checkedAt, long timeout, IUserDetails user) {
        Instant expiresAt = timeout == SaTokenDao.NEVER_EXPIRE ? null : checkedAt.plusSeconds(timeout);
        return new LoginSession(token, expiresAt, user);
    }
}
