package com.yulinlin.monitor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Read-only API used by the built-in administration page. */
@RestController
@RequestMapping("/monitor/api")
public final class MonitorApiController {
    private final DatabaseMonitor monitor;
    private final MonitorProperties properties;

    public MonitorApiController(DatabaseMonitor monitor, MonitorProperties properties) {
        this.monitor = monitor;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) LoginRequest credentials,
                                                      HttpServletRequest request) {
        if (properties.getPassword().isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", 503, "message", "请先配置 yulinlin.monitor.password"));
        }
        if (credentials == null
                || !equalsConstantTime(properties.getUsername(), credentials.username())
                || !equalsConstantTime(properties.getPassword(), credentials.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 401, "message", "账号或密码错误"));
        }
        request.getSession(true).setAttribute(MonitorLoginInterceptor.SESSION_ATTRIBUTE, Boolean.TRUE);
        return ResponseEntity.ok(Map.of("code", 200, "message", "验证成功"));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.removeAttribute(MonitorLoginInterceptor.SESSION_ATTRIBUTE);
        return Map.of("code", 200, "message", "已退出");
    }

    @GetMapping("/snapshot")
    public DatabaseMonitorSnapshot snapshot(@RequestParam(defaultValue = "200") int limit) {
        return monitor.snapshot(limit);
    }

    private static boolean equalsConstantTime(String expected, String actual) {
        if (actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public record LoginRequest(String username, String password) {
    }
}
