package com.yulinlin.mcp.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.common.model.*;
import com.yulinlin.mcp.McpProperties;
import com.yulinlin.data.core.session.SessionUtil;
import org.springframework.stereotype.Repository;
import java.util.*;


public class OrmMcpStore implements McpStore {
 private final McpProperties properties;
 private String group;
 private String key(String key){return properties.getApplicationId()+":"+key;}
 private String kind(String kind){return properties.getApplicationId()+":"+kind;}
 private ModelSelectWrapper<McpRecordEntity> select(){var q=ModelSelectWrapper.newInstance(group,McpRecordEntity.class).cache(false).autoUpdate(false);q.getRequest().setCluster(com.yulinlin.data.core.anno.JoinCluster.master);return q;}
 public void initialize(){if((properties.getGroup()==null||properties.getGroup().isBlank())&&SessionUtil.route().sessionSize()!=1)throw new IllegalStateException("多个数据源时必须指定 yulinlin.mcp.group");var session=SessionUtil.route().session(properties.getGroup());group=session.group();session.initializeSchema(List.of(McpRecordEntity.class)); ModelSelectWrapper.newInstance(group,McpRecordEntity.class).page(1,1).selectList();}

    private final ObjectMapper json;
    public OrmMcpStore(ObjectMapper json,McpProperties properties) { this.json=json;this.properties=properties; }
    public <T> T get(String key, Class<T> type) {
        var row = select().eq("recordKey", key(key)).selectOne();
        return row == null ? null : decode(row, type);
    }
    public <T> List<T> list(String kind, int page, int size, Class<T> type) {
        if (page < 1 || page > 10000 || size < 1 || size > 2000) throw new IllegalArgumentException("分页参数无效");
        return select().eq("kind", kind(kind))
                .orderBy("updatedAt", false).page(page, size).selectList().stream().map(row -> decode(row, type)).toList();
    }
    public synchronized void save(String key, String kind, Object value) {
        String payload;
        try { payload = json.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("MCP 数据编码失败", error); }
        var existing = select().eq("recordKey", key(key)).selectOne();
        int changed;
        if (existing == null) {
            var row = new McpRecordEntity(); row.setRecordKey(key(key)); row.setKind(kind(kind));
            row.setPayload(payload); row.setUpdatedAt(System.currentTimeMillis());
            changed = ModelInsertWrapper.newInstance(group, row).execute();
        } else changed = ModelUpdateWrapper.newInstance(group, McpRecordEntity.class)
                .field("payload", payload).field("updatedAt", System.currentTimeMillis()).eq("recordKey", key(key)).execute();
        if (changed != 1) throw new IllegalStateException("MCP 数据保存失败");
    }
    public void delete(String key) { ModelDeleteWrapper.newInstance(group, McpRecordEntity.class).eq("recordKey", key(key)).execute(); }
    public long count(String kind) { return select().eq("kind", kind(kind)).count(); }
    private <T> T decode(McpRecordEntity row, Class<T> type) {
        try { return json.readValue(row.getPayload(), type); }
        catch (Exception error) { throw new IllegalStateException("MCP 数据不可用，请检查存储", error); }
    }
    public void create(String key,String kind,Object value){
 try {var row=new McpRecordEntity();row.setRecordKey(key(key));row.setKind(kind(kind));row.setPayload(json.writeValueAsString(value));row.setUpdatedAt(System.currentTimeMillis());if(ModelInsertWrapper.newInstance(group,row).execute()!=1)throw new IllegalStateException("MCP insert failed");}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
}
