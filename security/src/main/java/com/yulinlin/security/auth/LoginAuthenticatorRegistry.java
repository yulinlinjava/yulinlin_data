package com.yulinlin.security.auth;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 启动时固定注册信息并检查冲突；按精确参数类 O(1) 查找，不使用匹配顺序或客户端类型名。 */
public final class LoginAuthenticatorRegistry {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    private final Map<Class<?>, Registration<?>> registrations;

    public LoginAuthenticatorRegistry(Collection<? extends LoginAuthenticator<?>> authenticators, UserStore users) {
        Map<Class<?>, Registration<?>> types = new HashMap<>();
        Map<String, Class<?>> names = new HashMap<>();
        for (LoginAuthenticator<?> authenticator : authenticators) add(types, names, authenticator);
        // ID 登录不需要额外业务类；提供自定义同类型认证器时使用业务实现。
        if (users != null && !types.containsKey(UserIdLogin.class)) {
            add(types, names, new UserIdLoginAuthenticator(users));
        }
        registrations = Map.copyOf(types);
    }

    private static <T extends LoginRequest> void add(Map<Class<?>, Registration<?>> types,
            Map<String, Class<?>> names, LoginAuthenticator<T> authenticator) {
        Objects.requireNonNull(authenticator, "登录认证器不能为空");
        String name = requireName(authenticator.name());
        Class<T> type = Objects.requireNonNull(authenticator.requestType(), "登录参数类型不能为空");
        if (!LoginRequest.class.isAssignableFrom(type) || type.isInterface()
                || java.lang.reflect.Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalStateException("登录认证器必须声明具体 LoginRequest 类型：" + type.getName());
        }
        if (types.containsKey(type)) throw new IllegalStateException("登录参数类型重复注册：" + type.getName());
        if (names.putIfAbsent(name, type) != null) throw new IllegalStateException("登录认证器 name 重复：" + name);
        types.put(type, new Registration<>(name, type, authenticator));
    }

    public Registration<?> required(LoginRequest request) {
        if (request == null) throw new IllegalArgumentException("登录参数不能为空");
        String device = request.deviceType();
        if (device == null || device.isBlank() || !device.equals(device.strip())) {
            throw new IllegalArgumentException("设备类型不能为空或包含首尾空白");
        }
        if (request instanceof PasswordLogin password && (password.username() == null || password.username().isBlank()
                || password.password() == null || password.password().isEmpty())) {
            throw new LoginException("用户名或密码错误");
        }
        if (request instanceof ApiKeyLogin key && (key.apiKey() == null || key.apiKey().isBlank()
                || key.apiKey().length() > 8192)) throw new LoginException("密钥无效或已失效");
        if (request instanceof UserIdLogin id && (id.userId() == null || id.userId().isBlank()
                || !id.userId().equals(id.userId().strip()))) throw new LoginException("用户 ID 无效");
        Registration<?> registration = registrations.get(request.getClass());
        if (registration == null) {
            throw new IllegalStateException("未注册该登录参数的 LoginAuthenticator：" + request.getClass().getName());
        }
        return registration;
    }

    public static String requireName(String name) {
        if (!validName(name)) throw new IllegalArgumentException("认证名称必须为 1～64 位字母、数字、点、下划线、冒号或短横线，且以字母或数字开头");
        return name;
    }

    public static boolean validName(String name) { return name != null && NAME.matcher(name).matches(); }

    public static final class Registration<T extends LoginRequest> {
        private final String name;
        private final Class<T> type;
        private final LoginAuthenticator<T> authenticator;

        private Registration(String name, Class<T> type, LoginAuthenticator<T> authenticator) {
            this.name = name;
            this.type = type;
            this.authenticator = authenticator;
        }

        public String name() { return name; }

        public IUserDetails authenticate(LoginRequest request) {
            IUserDetails user = authenticator.authenticate(type.cast(request));
            if (user == null) throw new LoginException("认证未通过或账号不可用");
            String id = user.getUserId();
            if (id == null || id.isBlank() || !id.equals(id.strip())) {
                throw new IllegalStateException("认证器必须返回具有有效用户 ID 的安全用户");
            }
            return user;
        }
    }
}
