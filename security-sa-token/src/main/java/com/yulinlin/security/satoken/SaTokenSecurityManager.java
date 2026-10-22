package com.yulinlin.security.satoken;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.strategy.SaStrategy;
import com.yulinlin.security.auth.AbstractUserSecurityManager;
import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginResult;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityContextResolver;
import com.yulinlin.security.auth.UserStore;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 会话、设备隔离、踢人和存储均委托 Sa-Token，配置沿用 sa-token.*。
 * 原生 StpUtil.login/logout 与统一入口共用身份；用户资料仅在请求内缓存，不另建登录记录。
 */
public final class SaTokenSecurityManager extends AbstractUserSecurityManager implements SecurityContextResolver {
    @Override public boolean sessionManagementEnabledByDefault() { return true; }

    /** 原生可信登录接入时，需在对应 Token-Session 中保存已验证认证方式；不能使用客户端声明。 */
    public static final String AUTH_TYPE_KEY = "yulinlin.authType";
    private final Supplier<StpLogic> logicSupplier;

    public SaTokenSecurityManager(UserStore users, Supplier<StpLogic> logicSupplier,
                                   LoginAuthenticatorRegistry authenticators) {
        super(users, authenticators);
        this.logicSupplier = Objects.requireNonNull(logicSupplier, "StpLogic 提供者不能为空");
    }

    /** 可访问底层能力；默认跟随 StpUtil 配置，避免在 Spring 注入完成前捕获旧 StpLogic。 */
    public StpLogic stpLogic() {
        return Objects.requireNonNull(logicSupplier.get(), "StpLogic 不能为空");
    }

    @Override
    protected LoginResult createLogin(IUserDetails user, String deviceType, String authType) {
        String userId = user.getUserId();
        StpLogic logic = stpLogic();
        String token = logic.createLoginSession(userId,
                logic.createSaLoginParameter().setDeviceType(deviceType).setIsShare(false));
        try {
            // 每次登录独立 Token，避免同用户同设备的不同认证方式覆盖共享 Token 的来源。
            logic.getTokenSessionByToken(token).set(AUTH_TYPE_KEY, authType);
            Instant checkedAt = Instant.now();
            long timeout = logic.getTokenTimeout(token);
            if (timeout != SaTokenDao.NEVER_EXPIRE && timeout <= 0) {
                throw new LoginException("登录会话未生效，请检查 Sa-Token 的有效期和存储配置");
            }
            LoginSession session = snapshot(token, checkedAt, timeout, user, authType);
            // 统一 API 登录后，原生 StpUtil 也应立即看到同一 Token；不额外写 Cookie/响应头。
            if (SaHolder.getContext().isValid()) logic.setTokenValueToStorage(token);
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
        return resolveSession(token, null);
    }

    @Override
    public LoginSession resolveContext(LoginSession cached) {
        if (!SaHolder.getContext().isValid()) {
            // 非 HTTP 场景仍可通过账号密码登录或设置可信快照，不跨线程恢复身份。
            return cached == null ? null : resolveSession(cached.token(), cached);
        }
        try {
            // 即使已有快照也检查原生 Token：注销、重新登录、踢人后不能复用旧身份。
            return resolveSession(stpLogic().getTokenValue(true), cached);
        } catch (NotLoginException error) {
            return null;
        }
    }

    private LoginSession resolveSession(String token, LoginSession cached) {
        if (token == null || token.isBlank() || token.length() > 8192) return null;
        StpLogic logic = stpLogic();
        Instant checkedAt = Instant.now();
        Object loginId;
        long timeout;
        String authType;
        try {
            // 不能只读 Token -> ID 映射，否则会绕过踢人、顶替和活跃超时校验。
            loginId = logic.getLoginIdByToken(token);
            if (loginId == null) return null;
            timeout = logic.getTokenTimeout(token);
            if (timeout != SaTokenDao.NEVER_EXPIRE && timeout <= 0) return null;
            var tokenSession = logic.getTokenSessionByToken(token, false);
            Object storedType = tokenSession == null ? null : tokenSession.get(AUTH_TYPE_KEY);
            if (!(storedType instanceof String name) || !LoginAuthenticatorRegistry.validName(name)) return null;
            authType = name;
        } catch (NotLoginException error) {
            return null;
        }
        if (cached != null && !cached.expired() && token.equals(cached.token())
                && authType.equals(cached.authType())
                && String.valueOf(loginId).equals(cached.user().getUserId())) {
            return cached;
        }
        IUserDetails user = getUserDetails(String.valueOf(loginId));
        if (user == null) {
            logic.logoutByTokenValue(token);
            return null;
        }
        // 用户加载耗时不能被算成新的有效期，也不能恢复一个加载期间已经过期的快照。
        LoginSession session = snapshot(token, checkedAt, timeout, user, authType);
        if (session.expired()) return null;
        if (logic.isOpenCheckActiveTimeout() && SaStrategy.instance.autoRenew.apply(logic)) {
            logic.updateLastActiveToNow(token);
        }
        return session;
    }

    @Override
    public void logout() {
        try {
            if (SaHolder.getContext().isValid()) {
                stpLogic().logout();
            } else {
                String token = SecurityContext.getToken();
                if (token != null) stpLogic().logoutByTokenValue(token);
            }
        } finally {
            SecurityContext.clear();
        }
    }

    private static LoginSession snapshot(String token, Instant checkedAt, long timeout, IUserDetails user, String authType) {
        Instant expiresAt = timeout == SaTokenDao.NEVER_EXPIRE ? null : checkedAt.plusSeconds(timeout);
        return new LoginSession(token, expiresAt, user, authType);
    }
}
