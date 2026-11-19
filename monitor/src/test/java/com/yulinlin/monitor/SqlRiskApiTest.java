package com.yulinlin.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SqlRiskApiTest {
    @Test void riskRecordsRequireMonitorLoginAndExposeNoParameterField() throws Exception {
        var monitor = new SqlRiskMonitor(new MonitorProperties());
        monitor.record("mysql", "SELECT SLEEP(?)", true, false, List.of("deny function"));
        var mvc = MockMvcBuilders.standaloneSetup(new SqlRiskApiController(monitor))
                .addInterceptors(new MonitorLoginInterceptor()).build();
        mvc.perform(get("/monitor/api/sql-risks")).andExpect(status().isUnauthorized());
        var session = new MockHttpSession();
        session.setAttribute(MonitorLoginInterceptor.SESSION_ATTRIBUTE, Boolean.TRUE);
        mvc.perform(get("/monitor/api/sql-risks").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].blocked").value(true))
                .andExpect(jsonPath("$[0].sql").value("SELECT SLEEP(?)"))
                .andExpect(jsonPath("$[0].reasons[0]").value("deny function"))
                .andExpect(jsonPath("$[0].parameters").doesNotExist());
    }
}
