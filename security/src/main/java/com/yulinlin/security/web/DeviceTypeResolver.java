package com.yulinlin.security.web;

import org.springframework.http.HttpHeaders;

@FunctionalInterface
public interface DeviceTypeResolver {

    String resolve(HttpHeaders headers);
}
