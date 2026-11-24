package com.yulinlin.mcp;

import com.yulinlin.mcp.transport.McpHttpGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class McpOriginDiagnosticsTest extends McpTestSupport {
    @Test void logsComparisonValuesWithoutCredentials(CapturedOutput output) {
        var request=new MockHttpServletRequest();request.setServerName("internal");request.setServerPort(8080);request.setScheme("http");
        request.addHeader("Origin","https://public.example");request.addHeader("Authorization","Bearer do-not-log-me");
        var guard=new McpHttpGuard(properties());assertThrows(McpException.class,()->guard.check(request));
        assertTrue(output.toString().contains("Origin=https://public.example, serverName=internal, serverPort=8080, scheme=http"));
        assertFalse(output.toString().contains("do-not-log-me"));
        request.removeHeader("Origin");request.addHeader("Origin","https://secret-user:secret-password@public.example/path-secret?token=secret-query");
        assertThrows(McpException.class,()->guard.check(request));
        assertFalse(output.toString().contains("secret-password"));assertFalse(output.toString().contains("path-secret"));assertFalse(output.toString().contains("secret-query"));
    }
}
