package com.yulinlin.security.session;

/** 基本用户查询、计数与踢人为必需能力；其他能力必须显式声明。存储类型不靠猜测。 */
public enum SessionCapability {
    GLOBAL_QUERY,
    DEVICE_TYPE
}
