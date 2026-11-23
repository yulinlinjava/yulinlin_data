package com.yulinlin.mcp.store;
import com.yulinlin.mcp.auth.McpCredentials;
import com.yulinlin.mcp.McpProperties;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;


public class McpAudit {
    private final McpStore store; private final McpProperties properties;
    private final McpAuditTargetProvider targets;
    public McpAudit(McpStore store,McpProperties properties) { this(store, properties, (tool, args) -> Map.of()); }
    public McpAudit(McpStore store,McpProperties properties,McpAuditTargetProvider targets) {
        this.store=store;this.properties=properties;this.targets=targets;
    }
    public Entry begin(McpCredentials.Credential credential, String tool, JsonNode args) {
        var target = new LinkedHashMap<String,String>();
        // Do not copy command text, file content, base64 uploads, tokens or tool output into the audit table.
        targets.target(tool, args.deepCopy()).entrySet().stream().limit(16).forEach(entry -> {
            String key=Objects.requireNonNull(entry.getKey()), value=Objects.requireNonNull(entry.getValue());
            target.put(key.substring(0,Math.min(key.length(),128)),value.substring(0,Math.min(value.length(),512)));
        });
        var row = new Entry(McpStore.id(), credential.id(), credential.name(), credential.userId(), tool, target,
                System.currentTimeMillis(), null, "started");
        store.save("audit-"+row.id(),"audit",row); return row;
    }
    public void finish(Entry row, boolean success) {
        store.save("audit-"+row.id(),"audit",new Entry(row.id(),row.credentialId(),row.clientName(),row.userId(),row.tool(),row.target(),
                row.createdAt(),System.currentTimeMillis(),success?"succeeded":"failed"));
    }
    public List<Entry> list(int page) { return store.list("audit",page,50,Entry.class); }
    public void clean() {
        // Keep 30 days and at most the newest 10,000 calls. "started" after restart has an unknown outcome.
        long cutoff = System.currentTimeMillis()-properties.getRetentionDays()*86_400_000L;
        var expired = new ArrayList<String>();
        for (int page=1;page<=200;page++) {
            var rows=list(page);
            for(var row:rows) if(row.createdAt()<cutoff) expired.add(row.id());
            if(rows.size()<50) break;
        }
        for(var id:expired) store.delete("audit-"+id);
        for(int batch=0;batch<200;batch++) {
            var old=list(201); if(old.isEmpty()) break;
            for(var row:old) store.delete("audit-"+row.id());
        }
    }
    public record Entry(String id, String credentialId, String clientName, String userId, String tool, Map<String,String> target,
                        long createdAt, Long finishedAt, String state) { }
}
