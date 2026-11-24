package com.yulinlin.mcp.tool;

import java.lang.annotation.*;

/** Exposes a public Spring bean method with zero arguments or one request DTO. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {
    String name();
    String description();
    boolean readOnly() default false;
}
