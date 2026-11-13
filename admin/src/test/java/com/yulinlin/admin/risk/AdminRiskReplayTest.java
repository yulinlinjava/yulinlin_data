package com.yulinlin.admin.risk;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.risk.RiskModels.*;
import com.yulinlin.security.risk.RiskDetector;
import com.yulinlin.security.risk.RiskProperties;
import com.yulinlin.security.risk.RiskReplay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Standalone JUnit: no application startup, database, real RiskControl or waiting. */
class AdminRiskReplayTest {
    private static final ZoneId RECORD_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long START = LocalDateTime.of(2026,11,5,0,0).atZone(RECORD_ZONE).toInstant().toEpochMilli();
    private static ReplayEvent row(String user,long offset) {
        return new ReplayEvent(user,"video-play-url","video-"+offset,"row-"+offset,START+offset);
    }
    private static List<ReplayResult> replay(List<ReplayEvent> events) {
        var result=new RiskReplay(config()).replay(events);printReport(result);return result;
    }
    @Test @DisplayName("01 极速快切：每秒一个不同视频，第 51 条请求命中 50 个极速目标")
    void rapidSwitch() {
        var events=new ArrayList<ReplayEvent>();for(int i=0;i<=50;i++)events.add(row("rapid",i*1000L));
        var result=replay(events);
        assertTrue(result.subList(0,50).stream().allMatch(r->r.decision().matches().isEmpty()));
        assertEquals("rapid",result.getLast().decision().matches().getFirst().ruleId());
        assertEquals(50,result.getLast().decision().matches().getFirst().statistics().get("fastVideos"));
    }
    @Test @DisplayName("02 普通快切：每四秒一个视频，第 200 条请求命中，不受旧十分钟门槛影响")
    void normalSwitch() {
        var events=new ArrayList<ReplayEvent>();for(int i=0;i<200;i++)events.add(row("normal",i*4000L));
        var result=replay(events);
        assertTrue(result.subList(0,199).stream().allMatch(r->r.decision().matches().isEmpty()));
        assertEquals("normal",result.getLast().decision().matches().getFirst().ruleId());
        assertEquals(START+796_000,result.getLast().event().occurredAt());
    }
    @Test @DisplayName("03 全天候采集：间隔一、三、五分钟，完整六小时后命中")
    void aroundTheClock() {
        var events=new ArrayList<ReplayEvent>();long offset=0;int i=0;
        while(offset<Duration.ofHours(6).toMillis()) {
            events.add(row("collector",offset));offset+=List.of(60_000L,180_000L,300_000L).get(i++%3);
        }
        events.add(row("collector",Duration.ofHours(6).toMillis()));
        var result=replay(events);
        assertTrue(result.subList(0,result.size()-1).stream().allMatch(r->r.decision().matches().isEmpty()));
        assertEquals("active-access",result.getLast().decision().matches().getFirst().componentId());
    }
    @Test @DisplayName("04 间歇浏览：每小时活跃十分钟、休息五十分钟，不命中全天候规则")
    void intermittentBrowsing() {
        var events=new ArrayList<ReplayEvent>();
        for(int hour=0;hour<6;hour++)for(int i=0;i<150;i++)events.add(row("viewer",hour*3_600_000L+i*4000L));
        events.add(row("viewer",Duration.ofHours(6).toMillis()));
        var result=replay(events);assertTrue(result.stream().allMatch(r->r.decision().matches().isEmpty()));
        var active=result.getLast().decision().components().stream().filter(d->d.componentId().equals("active-access")).findFirst().orElseThrow();
        assertEquals(6,active.statistics().get("activeBuckets"));
    }
    @Test @DisplayName("05 自定义组件：业务敏感资源规则，每次回放创建独立实例")
    void customComponent() {
        var p=config();p.getBehaviors().get("video-play-url").setComponents(List.of("fast-switch","active-access","business-check"));
        var replay=new RiskReplay(p,()->List.of(new BusinessDetector()));
        var events=List.of(new ReplayEvent("u","video-play-url","sensitive",START));
        var first=replay.replay(events);assertEquals(first,replay.replay(events));printReport(first);
        assertEquals("business-check",first.getFirst().decision().matches().getFirst().componentId());
        assertTrue(first.getFirst().decision().allowed()); // Replay reports hits; never performs real restriction.
    }
    static final class BusinessDetector implements RiskDetector {
        public String id(){return "business-check";}
        public Detection detect(BehaviorEvent event,long at) {
            boolean hit="sensitive".equals(event.resourceId());
            return new Detection(id(),hit?DetectionStatus.MATCHED:DetectionStatus.NORMAL,null,
                    hit?List.of(new Match(id(),"sensitive-resource",at,at,"命中业务敏感资源",Map.of())):List.of(),Map.of());
        }
        public void reset(Subject subject) { /* This example has no mutable state. */ }
    }
    @Test @DisplayName("06 导入真实 JSON 历史：设置 -Drisk.replay.file 指定文件")
    void importedHistory() throws Exception {
        String file=System.getProperty("risk.replay.file");
        assumeTrue(file!=null&&!file.isBlank(),"VM options: -Drisk.replay.file=C:/data/history.json");
        List<HistoryRow> rows;
        try(var reader=Files.newBufferedReader(Path.of(file))) {
            rows=new ObjectMapper().readValue(reader,new TypeReference<List<HistoryRow>>(){});
        }
        assertFalse(rows.isEmpty(),"行为记录不能为空");
        var events=rows.stream().map(r->new ReplayEvent(r.userId(),"video-play-url",r.videoId(),r.id(),
                LocalDateTime.parse(r.crtTime().replace(' ','T')).atZone(RECORD_ZONE).toInstant().toEpochMilli())).toList();
        var results=replay(events);assertEquals(rows.size(),results.size());
        // Unknown real samples have no preset label. Inspect each component before adding expected assertions.
    }
    private record HistoryRow(String id,@JsonAlias("user_id") String userId,
                              @JsonAlias("video_id") String videoId,@JsonAlias("crt_time") String crtTime) {}
    static RiskProperties config() {
        var p=new RiskProperties();
        p.setMode(RiskProperties.Mode.OBSERVE);
        // Defaults: rapid 5m/<2s/50; normal 15m/<=5s/200/70%; active 6h/90%/60.
        p.getBehaviors().put("video-play-url",new RiskProperties.Behavior());
        return p;
    }
    static void printReport(List<ReplayResult> results) {
        System.out.printf("%n回放完成：输入 %d 条（只报告识别，不执行业务处罚）%n",results.size());
        for(var status:Status.values()) {
            long count=results.stream().filter(r->r.decision().status()==status).count();
            if(count>0)System.out.printf("  %s = %d%n",status,count);
        }
        var reported=new HashSet<String>();
        for(var row:results)for(var match:row.decision().matches()) {
            String key=row.event().userId()+"\0"+row.event().behaviorType()+"\0"+match.componentId()+"\0"+match.ruleId();
            if(!reported.add(key))continue;
            System.out.printf("首次命中：用户=%s 组件=%s 档位=%s 原始行号=%d 时间=%s%n",
                    row.event().userId(),match.componentId(),match.ruleId(),row.inputIndex()+1,
                    Instant.ofEpochMilli(row.event().occurredAt()).atZone(RECORD_ZONE));
            System.out.printf("  原因=%s 统计=%s 窗口=[%s, %s]%n",match.reason(),match.statistics(),
                    Instant.ofEpochMilli(match.windowStart()).atZone(RECORD_ZONE),Instant.ofEpochMilli(match.windowEnd()).atZone(RECORD_ZONE));
        }
        if(reported.isEmpty())System.out.println("没有命中；OBSERVING / CAPACITY 不表示已验证正常。");
    }
}
