package com.yulinlin.mcp;
import com.yulinlin.mcp.store.McpAudit;
import com.yulinlin.mcp.task.McpTasks;
import java.util.concurrent.*;
/** Own lifecycle: does not enable scheduling for unrelated application beans. */
public class McpMaintenance implements AutoCloseable {
 private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("mcp-cleanup").factory());
 private final McpAudit audit;
 private final McpTasks tasks;
 private boolean started;
 public McpMaintenance(McpAudit audit,McpTasks tasks){this.audit=audit;this.tasks=tasks;}
 public synchronized void start(){if(started)return;started=true;executor.scheduleWithFixedDelay(()->{try{audit.clean();if(tasks!=null)tasks.clean();}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(McpMaintenance.class).warn("MCP cleanup failed",e);}},60,3600,TimeUnit.SECONDS);}
 public void close(){executor.shutdownNow();}
}
