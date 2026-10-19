package com.yulinlin.admin.rbac.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yulinlin.admin.rbac.config.RbacProperties;
import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.repository.UserRepository;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.LoginException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** 单机登录示例：Token 只表示服务端会话，不把权限信息交给客户端决定。 */
@Service
@Profile("rbac")
public class AdminSecurityManager implements ISecurityManager {
    private static final String REQUEST_TOKEN = AdminSecurityManager.class.getName() + ".token";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final UserRepository users;
    private final PasswordHash passwords;
    private final Duration tokenTtl;
    private final Cache<String, Session> sessions;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;
    private final Object sessionLock = new Object();
    private final AtomicLong authorizationVersion = new AtomicLong();

    public AdminSecurityManager(UserRepository users, PasswordHash passwords, RbacProperties properties) {
        this.users = users;
        this.passwords = passwords;
        this.tokenTtl = properties.getTokenTtl();
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()
                || properties.getMaximumSessions() < 1) {
            throw new IllegalArgumentException("会话有效期和最大会话数必须大于 0");
        }
        sessions = Caffeine.newBuilder().maximumSize(properties.getMaximumSessions())
                .expireAfterWrite(tokenTtl).build();
        dummyHash = passwords.encode(randomToken());
    }

    @Override
    public AdminUserDetails login(String username, String password, String code) {
        return signIn(username, password, code).user();
    }

    public LoginResponse signIn(String username, String password, String code) {
        // 本案例未启用验证码；传入 code 时明确拒绝，避免让调用者误以为已经校验。
        if (code != null && !code.isBlank()) throw new LoginException("本案例未配置验证码校验");
        UserEntity user = username == null || username.isBlank() ? null : users.findByUsernameEq(username);
        boolean valid = passwords.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !valid) throw new LoginException("用户名或密码错误");
        return createSession(user);
    }

    private LoginResponse createSession(UserEntity verified) {
        for (int attempt = 0; attempt < 3; attempt++) {
            long version = authorizationVersion.get();
            // 不持有会话锁查库，避免连接池耗尽时和提交后的会话回调互相等待。
            UserEntity current = users.findByIdEq(verified.getId());
            if (current == null || !Objects.equals(current.getPasswordHash(), verified.getPasswordHash())) {
                throw new LoginException("账号信息已变化，请重新登录");
            }
            if (current.getStatus() != UserStatus.ENABLED) throw new LoginException("账号已停用或锁定");
            AdminUserDetails details = AdminUserDetails.from(current);
            synchronized (sessionLock) {
                // 查库期间发生过授权变更时重读，防止在撤销操作之后放入旧会话。
                if (version != authorizationVersion.get()) continue;
                Instant expiresAt = Instant.now().plus(tokenTtl);
                String token = randomToken();
                sessions.put(token, new Session(details, expiresAt));
                HttpServletRequest request = currentRequest();
                if (request != null) request.setAttribute(REQUEST_TOKEN, token);
                return new LoginResponse(token, "Bearer", expiresAt, details);
            }
        }
        throw new LoginException("账号权限正在调整，请重试登录");
    }

    @Override
    public void logout() {
        String token = currentToken();
        if (token != null) sessions.invalidate(token);
    }

    @Override
    public AdminUserDetails getUserDetails() {
        String token = currentToken();
        if (token == null) return null;
        Session session = sessions.getIfPresent(token);
        if (session == null) return null;
        if (!session.expiresAt().isAfter(Instant.now())) {
            sessions.invalidate(token);
            return null;
        }
        return session.user();
    }

    @Override
    public AdminUserDetails getUserDetails(String userId) {
        if (userId == null || userId.isBlank()) return null;
        UserEntity user = users.findSnapshotById(userId);
        return user == null ? null : AdminUserDetails.from(user);
    }

    @Override
    public AdminUserDetails refresh(String userId) {
        long version;
        synchronized (sessionLock) {
            version = authorizationVersion.incrementAndGet();
        }
        AdminUserDetails details;
        try {
            details = getUserDetails(userId);
        } catch (RuntimeException error) {
            revokeUser(userId);
            throw error;
        }
        if (details == null || details.getStatus() != UserStatus.ENABLED) {
            revokeUser(userId);
            return details;
        }
        synchronized (sessionLock) {
            if (version != authorizationVersion.get()) {
                // 并发发生其他授权写入时，不使用可能过时的快照恢复旧权限。
                revokeUser(userId);
                return details;
            }
            // 保留 expiresAt；刷新用户资料不会延长 Token 的固定有效期。
            sessions.asMap().forEach((token, session) -> {
                if (session.user().getUserId().equals(userId)) {
                    sessions.asMap().replace(token, session, new Session(details, session.expiresAt()));
                }
            });
        }
        return details;
    }

    public void revokeUser(String userId) {
        synchronized (sessionLock) {
            authorizationVersion.incrementAndGet();
            sessions.asMap().entrySet().removeIf(entry -> entry.getValue().user().getUserId().equals(userId));
        }
    }

    public void refreshRole(String roleCode) {
        Set<String> affectedUsers = new LinkedHashSet<>();
        synchronized (sessionLock) {
            authorizationVersion.incrementAndGet();
            sessions.asMap().values().forEach(session -> {
                if (session.user().hasRole(roleCode)) affectedUsers.add(session.user().getUserId());
            });
        }
        RuntimeException failure = null;
        for (String userId : affectedUsers) {
            try {
                refresh(userId);
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    private String currentToken() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        Object authenticated = request.getAttribute(REQUEST_TOKEN);
        if (authenticated instanceof String token) return token;
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = authorization.substring(7).trim();
        return TOKEN_PATTERN.matcher(token).matches() ? token : null;
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Session(AdminUserDetails user, Instant expiresAt) { }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, AdminUserDetails user) { }
}
