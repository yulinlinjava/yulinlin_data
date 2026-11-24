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
 private final McpProperties properties;private final McpTasks tasks;private final McpAccessPolicy policy;
 private final ProcessOverviewService processes;private final ProcessControlService controls;private final ObjectMapper json;
 private final List<McpToolDefinition> definitions=new ArrayList<>();
 public SystemToolProvider(McpProperties p,McpTasks tasks,McpAccessPolicy policy,ProcessOverviewService processes,ProcessControlService controls,ObjectMapper json){this.properties=p;this.tasks=tasks;this.policy=policy;this.processes=processes;this.controls=controls;this.json=json;register();}
 public Collection<McpToolDefinition> tools(){return List.copyOf(definitions);}
 private void register(){
        add("server_info", "当前服务器/容器的操作系统、Java、CPU、内存、磁盘及网卡。不是远程宿主机代理。", true, Map.of(), List.of());
        add("command_execute", "Linux 自定义命令/多行脚本，立即返回后台 taskId（id）。cwd 是服务器绝对路径，权限等同 Java 进程；不受业务文件目录限制。默认超时按配置，0 不限时。无需加 & 或 nohup；非交互安装请使用相应非交互参数。长期服务建议 systemd。建议提供唯一 requestId，重试同一命令复用同一编号以免重复启动（任务记录保留期间有效）。", false,
                props("command", described(str(properties.getCommand().getMaxCommandBytes()), "交给配置的 Linux shell 执行的命令或多行脚本，UTF-8 字节数上限为 "+properties.getCommand().getMaxCommandBytes()+"；无需添加 & 或 nohup。"),
                        "cwd", described(str(4096), "服务器上已存在的绝对工作目录；省略或空白时使用应用进程的工作目录。不是业务虚拟文件路径。"),
                        "timeoutSeconds", defaulted(integer(0, 604800), "任务超时秒数，0 表示不限时；达到超时会尝试终止进程及可见子进程。", properties.getCommand().getDefaultTimeoutSeconds()),
                        "requestId", described(str(128), "可选幂等编号，1–128 位字母、数字、下划线或短横线。同一应用及凭证内，重试相同命令、规范化目录和超时须复用编号；不同参数复用会冲突。记录删除后不再去重，省略时每次创建新任务。")), List.of("command"));
        add("task_list", "分页查看有权访问的后台命令任务，每页最多 30 条；先按全局任务页读取再过滤凭证，页面可能为空或不足 30 条，不能单凭空页判断其他页不存在。", true,
                props("page", defaulted(integer(1, 10000), "任务页码，从 1 开始；每个全局任务页最多 30 条。", 1)), List.of());
        add("task_get", "查看任务状态、命令、PID、退出码。unknown 表示应用重启后无法确认，不代表进程已停止。", true,
                props("id", described(str(32), "command_execute 或 task_list 返回的任务 id，32 位小写十六进制；不是 PID。")), List.of("id"));
        add("task_output", "按字节偏移读取合并后的 stdout/stderr，每次最多 64 KiB。使用 nextOffset 继续；base64 可无损拼接。", true,
                props("id", described(str(32), "任务 id，32 位小写十六进制；必须访问该任务所属实例。"),
                        "offset", defaulted(integer(0, Long.MAX_VALUE), "输出文件的字节偏移，从 0 开始；下一次传上次返回的 nextOffset。不是字符下标。", 0),
                        "limit", defaulted(integer(1, 65536), "本次最多读取的字节数，最大 65536（64 KiB）。", 16384)), List.of("id"));
        add("task_cancel", "取消此实例跟踪的命令及可见子进程，先 TERM，3 秒后 KILL；脱离进程树的服务需另行停止。", false,
                props("id", described(str(32), "要取消的任务 id，32 位小写十六进制；必须访问正在跟踪该任务的实例。unknown/已结束任务不能按旧 PID 取消。")), List.of("id"));
        add("process_list", "查看进程 PID、CPU、RSS、工作目录和完整启动参数；停止时使用返回的 stopIdentity。", true,
                props("query", defaulted(str(100), "可选搜索词，忽略大小写，匹配 PID、名称、应用、路径、用户、父 PID 或命令行；空字符串表示不筛选。", ""),
                        "page", defaulted(integer(1, 200), "进程页码，从 1 开始，每页 50 条；超过实际总页数时返回最后一页。", 1),
                        "sort", defaulted(enumeration("memory", "cpu", "pid"), "降序排序字段：memory 为常驻内存字节数，cpu 为 CPU 占用百分比，pid 为进程 ID；不可用指标排最后。", "memory")), List.of());
        add("process_stop", "停止指定进程，须传入列表中的身份以防 PID 重用；force=true 使用 KILL。systemd/Docker 服务可能被自动拉起，应使用服务管理命令。", false,
                props("pid", described(integer(1, Long.MAX_VALUE), "要停止的系统进程 ID，来自 process_list；不能传后台任务 id。"),
                        "identity", described(str(64), "原样传入 process_list 对应行的 stopIdentity（精确启动时刻字符串），防止 PID 被复用。为 null 时不允许停止；不可用 startedAt 毫秒值替代。"),
                        "force", defaulted(bool(), "false 尝试正常停止；true 请求强制终止。不会因正常停止失败而自动升级为强制停止。", false),
                        "confirm", described(bool(), "明确确认停止操作，执行时必须为 true；false 会拒绝操作。")), List.of("pid", "identity", "confirm"));

 }
 private void add(String name,String description,boolean readOnly,Map<String,Object> props,List<String> required){
  if(name.equals("server_info")&&!properties.getMonitor().isEnabled())return;
  if(name.startsWith("process_")&&!properties.getProcess().isEnabled())return;
  if((name.startsWith("task_")||name.equals("command_execute"))&&!properties.getCommand().isEnabled())return;
  definitions.add(new McpToolDefinition(name,description,json.valueToTree(Map.of("type","object","description",description,"properties",props,"required",required,"additionalProperties",false)),json.valueToTree(SystemToolSchemas.output(name)),readOnly,(context,args)->call(context,name,args)));
 }
 private Object call(McpCallContext context,String name,JsonNode a)throws Exception{
  if(name.startsWith("task_")&&!name.equals("task_list"))authorizeTask(context,s(a,"id"));
  return switch(name){
   case "server_info" -> serverInfo();
   case "command_execute" -> tasks.start(context.principal(),s(a,"command"),s(a,"cwd"),n(a,"timeoutSeconds",properties.getCommand().getDefaultTimeoutSeconds()),s(a,"requestId"));
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
    private static Map<String,Object> described(Map<String,Object> schema,String description) {var result=new LinkedHashMap<>(schema);result.put("description",description);return result;}
    private static Map<String,Object> defaulted(Map<String,Object> schema,String description,Object value) {var result=described(schema,description);result.put("default",value);return result;}
    private static String s(JsonNode a,String key) { return s(a,key,null); }
    private static String s(JsonNode a,String key,String fallback) { return a.has(key)?a.get(key).asText():fallback; }
    private static int n(JsonNode a,String key,int fallback) { return a.has(key)?a.get(key).asInt():fallback; }
    private static long l(JsonNode a,String key,long fallback) { return a.has(key)?a.get(key).asLong():fallback; }
    private static boolean b(JsonNode a,String key) { return a.path(key).asBoolean(false); }

}

