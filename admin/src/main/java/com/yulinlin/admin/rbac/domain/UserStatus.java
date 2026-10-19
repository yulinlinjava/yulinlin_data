package com.yulinlin.admin.rbac.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.yulinlin.common.domain.enums.SuperEnum;

/** 数据库和接口都使用稳定编码，不依赖枚举顺序。 */
public enum UserStatus implements SuperEnum<String> {
    ENABLED("enabled", "正常"),
    DISABLED("disabled", "停用"),
    LOCKED("locked", "锁定");

    private final String value;
    private final String label;

    UserStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    @JsonValue
    public String getValue() { return value; }

    @Override
    public String getLabel() { return label; }

    @JsonCreator
    public static UserStatus from(String value) {
        for (UserStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) return status;
        }
        throw new IllegalArgumentException("用户状态不正确");
    }
}
