package com.yulinlin.mcp.tool;

import java.util.*;

/** Discoverable result contracts for the built-in tools; the runtime returns structuredContent.data. */
final class SystemToolSchemas {
    private SystemToolSchemas() { }

    static Map<String, Object> output(String tool) {
        var data = switch (tool) {
            case "command_execute", "task_get", "task_cancel" -> task();
            case "task_list" -> array("当前页中有权访问的任务列表，可能为空；不包含分页总数。", task());
            case "task_output" -> taskOutput();
            case "process_list" -> processPage();
            case "process_stop" -> object("停止请求结果，不保证服务不会被外部守护进程重新拉起。", List.of("pid", "state", "force"),
                "pid", field("integer", "本次操作的系统进程 ID。"),
                "state", values("requested 表示已发送停止请求但进程尚存活；exited 表示已观察到退出或进程已不存在。", "requested", "exited"),
                "force", field("boolean", "本次是否请求强制停止，与输入 force 对应。"));
            case "server_info" -> serverInfo();
            default -> throw new IllegalArgumentException("No result schema for tool: " + tool);
        };
        return object("工具成功结果的 structuredContent；实际业务值位于 data。失败时读取 isError 和 content 中的错误说明，写操作不能盲目重试。",
            List.of("data"), "data", data,
            "warning", field("string", "可选警告：操作可能已完成但审计完成状态保存失败；不要自动重复执行。"));
    }

    private static Map<String, Object> task() {
        return object("后台命令任务。command_execute 返回任务并不代表命令成功，请继续调用 task_get/task_output。",
            List.of("id", "credentialId", "command", "cwd", "state", "createdAt", "timeoutSeconds", "outputTruncated"),
            "id", field("string", "32 位小写十六进制任务 ID，供 task_get、task_output、task_cancel 的 id 参数使用。"),
            "credentialId", field("string", "创建任务的凭证 ID，不是凭证明文，不可作为 Bearer Token 使用。"),
            "command", field("string", "保存的命令或脚本文本，CRLF 已规范化为 LF。"),
            "cwd", field("string", "规范化后的服务器绝对工作目录。"),
            "state", values("queued=已持久化待启动；running=执行中；cancelling=取消中；succeeded=退出码 0；failed=启动/执行失败或非零退出；cancelled=已取消；timed_out=超时；unknown=重启后无法确认，不能据此认定进程已停止或自动重跑。",
                "queued", "running", "cancelling", "succeeded", "failed", "cancelled", "timed_out", "unknown"),
            "createdAt", field("integer", "任务创建时间，Unix 毫秒时间戳。"),
            "startedAt", nullable("integer", "进程启动时间，Unix 毫秒时间戳；尚未启动时为 null 或省略。"),
            "finishedAt", nullable("integer", "跟踪结束时间，Unix 毫秒时间戳；未结束时为 null 或省略。unknown 状态下不是进程真实退出时间。"),
            "pid", nullable("integer", "执行进程 PID；尚未启动可能为空。旧记录的 PID 可能被复用，不能直接用于停止进程。"),
            "exitCode", nullable("integer", "进程退出码；未取得退出码时为 null 或省略，不能把 null 当作成功。"),
            "timeoutSeconds", field("integer", "任务超时秒数，0 表示不限时。"),
            "message", nullable("string", "失败或状态说明；无附加说明时为 null 或省略。"),
            "outputTruncated", field("boolean", "是否存在输出截断或读取/保存异常；true 表示日志可能不完整。"),
            "instanceId", nullable("string", "任务所属应用实例，读取输出和取消需访问该实例；旧记录可能为空，按 local 处理。"),
            "runId", nullable("string", "创建/恢复记录的应用运行批次标识，用于区分重启；旧记录可能为空，不是业务任务 ID。"));
    }

    private static Map<String, Object> taskOutput() {
        return object("一段任务 stdout/stderr 合并输出，偏移按字节计算。",
            List.of("text", "base64", "nextOffset", "hasMore", "truncated"),
            "text", field("string", "本段字节直接按 UTF-8 解码的文本。跨页字符可能拆开；无损合并应使用 base64 字节。"),
            "base64", field("string", "本段原始字节的 Base64；逐段解码后拼接字节，再统一按 UTF-8 解码。"),
            "nextOffset", field("integer", "下一次请求的字节偏移。输出文件尚不存在时返回 0，不能据此判断任务结束。"),
            "hasMore", field("boolean", "当前输出文件是否还有未读取字节。false 不代表任务已结束，执行中仍可能产生新输出。"),
            "truncated", field("boolean", "输出是否因容量上限或读写异常而不完整；截断后进程仍会继续被排空输出。"));
    }

    private static Map<String, Object> processPage() {
        var row = object("进程快照。指标不可用时对应字段可能为 null 或省略，不能当作 0。",
            List.of("pid", "name", "netdisk", "commandTruncated"),
            "pid", field("integer", "系统进程 ID，调用 process_stop 时还必须提供同一行的 stopIdentity。"),
            "name", field("string", "进程名称或不可读取提示。"),
            "startedAt", nullable("integer", "启动时间，Unix 毫秒时间戳；不能替代 stopIdentity。"),
            "runningSeconds", nullable("integer", "进程已运行的秒数。"),
            "cpuPercent", nullable("number", "相邻采样间的 CPU 占用百分比，单核心满载约 100，多核心可超过 100；首次采样可能为空。"),
            "memoryBytes", nullable("integer", "常驻物理内存 RSS，单位字节；平台不支持或不可读时为空。"),
            "netdisk", field("boolean", "兼容字段名：true 表示当前 Java 应用进程，并非判断它是否为网盘产品。"),
            "stopIdentity", nullable("string", "可停止进程的精确启动时刻字符串。原样传给 process_stop.identity；为空时不允许停止。"),
            "stopBlockedReason", nullable("string", "禁止停止的原因；无阻止原因时为空。"),
            "application", nullable("string", "尽力识别的应用名称，不保证能准确识别。"),
            "executable", nullable("string", "可执行文件路径，可能因权限限制不可见。"),
            "user", nullable("string", "执行该进程的操作系统用户。"),
            "parentPid", nullable("integer", "父进程 PID，无法读取时为空。"),
            "workingDirectory", nullable("string", "服务器上的进程工作目录。"),
            "launchTarget", nullable("string", "识别出的启动目标，例如 JAR、脚本或程序路径。"),
            "commandLine", nullable("string", "用于展示的启动命令行，可能包含敏感参数，可能被截断；不读取进程环境变量。"),
            "commandTruncated", field("boolean", "命令行显示是否因长度/参数数量上限而截断。"));
        return object("当前可见进程的筛选分页结果；快照最多约每 2 秒刷新一次，每页 50 条。",
            List.of("items", "page", "totalPages", "total", "sampledAt", "truncated"),
            "items", array("当前页进程列表。", row),
            "page", field("integer", "实际返回页码，从 1 开始。"),
            "totalPages", field("integer", "采样并筛选后的总页数，至少为 1。"),
            "total", field("integer", "已采样且匹配搜索条件的进程数；采样截断时不代表系统全部匹配进程数。"),
            "sampledAt", field("integer", "快照采样时间，Unix 毫秒时间戳。"),
            "truncated", field("boolean", "是否因为 10000 个进程的采样上限而未包含全部可见进程。"));
    }

    private static Map<String, Object> serverInfo() {
        return object("当前 JVM 所处服务器/容器的基础状态，不是远程宿主机监控。平台不可用的扩展指标可能省略。",
            List.of("os", "arch", "javaVersion", "executionUser", "processors", "loadAverage", "pid", "netdiskPid", "disks", "network"),
            "os", field("string", "操作系统名称。"), "arch", field("string", "操作系统架构。"),
            "javaVersion", field("string", "当前 Java 运行时版本。"),
            "executionUser", field("string", "Java 进程的操作系统用户；命令执行继承其权限。"),
            "processors", field("integer", "JVM 可用处理器数量。"),
            "loadAverage", field("number", "系统最近一分钟平均负载，不是 CPU 百分比；不可用时为负数。"),
            "pid", field("integer", "当前 Java 应用的系统进程 ID。"),
            "netdiskPid", field("integer", "历史兼容字段，与 pid 相同，不表示一定运行网盘应用。"),
            "cpuLoad", field("number", "近期系统 CPU 使用率，范围通常为 0～1；负数表示不可用，平台不支持时省略。"),
            "memoryTotal", field("integer", "JVM 所处环境可报告的物理内存总量，单位字节；平台不支持时省略。"),
            "memoryFree", field("integer", "可报告的空闲物理内存，单位字节；平台不支持时省略。"),
            "swapTotal", field("integer", "可报告的交换空间总量，单位字节；平台不支持时省略。"),
            "disks", array("可访问的根文件系统容量，跳过不可读项，不保证枚举全部挂载点。",
                object("一个根文件系统。", List.of("path", "totalBytes", "usableBytes"),
                    "path", field("string", "服务器根路径，例如 / 或 Windows 盘符根目录。"),
                    "totalBytes", field("integer", "文件系统总容量，单位字节。"),
                    "usableBytes", field("integer", "当前用户可用容量，单位字节。"))),
            "network", array("可见网卡及地址列表，不包含实时带宽。",
                object("一个网卡。", List.of("name", "addresses"),
                    "name", field("string", "网络接口名称。"),
                    "addresses", array("该接口的 IPv4/IPv6 地址。", field("string", "IP 地址文本，IPv6 可能带作用域。")))));
    }

    private static Map<String, Object> field(String type, String description) {
        return Map.of("type", type, "description", description);
    }
    private static Map<String, Object> nullable(String type, String description) {
        return Map.of("type", List.of(type, "null"), "description", description);
    }
    private static Map<String, Object> values(String description, String... values) {
        return Map.of("type", "string", "description", description, "enum", List.of(values));
    }
    private static Map<String, Object> array(String description, Map<String, Object> item) {
        return Map.of("type", "array", "description", description, "items", item);
    }
    private static Map<String, Object> object(String description, List<String> required, Object... fields) {
        var properties = new LinkedHashMap<String, Object>();
        for (int i = 0; i < fields.length; i += 2) properties.put((String) fields[i], fields[i + 1]);
        return Map.of("type", "object", "description", description, "properties", properties,
            "required", required, "additionalProperties", false);
    }
}
