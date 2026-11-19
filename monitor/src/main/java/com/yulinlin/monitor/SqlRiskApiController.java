package com.yulinlin.monitor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public final class SqlRiskApiController {
    private final SqlRiskMonitor monitor;

    public SqlRiskApiController(SqlRiskMonitor monitor) { this.monitor = monitor; }

    @GetMapping("/monitor/api/sql-risks")
    public List<SqlRiskRecord> recent(@RequestParam(defaultValue = "200") int limit) {
        return monitor.recent(limit);
    }
}
