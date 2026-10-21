package com.yulinlin.security.jwt;

import com.yulinlin.security.auth.AbstractUserSecurityManager;
import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginResult;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.UserStore;

import java.time.Instant;
import java.util.Objects;

/**
 * 纯无状态 JWT：Token 格式和验签委托 JwtTokenCodec，通过 userId 加载当前用户。
 * 不保存登录记录、不查询撤销列表；密钥及校验配置不变时，重启不影响未过期 Token。
 */
public final class JwtSecurityManager extends AbstractUserSecurityManager {
    private final JwtTokenCodec tokens;

    public JwtSecurityManager(UserStore users, JwtProperties properties, LoginAuthenticatorRegistry authenticators) {
        this(users, new DefaultJwtTokenCodec(properties), properties, authenticators);
    }

    public JwtSecurityManager(UserStore users, JwtTokenCodec tokens, JwtProperties properties,
                              LoginAuthenticatorRegistry authenticators) {
        super(users, authenticators);
        properties.validate();
        this.tokens = Objects.requireNonNull(tokens, "JwtTokenCodec 不能为空");
    }

    @Override
    protected LoginResult createLogin(IUserDetails user, String deviceType, String authType) {
        String userId = user.getUserId();
        String token = tokens.generate(user, deviceType, authType);
        TokenInfo info = verifiedInfo(token);
        // 回读生成结果，不能允许自定义生成器改写本次登录的用户或设备。
        if (info == null || !userId.equals(info.userId()) || !deviceType.equals(info.deviceType())
                || !authType.equals(info.authType())
                || !userId.equals(user.getUserId())) {
            throw new IllegalStateException("JwtTokenCodec 生成与解析结果不一致，请保留本次登录的 userId、deviceType 和 authType");
        }
        LoginSession session = new LoginSession(token, info.expiresAt(), user, info.authType());
        SecurityContext.set(session);
        return LoginResult.of(session);
    }

    @Override
    public LoginSession resolveSession(String token) {
        TokenInfo info = verifiedInfo(token);
        if (info == null) return null;
        IUserDetails user = getUserDetails(info.userId());
        // 用户加载期间 Token 也可能到期；不能把加载耗时变成额外有效期。
        return user == null || !info.validAt(Instant.now())
                ? null : new LoginSession(token, info.expiresAt(), user, info.authType());
    }

    /** 仅清理当前请求身份；客户端应删除 Token，已签发 Token 本身仍有效至到期。 */
    @Override
    public void logout() {
        SecurityContext.clear();
    }

    private TokenInfo verifiedInfo(String token) {
        if (token == null || token.isBlank() || token.length() > 8192
                || token.chars().anyMatch(Character::isWhitespace)) return null;
        try {
            TokenInfo info = tokens.parse(token);
            return info != null && info.validAt(Instant.now()) ? info : null;
        } catch (LoginException error) {
            // 只把 Token 验证失败视为匿名，不吞掉密钥服务等基础设施异常。
            return null;
        }
    }

}
