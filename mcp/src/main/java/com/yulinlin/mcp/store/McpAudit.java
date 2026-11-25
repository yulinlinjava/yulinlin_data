package com.yulinlin.mcp.store;
import com.yulinlin.mcp.auth.McpPrincipal;
import com.yulinlin.mcp.McpProperties;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;


public class McpAudit implements McpAuditService {
    private final McpStore store; private final McpProperties properties;
    private final McpAuditTargetProvider targets;
    public McpAudit(McpStore store,McpProperties properties) { this(store, properties, List.of()); }
    public McpAudit(McpStore store,McpProperties properties,McpAuditTargetProvider targets) { this(store, properties, List.of(targets)); }
    /**
     * @param providers 全部目标字段提供者，按顺序合并，同名字段后者覆盖。可选模块（mcp-ops、mcp-data）和业务各自注册，
     *                  互相追加、不互相替换：业务提供自己的 Provider 不会让模块记录的字段消失。
     */
    public McpAudit(McpStore store,McpProperties properties,List<McpAuditTargetProvider> providers) {
        this.store=store;this.properties=properties;var all=List.copyOf(providers);
        this.targets=(tool,args)->{var merged=new LinkedHashMap<String,String>();for(var p:all){var m=p.target(tool,args);if(m!=null)merged.putAll(m);}return merged;};
    }
    public Entry begin(McpPrincipal principal, String tool, JsonNode args) {
        var target = new LinkedHashMap<String,String>();
        // Do not copy command text, file content, base64 uploads, tokens or tool output into the audit table.
        targets.target(tool, args.deepCopy()).entrySet().stream().limit(16).forEach(entry -> {
            String key=Objects.requireNonNull(entry.getKey()), value=Objects.requireNonNull(entry.getValue());
            target.put(key.substring(0,Math.min(key.length(),128)),value.substring(0,Math.min(value.length(),512)));
        });
        var row = new Entry(McpStore.id(), principal.credentialId(), principal.name(), principal.accountId(), tool, target,
                System.currentTimeMillis(), null, "started");
        store.save("audit-"+row.id(),"audit",row); return row;
    }
    public void finish(Entry row, boolean success) {
        store.save("audit-"+row.id(),"audit",new Entry(row.id(),row.credentialId(),row.clientName(),row.userId(),row.tool(),row.target(),
                row.createdAt(),System.currentTimeMillis(),success?"succeeded":"failed"));
    }
    public List<Entry> list(int page) { return store.list("audit",page,50,Entry.class); }
    public void recordManagement(ManagementEntry entry) { store.save("manage-audit-"+entry.id(),"manage-audit",entry); }
    public List<ManagementEntry> listManagement(int page) { return store.list("manage-audit",page,50,ManagementEntry.class); }
    public void clean() {
        // Keep retentionDays and at most the newest 10,000 records per kind. "started" after restart has an unknown outcome.
        long cutoff = System.currentTimeMillis()-properties.getRetentionDays()*86_400_000L;
        clean("audit-",cutoff,this::list,Entry::id,Entry::createdAt);
        clean("manage-audit-",cutoff,this::listManagement,ManagementEntry::id,ManagementEntry::time);
    }
    private <T> void clean(String prefix,long cutoff,java.util.function.IntFunction<List<T>> pages,
                           java.util.function.Function<T,String> id,java.util.function.ToLongFunction<T> time) {
        var expired = new ArrayList<String>();
        for (int page=1;page<=200;page++) {
            var rows=pages.apply(page);
            for(var row:rows) if(time.applyAsLong(row)<cutoff) expired.add(id.apply(row));
            if(rows.size()<50) break;
        }
        for(var key:expired) store.delete(prefix+key);
        for(int batch=0;batch<200;batch++) {
            var old=pages.apply(201); if(old.isEmpty()) break;
            for(var row:old) store.delete(prefix+id.apply(row));
        }
    }
    public record Entry(String id, String credentialId, String clientName, String userId, String tool, Map<String,String> target,
                        long createdAt, Long finishedAt, String state) { }
    /**
     * 令牌管理审计。changes 为字段变化 {字段: {from, to}}，不含任何 token 或 hash。
     */
    public record ManagementEntry(String id, String action, String credentialId, String keyId, String ownerId,
                                  String actorId, String ip, Map<String,Object> changes, long time) { }
}
