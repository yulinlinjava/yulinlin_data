package com.yulinlin.mcp.task;
import com.yulinlin.mcp.McpProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
public class LocalCommandExecutor implements CommandExecutor {
 private final McpProperties properties;
 public LocalCommandExecutor(McpProperties properties){this.properties=properties;}
 public Process start(String command,Path directory)throws IOException{
  if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("linux"))throw new IllegalArgumentException("默认命令执行器只支持 Linux");
  return new ProcessBuilder(properties.getCommand().getShell(),"-c",command).directory(directory.toFile()).redirectErrorStream(true).start();
 }
}
