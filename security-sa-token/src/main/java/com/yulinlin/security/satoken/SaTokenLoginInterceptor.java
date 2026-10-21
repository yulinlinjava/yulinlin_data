package com.yulinlin.security.satoken;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.web.LoginContextInterceptor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.function.Supplier;

/** 优先使用 Sa-Token 原生 Token 读取规则；未提供原生凭证时兼容既有 Bearer 调用。 */
public final class SaTokenLoginInterceptor extends LoginContextInterceptor {
    private final Supplier<ISecurityManager> managers;

    public SaTokenLoginInterceptor(Supplier<ISecurityManager> managers) {
        super(managers);
        this.managers = managers;
    }

    @Override
    protected String resolveToken(HttpServletRequest request) {
        if (managers.get() instanceof SaTokenSecurityManager manager) {
            StpLogic logic = manager.stpLogic();
            // 原生凭证存在但格式错误时直接拒绝，不能降级尝试另一份 Bearer 身份。
            String raw = logic.getTokenValueNotCut();
            if (raw != null && !raw.isEmpty()) {
                try {
                    return logic.getTokenValue(true);
                } catch (NotLoginException error) {
                    throw new LoginException("登录凭证格式错误，请检查 Sa-Token 的 Token 前缀配置");
                }
            }
        }
        return super.resolveToken(request);
    }

    @Override
    protected void onSessionResolved(LoginSession session) {
        if (managers.get() instanceof SaTokenSecurityManager manager) {
            // 此处凭证已通过校验。仅同步请求存储，使 Bearer 请求也能使用 StpUtil.logout()。
            manager.stpLogic().setTokenValueToStorage(session.token());
        }
    }
}
