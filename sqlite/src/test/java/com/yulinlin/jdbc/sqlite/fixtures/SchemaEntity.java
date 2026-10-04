package com.yulinlin.jdbc.sqlite.fixtures;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@JoinTable("schema_entity")
public class SchemaEntity extends IdEntity<SchemaEntity> {
    @JoinField(name = "display_name") private String name;
    private Date createdAt;
    private State state;
    private BigDecimal amount;
    private Map<String, String> details;
    private Payload payload;
    private byte[] bytes;
    private Integer quantity;
    private Double ratio;
    private Boolean enabled;
    @JoinField(exist = false) private String ignored;
    @JoinField(function = "upper(display_name)") private String computed;
    public enum State { READY, DONE }
    @Data public static class Payload { private String message; }
}
