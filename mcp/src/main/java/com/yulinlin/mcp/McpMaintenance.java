package com.yulinlin.mcp;
import com.yulinlin.mcp.store.McpAuditService;
import java.util.List;
import java.util.concurrent.*;
/** Own lifecycle: does not enable scheduling for unrelated application beans. */
public class McpMaintenance implements AutoCloseable {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(McpMaintenance.class);
 private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("mcp-cleanup").factory());
 private final McpAuditService audit;
 private final List<McpLifecycle> lifecycles;
 private boolean started;
 public McpMaintenance(McpAuditService audit){this(audit,List.of());}
 /** 可选模块的清理（命令任务、数据写入幂等记录等）通过 McpLifecycle#clean 参与。 */
 public McpMaintenance(McpAuditService audit,List<McpLifecycle> lifecycles){this.audit=audit;this.lifecycles=List.copyOf(lifecycles);}
 public synchronized void start(){if(started)return;started=true;executor.scheduleWithFixedDelay(this::cleanOnce,60,3600,TimeUnit.SECONDS);}
 void cleanOnce(){
  try{audit.clean();}catch(RuntimeException e){log.warn("MCP cleanup failed",e);}
  for(var lifecycle:lifecycles){try{lifecycle.clean();}catch(RuntimeException e){log.warn("MCP cleanup failed: {}",lifecycle.getClass().getSimpleName(),e);}}
 }
 public void close(){executor.shutdownNow();}
}
