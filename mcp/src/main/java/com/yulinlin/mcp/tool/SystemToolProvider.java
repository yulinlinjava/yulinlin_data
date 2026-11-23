package com.yulinlin.mcp.tool;
import com.fasterxml.jackson.databind.*;
import com.yulinlin.mcp.*;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.task.McpTasks;
import com.yulinlin.mcp.process.*;
import java.util.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
public class SystemToolProvider implements McpToolProvider {
 private final McpProperties properties;private final McpTasks tasks;private final McpCredentials credentials;private final McpAccessPolicy policy;
 private final ProcessOverviewService processes;private final ProcessControlService controls;private final ObjectMapper json;
 private final List<McpToolDefinition> definitions=new ArrayList<>();
 public SystemToolProvider(McpProperties p,McpTasks tasks,McpCredentials credentials,McpAccessPolicy policy,ProcessOverviewService processes,ProcessControlService controls,ObjectMapper json){this.properties=p;this.tasks=tasks;this.credentials=credentials;this.policy=policy;this.processes=processes;this.controls=controls;this.json=json;register();}
 public Collection<McpToolDefinition> tools(){return List.copyOf(definitions);}
 private void register(){
        add("server_info", "当前服务器/容器的操作系统、Java、CPU、内存、磁盘及网卡。不是远程宿主机代理。", true, Map.of(), List.of());
        add("command_execute", "Linux 自定义命令/多行脚本，立即返回后台 taskId（id）。cwd 是服务器绝对路径，权限等同 Java 进程；不受业务文件目录限制。默认超时按配置，0 不限时。无需加 & 或 nohup；非交互安装请使用相应非交互参数。长期服务建议 systemd。建议提供唯一 requestId，重试同一命令复用同一编号以免重复启动（任务记录保留期间有效）。", false,
                props("command", str(properties.getCommand().getMaxCommandBytes()), "cwd", str(4096), "timeoutSeconds", integer(0, 604800), "requestId", str(128)), List.of("command"));
        add("task_list", "分页查看后台命令任务，每页 30 条。", true, props("page", integer(1, 10000)), List.of());
        add("task_get", "查看任务状态、命令、PID、退出码。unknown 表示应用重启后无法确认，不代表进程已停止。", true, props("id", str(32)), List.of("id"));
        add("task_output", "按字节偏移读取合并后的 stdout/stderr，每次最多 64 KiB。使用 nextOffset 继续；base64 可无损拼接。", true,
                props("id", str(32), "offset", integer(0, Long.MAX_VALUE), "limit", integer(1, 65536)), List.of("id"));
        add("task_cancel", "取消此实例跟踪的命令及可见子进程，先 TERM，3 秒后 KILL；脱离进程树的服务需另行停止。", false,
                props("id", str(32)), List.of("id"));
        add("process_list", "查看进程 PID、CPU、RSS、工作目录和完整启动参数；停止时使用返回的 stopIdentity。", true,
                props("query", str(100), "page", integer(1, 200), "sort", enumeration("memory", "cpu", "pid")), List.of());
        add("process_stop", "停止指定进程，须传入列表中的身份以防 PID 重用；force=true 使用 KILL。systemd/Docker 服务可能被自动拉起，应使用服务管理命令。", false,
                props("pid", integer(1, Long.MAX_VALUE), "identity", str(64), "force", bool(), "confirm", bool()), List.of("pid", "identity", "confirm"));

 }
 private void add(String name,String description,boolean readOnly,Map<String,Object> props,List<String> required){
  if(name.equals("server_info")&&!properties.getMonitor().isEnabled())return;
  if(name.startsWith("process_")&&!properties.getProcess().isEnabled())return;
  if((name.startsWith("task_")||name.equals("command_execute"))&&!properties.getCommand().isEnabled())return;
  definitions.add(new McpToolDefinition(name,description,json.valueToTree(Map.of("type","object","properties",props,"required",required,"additionalProperties",false)),readOnly,(context,args)->call(context,name,args)));
 }
 private Object call(McpCallContext context,String name,JsonNode a)throws Exception{
  if(name.startsWith("task_")&&!name.equals("task_list"))authorizeTask(context,s(a,"id"));
  return switch(name){
   case "server_info" -> serverInfo();
   case "command_execute" -> tasks.start(credentials.byId(context.credentialId()),s(a,"command"),s(a,"cwd"),n(a,"timeoutSeconds",properties.getCommand().getDefaultTimeoutSeconds()),s(a,"requestId"));
   case "task_list" -> tasks.list(n(a,"page",1)).stream().filter(t->context.credentialId().equals(t.credentialId())||policy.canManage(context.user())).toList();
   case "task_get" -> tasks.get(s(a,"id"));
   case "task_output" -> tasks.output(s(a,"id"),l(a,"offset",0),n(a,"limit",16384));
   case "task_cancel" -> tasks.cancel(s(a,"id"));
   case "process_list" -> processes.list(s(a,"query",""),n(a,"page",1),s(a,"sort","memory"));
   case "process_stop" -> {try{yield controls.stop(l(a,"pid",0),s(a,"identity"),b(a,"force"),b(a,"confirm"));}finally{processes.invalidate();}}
   default -> throw new IllegalArgumentException("Unknown tool");
  };
 }
 private void authorizeTask(McpCallContext c,String id){if(!tasks.get(id).credentialId().equals(c.credentialId())&&!policy.canManage(c.user()))throw new McpException(403,"无权访问其他凭证的任务");}
 private Object serverInfo()throws Exception{
  var os=ManagementFactory.getOperatingSystemMXBean();var result=new LinkedHashMap<String,Object>();
  result.put("os",os.getName());result.put("arch",os.getArch());result.put("javaVersion",System.getProperty("java.version"));result.put("executionUser",System.getProperty("user.name"));result.put("processors",os.getAvailableProcessors());result.put("loadAverage",os.getSystemLoadAverage());result.put("netdiskPid",ProcessHandle.current().pid());result.put("pid",ProcessHandle.current().pid());
  if(os instanceof com.sun.management.OperatingSystemMXBean extended){result.put("cpuLoad",extended.getCpuLoad());result.put("memoryTotal",extended.getTotalMemorySize());result.put("memoryFree",extended.getFreeMemorySize());result.put("swapTotal",extended.getTotalSwapSpaceSize());}
  var disks=new ArrayList<Object>();for(var root:FileSystems.getDefault().getRootDirectories()){try{var disk=Files.getFileStore(root);disks.add(Map.of("path",root.toString(),"totalBytes",disk.getTotalSpace(),"usableBytes",disk.getUsableSpace()));}catch(java.io.IOException ignored){}}
  result.put("disks",disks);var network=new ArrayList<Object>();var interfaces=java.net.NetworkInterface.getNetworkInterfaces();if(interfaces!=null)for(var nic:Collections.list(interfaces))network.add(Map.of("name",nic.getName(),"addresses",Collections.list(nic.getInetAddresses()).stream().map(java.net.InetAddress::getHostAddress).toList()));result.put("network",network);return result;
 }
    private static Map<String,Object> props(Object... values) { var result = new LinkedHashMap<String,Object>(); for(int i=0;i<values.length;i+=2) result.put((String)values[i],values[i+1]); return result; }
    private static Map<String,Object> str(int max) { return Map.of("type","string","maxLength",max); }
    private static Map<String,Object> integer(long min,long max) { return Map.of("type","integer","minimum",min,"maximum",max); }
    private static Map<String,Object> bool() { return Map.of("type","boolean"); }
    private static Map<String,Object> enumeration(String... choices) { return Map.of("type","string","enum",List.of(choices)); }
    private static String s(JsonNode a,String key) { return s(a,key,null); }
    private static String s(JsonNode a,String key,String fallback) { return a.has(key)?a.get(key).asText():fallback; }
    private static int n(JsonNode a,String key,int fallback) { return a.has(key)?a.get(key).asInt():fallback; }
    private static long l(JsonNode a,String key,long fallback) { return a.has(key)?a.get(key).asLong():fallback; }
    private static boolean b(JsonNode a,String key) { return a.path(key).asBoolean(false); }

}

