package com.yulinlin.security.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/** 传给 AuthenticationManager 的登录详情；不保存账号密码，设备类型只是业务元数据。 */
public final class SpringLoginDetails extends WebAuthenticationDetails {
    private static final long serialVersionUID = 1L;
    private final String deviceType;

    public SpringLoginDetails(HttpServletRequest request, String deviceType) {
        super(request);
        if (deviceType == null || deviceType.isBlank()) throw new IllegalArgumentException("设备类型不能为空");
        this.deviceType = deviceType;
    }

    public String getDeviceType() { return deviceType; }
}
