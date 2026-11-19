package com.yulinlin.security.limit;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** IP blacklist rejection; applications own the response body through exception handling. */
public final class IpBlockedException extends ResponseStatusException {
    private final String ip;

    public IpBlockedException(String ip) {
        super(HttpStatus.FORBIDDEN, "当前 IP 已被禁止访问");
        this.ip = ip;
    }

    public String getIp() { return ip; }
}
