package com.yulinlin.security.spring;

import jakarta.servlet.http.HttpServletRequest;
import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/** 传给 AuthenticationManager 的登录详情；不保存账号密码，设备类型只是业务元数据。 */
public final class SpringLoginDetails extends WebAuthenticationDetails {
    private static final long serialVersionUID = 1L;
    private final String deviceType;
    private final String authType;
    private final Object providerDetails;

    public SpringLoginDetails(HttpServletRequest request, String deviceType, String authType) {
        this(request, deviceType, authType, null);
    }

    public SpringLoginDetails(HttpServletRequest request, String deviceType, String authType, Object providerDetails) {
        super(request);
        if (deviceType == null || deviceType.isBlank() || !deviceType.equals(deviceType.strip())) {
            throw new IllegalArgumentException("设备类型不能为空或包含首尾空白");
        }
        this.deviceType = deviceType;
        this.authType = LoginAuthenticatorRegistry.requireName(authType);
        this.providerDetails = providerDetails;
    }

    public String getDeviceType() { return deviceType; }
    public String getAuthType() { return authType; }
    /** 认证提供者自定义详情；业务必须确保不包含原始凭证。 */
    public Object getProviderDetails() { return providerDetails; }
}
