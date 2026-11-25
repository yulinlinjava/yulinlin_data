package com.yulinlin.mcp.tool;
import java.util.Collection;
import java.util.Set;

/** 显式注册工具。可选模块（mcp-ops、mcp-data 等）也通过它提供工具，并可以给 initialize 的 instructions 补充一段说明。 */
@FunctionalInterface public interface McpToolProvider {
 Collection<McpToolDefinition> tools();

 /**
  * 写进 initialize instructions 的一段使用说明，只讲本 Provider 的工具怎么用。
  * visibleTools 是当前令牌在 tools/list 中能看到的全部工具名；本 Provider 的工具一个都不可见时应返回 null，
  * 避免 AI 读到讲自己用不了的工具的说明。默认不补充。
  */
 default String instructions(Set<String> visibleTools) { return null; }
}
