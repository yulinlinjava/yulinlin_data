package com.yulinlin.admin.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Fill application-risk-local.yml, then run this test directly in IDEA. */
class AdminRiskDatabaseReplayTest {
    @Test
    @DisplayName("今日全部用户分析：加载历史，输出疑似机器人及命中依据")
    void replayDatabaseHistory() throws Exception {
        String path = System.getProperty("risk.replay.config");
        Resource resource = path == null ? new ClassPathResource("application-risk-local.yml") : new FileSystemResource(path);
        if (path != null && !resource.exists()) throw new IllegalArgumentException("本地配置文件不存在：" + path);
        assumeTrue(resource.exists(), "请将 application-risk-local.example.yml 复制为 application-risk-local.yml");
        var config = LocalRiskHistory.load(resource);
        assumeTrue(config.datasource().getUrl() != null && !config.datasource().getUrl().isBlank(),
                "请填写 application-risk-local.yml 的 spring.datasource.url");
        var history = LocalRiskHistory.read(config, Clock.systemUTC());
        var report = LocalRiskAnalysis.analyze(history, config.risk());
        var threshold = config.history().getReportRequestsGreaterThan();
        var files = LocalRiskAnalysis.write(report, Path.of(config.history().getReportDirectory()), threshold);
        LocalRiskAnalysis.print(report, files, threshold);
    }
}
