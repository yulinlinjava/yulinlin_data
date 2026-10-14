package com.yulinlin.monitor;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Serves the module-owned single-file Element Plus monitor page. */
@Controller
public final class MonitorPageController {
    private static final Resource PAGE = new ClassPathResource("META-INF/yulinlin-monitor/index.html");

    @GetMapping("/monitor")
    public String redirectToPage() {
        return "redirect:/monitor/";
    }

    @ResponseBody
    @GetMapping(value = "/monitor/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(PAGE);
    }
}
