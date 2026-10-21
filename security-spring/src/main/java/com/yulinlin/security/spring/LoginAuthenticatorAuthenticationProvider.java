package com.yulinlin.security.spring;

import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.LoginException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.LinkedHashSet;
import java.util.Objects;

/** 由业务加入其 AuthenticationManager；使统一认证器在 Spring 认证链内执行，不绕过该链。 */
public final class LoginAuthenticatorAuthenticationProvider implements AuthenticationProvider {
    private final LoginAuthenticatorRegistry authenticators;

    public LoginAuthenticatorAuthenticationProvider(LoginAuthenticatorRegistry authenticators) {
        this.authenticators = Objects.requireNonNull(authenticators, "认证器注册表不能为空");
    }

    @Override public boolean supports(Class<?> type) {
        return LoginRequestAuthenticationToken.class.isAssignableFrom(type);
    }

    @Override public Authentication authenticate(Authentication authentication) {
        if (!(authentication instanceof LoginRequestAuthenticationToken input)) return null;
        if (input.getLoginRequest() == null) throw new BadCredentialsException("登录凭证已清除");
        try {
            var registered = authenticators.required(input.getLoginRequest());
            if (!registered.name().equals(input.getAuthType())) throw new BadCredentialsException("认证类型与注册的认证器不一致");
            var user = registered.authenticate(input.getLoginRequest());
            var authorities = new LinkedHashSet<SimpleGrantedAuthority>();
            if (user.getRoles() != null) user.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            if (user.getPermissions() != null) user.getPermissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
            // 使用不含凭证的 principal，保留 ID；默认 mapper 的权限以 authorities 为准。
            var result = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
            result.setDetails(input.getDetails());
            return result;
        } catch (LoginException error) {
            throw new BadCredentialsException("登录认证未通过", error);
        }
    }
}
