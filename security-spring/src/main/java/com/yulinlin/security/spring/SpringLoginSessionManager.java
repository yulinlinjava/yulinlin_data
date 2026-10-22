package com.yulinlin.security.spring;

import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.session.*;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

/** 适配原生注册表。Spring Session 可替换注册表；不复制会话，也不扫描 Redis 私有键。 */
public final class SpringLoginSessionManager implements LoginSessionManager {
    private final SessionRegistry registry;
    private final SpringSessionPrincipalResolver principals;
    private final SessionManagementProperties properties;
    private final boolean globalQuery;

    public SpringLoginSessionManager(SessionRegistry registry, SpringSessionPrincipalResolver principals,
                                     SessionManagementProperties properties) {
        this.registry = Objects.requireNonNull(registry);
        this.principals = Objects.requireNonNull(principals);
        this.properties = Objects.requireNonNull(properties);
        boolean global;
        try { registry.getAllPrincipals(); global = true; }
        catch (UnsupportedOperationException unsupported) { global = false; }
        this.globalQuery = global;
    }

    @Override public Set<SessionCapability> capabilities() {
        return globalQuery ? Set.of(SessionCapability.GLOBAL_QUERY) : Set.of();
    }

    @Override public SessionPage listSessions(SessionQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.deviceType() != null) throw new UnsupportedOperationException("原生 Spring SessionRegistry 不提供设备类型索引，请自定义 LoginSessionManager");
        int size = properties.pageSize(query);
        int offset = SessionSupport.cursor("spring", query, 1)[0];
        List<LoginSessionView> views = sessions(query.userId()).stream().map(this::view)
                .sorted(Comparator.comparing(LoginSessionView::sessionId)).toList();
        int from = Math.min(offset, views.size());
        int end = (int) Math.min((long) from + size, views.size());
        boolean more = end < views.size();
        return new SessionPage(views.subList(from, end), more ? SessionSupport.cursorPrefix("spring", query) + end : null,
                more, (long) views.size());
    }

    @Override public Optional<LoginSessionView> getSession(String userId, String sessionId) {
        SessionSupport.requireText(userId, "userId");
        SessionSupport.requireText(sessionId, "sessionId");
        return sessions(userId).stream().filter(info -> id(info).equals(sessionId)).map(this::view).findFirst();
    }

    @Override public long countSessions(String userId) {
        return sessions(SessionSupport.requireText(userId, "userId")).size();
    }

    @Override public void kickoutSession(String userId, String sessionId, String reason) {
        SessionSupport.requireText(userId, "userId");
        SessionSupport.requireText(sessionId, "sessionId");
        for (SessionInformation info : sessions(userId)) if (id(info).equals(sessionId)) expire(info, userId);
    }

    @Override public void kickoutAll(String userId, String reason) {
        SessionSupport.requireText(userId, "userId");
        for (SessionInformation info : sessions(userId)) expire(info, userId);
    }

    private void expire(SessionInformation info, String userId) {
        SessionInformation fresh = registry.getSessionInformation(info.getSessionId());
        if (fresh != null && !fresh.isExpired() && userId.equals(principals.userId(fresh.getPrincipal()))) {
            // 必须保留失效标记；直接 remove 会导致 ConcurrentSessionFilter 无法识别被踢会话。
            fresh.expireNow();
        }
    }

    private List<SessionInformation> sessions(String userId) {
        if (userId == null && !globalQuery) {
            throw new UnsupportedOperationException("当前 Spring 会话注册表不支持全局查询，请提供自定义 LoginSessionManager");
        }
        List<Object> candidates = globalQuery ? registry.getAllPrincipals()
                : List.of(Objects.requireNonNull(principals.principal(userId), "无法解析用户的原生 principal"));
        Map<String, SessionInformation> results = new LinkedHashMap<>();
        for (Object principal : candidates) {
            if (globalQuery && userId != null && !userId.equals(principals.userId(principal))) continue;
            for (SessionInformation info : registry.getAllSessions(principal, false)) {
                SessionInformation fresh = registry.getSessionInformation(info.getSessionId());
                if (fresh == null || fresh.isExpired()) continue;
                String owner = principals.userId(fresh.getPrincipal());
                if (userId != null && !userId.equals(owner)) {
                    throw new IllegalStateException("会话 principal 与 userId 映射不一致，请检查 SpringSessionPrincipalResolver");
                }
                results.put(fresh.getSessionId(), fresh);
            }
        }
        return new ArrayList<>(results.values());
    }

    private static String id(SessionInformation info) { return SessionSupport.id("spring", info.getSessionId()); }

    private LoginSessionView view(SessionInformation info) {
        Object principal = info.getPrincipal();
        String username = principal instanceof IUserDetails user ? user.getUsername()
                : principal instanceof UserDetails user ? user.getUsername() : null;
        Boolean current = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var session = attributes.getRequest().getSession(false);
            current = session != null && info.getSessionId().equals(session.getId());
        }
        return new LoginSessionView(id(info), principals.userId(principal), username, null, null, null,
                info.getLastRequest() == null ? null : info.getLastRequest().toInstant(), null, current);
    }
}
