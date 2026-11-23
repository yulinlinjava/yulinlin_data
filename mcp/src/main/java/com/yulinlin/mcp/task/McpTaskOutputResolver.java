package com.yulinlin.mcp.task;

import java.nio.file.Path;

/** Maps a validated task ID to a local output file, including legacy output directories. */
@FunctionalInterface
public interface McpTaskOutputResolver {
    Path resolve(String taskId);
}
