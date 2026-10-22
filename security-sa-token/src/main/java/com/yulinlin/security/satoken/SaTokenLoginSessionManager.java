package com.yulinlin.security.satoken;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpLogic;
import com.yulinlin.security.session.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** 直接访问 Sa-Token Account-Session/Terminal，官方 Redis DAO 可无缝替换内存 DAO。 */
public final class SaTokenLoginSessionManager implements LoginSessionManager {
    private final Supplier<StpLogic> logic;
    private final SessionManagementProperties properties;

    public SaTokenLoginSessionManager(Supplier<StpLogic> logic, SessionManagementProperties properties) {
        this.logic = Objects.requireNonNull(logic);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override public Set<SessionCapability> capabilities() {
        return Set.of(SessionCapability.GLOBAL_QUERY, SessionCapability.DEVICE_TYPE);
    }

    @Override public SessionPage listSessions(SessionQuery query) {
        Objects.requireNonNull(query, "query");
        int size = properties.pageSize(query);
        String backend = "sa:" + logic.get().getLoginType();
        int[] position = SessionSupport.cursor(backend, query, 2);
        int account = position[0], terminal = position[1];
        List<LoginSessionView> items = new ArrayList<>();
        StpLogic stp = logic.get();
        // 每次只获取一个账号；过多空记录时返回可继续的游标，避免一次请求扫描全部存储。
        int scanned = 0;
        while (scanned++ < Math.max(100L, (long) size * 10)) {
            SaSession session;
            if (query.userId() != null) {
                if (account != 0) return new SessionPage(items, null, false, null);
                session = stp.getSessionByLoginId(query.userId(), false);
            } else {
                List<String> keys = stp.searchSessionId("", account, 1, true);
                if (keys.isEmpty()) return new SessionPage(items, null, false, null);
                session = stp.getSessionBySessionId(keys.getFirst());
            }
            List<SaTerminalInfo> terminals = session == null ? List.of() : new ArrayList<>(session.getTerminalList());
            String userId = session == null ? null : String.valueOf(session.getLoginId());
            while (terminal < terminals.size()) {
                SaTerminalInfo nativeInfo = terminals.get(terminal);
                LoginSessionView view = view(stp, userId, nativeInfo);
                if (view != null && (query.deviceType() == null || query.deviceType().equals(view.deviceType()))) {
                    if (items.size() == size) {
                        return page(items, backend, query, account, terminal);
                    }
                    items.add(view);
                }
                terminal++;
            }
            if (query.userId() != null) return new SessionPage(items, null, false, null);
            if (account == Integer.MAX_VALUE) throw new IllegalStateException("原生会话查询索引超过支持范围");
            account++;
            terminal = 0;
        }
        return page(items, backend, query, account, terminal);
    }

    private SessionPage page(List<LoginSessionView> items, String backend, SessionQuery query, int account, int terminal) {
        return new SessionPage(items, SessionSupport.cursorPrefix(backend, query) + account + ":" + terminal, true, null);
    }

    @Override public Optional<LoginSessionView> getSession(String userId, String sessionId) {
        SessionSupport.requireText(sessionId, "sessionId");
        StpLogic stp = logic.get();
        return terminals(stp, userId).stream().map(t -> view(stp, userId, t))
                .filter(Objects::nonNull).filter(v -> sessionId.equals(v.sessionId())).findFirst();
    }

    @Override public long countSessions(String userId) {
        StpLogic stp = logic.get();
        return terminals(stp, userId).stream().filter(t -> view(stp, userId, t) != null)
                .map(SaTerminalInfo::getTokenValue).distinct().count();
    }

    @Override public void kickoutSession(String userId, String sessionId, String reason) {
        SessionSupport.requireText(sessionId, "sessionId");
        StpLogic stp = logic.get();
        for (SaTerminalInfo terminal : terminals(stp, userId)) {
            LoginSessionView view = view(stp, userId, terminal);
            if (view != null && sessionId.equals(view.sessionId())) stp.kickoutByTokenValue(terminal.getTokenValue());
        }
    }

    @Override public void kickoutAll(String userId, String reason) {
        logic.get().kickout(SessionSupport.requireText(userId, "userId"));
    }

    @Override public void kickoutByDeviceType(String userId, String deviceType, String reason) {
        SessionSupport.requireText(userId, "userId");
        SessionSupport.requireText(deviceType, "deviceType");
        logic.get().kickout(userId, deviceType);
    }

    private List<SaTerminalInfo> terminals(StpLogic stp, String userId) {
        SessionSupport.requireText(userId, "userId");
        SaSession session = stp.getSessionByLoginId(userId, false);
        return session == null ? List.of() : new ArrayList<>(session.getTerminalList());
    }

    private LoginSessionView view(StpLogic stp, String userId, SaTerminalInfo terminal) {
        String token = terminal.getTokenValue();
        Object loginId = stp.getLoginIdByToken(token); // 包括踢人、顶替、活跃超时校验，不续期。
        if (loginId == null || !userId.equals(String.valueOf(loginId))) return null;
        Instant now = Instant.now();
        long ttl = stp.getTokenTimeout(token);
        if (ttl != SaTokenDao.NEVER_EXPIRE && ttl <= 0) return null;
        long active = stp.getTokenActiveTimeoutByToken(token);
        if (active != SaTokenDao.NEVER_EXPIRE && active <= 0) return null;
        long remaining = ttl < 0 ? active : active < 0 ? ttl : Math.min(ttl, active);
        long lastAccess = stp.getTokenLastActiveTime(token);
        Boolean current = SaHolder.getContext().isValid() ? token.equals(stp.getTokenValue()) : null;
        return new LoginSessionView(SessionSupport.id("sa-" + stp.getLoginType(), token), userId, null,
                terminal.getDeviceType(), terminal.getDeviceId(),
                terminal.getCreateTime() > 0 ? Instant.ofEpochMilli(terminal.getCreateTime()) : null,
                lastAccess > 0 ? Instant.ofEpochMilli(lastAccess) : null,
                remaining < 0 ? null : now.plusSeconds(remaining), current);
    }
}
