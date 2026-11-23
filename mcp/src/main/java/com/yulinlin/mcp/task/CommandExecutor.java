package com.yulinlin.mcp.task;
import java.io.IOException;
import java.nio.file.Path;
@FunctionalInterface public interface CommandExecutor { Process start(String command,Path directory)throws IOException; }
