package com.yulinlin.security.satoken;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import com.yulinlin.security.SecurityAuthProperties;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.LoginException;
import com.yulinlin.security.auth.LoginSession;
import com.yulinlin.security.web.LoginContextInterceptor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.function.Supplier;

/** 优先使用 Sa-Token 原生读取规则；未提供原生凭证时按公共 auth 配置读取备用请求头。 */
public final class SaTokenLoginInterceptor extends LoginContextInterceptor {
    private final Supplier<ISecurityManager> managers;

    /** 与默认拦截器共用容器绑定的配置，原生凭证读取规则仍优先。 */
    public SaTokenLoginInterceptor(Supplier<ISecurityManager> managers, SecurityAuthProperties properties) {
        super(managers, properties);
        this.managers = managers;
    }

    @Override
    protected String resolveToken(HttpServletRequest request) {
        if (managers.get() instanceof SaTokenSecurityManager manager) {
            StpLogic logic = manager.stpLogic();
            // 原生凭证存在但格式错误时直接拒绝，不能降级尝试备用请求头中的另一份身份。
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
            // 此处凭证已通过校验。仅同步请求存储，使备用请求头也能使用 StpUtil.logout()。
            manager.stpLogic().setTokenValueToStorage(session.token());
        }
    }
}
