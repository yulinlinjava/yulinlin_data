package com.yulinlin.mcp.task;
import com.yulinlin.mcp.*;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.McpStore;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Tasks belong to the server, not the HTTP connection. No stdin or PTY is attached. */

public class McpTasks {
    private final long outputLimit;
 private final String runId=McpStore.id();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpTasks.class);
    private final McpStore store;
    private final McpAuthentication authentication;
    private final McpProperties properties;
 private final CommandExecutor executor;
 private final McpAccessPolicy policy;
 private final McpTaskOutputResolver outputs;
    
    private final ConcurrentMap<String, Running> running = new ConcurrentHashMap<>();
    private final Semaphore slots;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean ready, stopping;
    public McpTasks(McpStore store,McpAuthentication authentication,McpProperties properties,CommandExecutor executor,McpAccessPolicy policy){this(store,authentication,properties,executor,policy,id->properties.getDataDirectory().toAbsolutePath().normalize().resolve(properties.getApplicationId()).resolve(properties.getInstanceId()).resolve("output").resolve(id+".log"));}
    public McpTasks(McpStore store,McpAuthentication authentication,McpProperties properties,CommandExecutor executor,McpAccessPolicy policy,McpTaskOutputResolver outputs){this.outputs=Objects.requireNonNull(outputs);this.policy=policy;this.store=store;this.authentication=authentication;this.properties=properties;this.executor=executor;this.slots=new Semaphore(properties.getCommand().getMaxConcurrent());this.outputLimit=properties.getCommand().getMaxOutputBytes();}
    public void recover() {
        if(ready)return;
        for (int page=1;page<=40;page++) {
            var batch=store.list("task",page,50,Task.class);
            for (Task task : batch) {
                if (local(task) && !runId.equals(task.runId()) && Set.of("queued", "running", "cancelling").contains(task.state())) {
                    // Never re-run a command or signal a recovered PID: it may have been reused.
                    save(new Task(task.id(), task.credentialId(), task.command(), task.cwd(), "unknown", task.createdAt(), task.startedAt(),
                            System.currentTimeMillis(), task.pid(), null, task.timeoutSeconds(), "应用已重启，进程状态未知；请通过进程列表核对", task.outputTruncated(), properties.getInstanceId(), runId));
                }
            }
            if(batch.size()<50) break;
        }
        ready = true;
    }
    public synchronized Task start(McpPrincipal principal, String command, String cwd, int timeoutSeconds, String requestId) throws IOException {
        if(!policy.canInvoke(authentication.refreshTool(principal,"command_execute",false),"command_execute"))throw new McpException(403,"无 command_execute 权限");
        if (!ready || stopping) throw new McpException(503, "任务服务尚未就绪");

        if (command == null || command.isBlank() || command.indexOf('\0') >= 0
                || command.getBytes(StandardCharsets.UTF_8).length > properties.getCommand().getMaxCommandBytes())
            throw new IllegalArgumentException("命令超过配置的 UTF-8 字节上限");
        if (timeoutSeconds < 0 || timeoutSeconds > 604800) throw new IllegalArgumentException("超时为 0（不限时）或 1–604800 秒");
        Path directory = cwd == null || cwd.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(cwd);
        if (!directory.isAbsolute() || !Files.isDirectory(directory)) throw new IllegalArgumentException("工作目录必须是已有绝对路径");
        directory = directory.toRealPath();
        command = command.replace("\r\n", "\n");
        if (requestId != null && !requestId.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("requestId 限 1–128 位字母、数字、下划线或短横线");
        String id = requestId == null ? McpStore.id() : UUID.nameUUIDFromBytes((principal.credentialId()+":"+requestId).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        if (requestId != null) {
            var existing = store.get("task-"+id, Task.class);
            if (existing != null) {
                if (!existing.command().equals(command) || !existing.cwd().equals(directory.toString()) || existing.timeoutSeconds()!=timeoutSeconds)
                    throw new McpException(409,"requestId 已用于不同命令，请换一个编号");
                return get(id);
            }
        }
        if (!slots.tryAcquire()) throw new McpException(429, "MCP 并发任务已满");
        var task = new Task(id, principal.credentialId(), command, directory.toString(), "queued", System.currentTimeMillis(),
                null, null, null, null, timeoutSeconds, null, false, properties.getInstanceId(), runId);
        var item = new Running(task); running.put(id, item);
        boolean created=false;
        try {
            if (store.count("task") >= 2000) throw new McpException(429, "任务记录已满，请清理完成的任务");
            store.create("task-"+id,"task",task);created=true;
            workers.submit(() -> execute(item, principal));
        } catch (RuntimeException error) {
            running.remove(id); slots.release();
            // If the worker was never dispatched, a retained queued row would incorrectly look runnable.
            if(created){save(new Task(task.id(),task.credentialId(),task.command(),task.cwd(),"failed",task.createdAt(),null,System.currentTimeMillis(),null,null,task.timeoutSeconds(),"任务未能派发",false,properties.getInstanceId(),runId));throw error;}
            var existing=store.get("task-"+id,Task.class);
            if(requestId!=null && existing!=null && existing.command().equals(command)&&existing.cwd().equals(directory.toString())&&existing.timeoutSeconds()==timeoutSeconds) return existing;
            if(existing!=null && requestId!=null)throw new McpException(409,"requestId 已用于不同命令");
            throw error;
        }
        return task;
    }
    private void execute(Running item, McpPrincipal principal) {
        Future<?> reader = null;
        String state = "failed", message = null; Integer exit = null;
        try {
            synchronized (item) {
                if (!policy.canInvoke(authentication.refreshTool(principal,"command_execute",false), "command_execute"))
                    throw new McpException(403, "无 command_execute 权限");
                if (item.cancel) { state = "cancelled"; return; }
                Path output = outputPath(item.task.id()); Files.createDirectories(output.getParent());
                if(Files.getFileStore(output.getParent()).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(output.getParent(), java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
                // -c avoids temporary script files, stdin is closed, no request-scoped cleanup can kill this process.
                item.process = executor.start(item.task.command(),Path.of(item.task.cwd()));
                item.process.getOutputStream().close();
                var old = item.task;
                item.task = new Task(old.id(), old.credentialId(), old.command(), old.cwd(), "running", old.createdAt(),
                        System.currentTimeMillis(), null, item.process.pid(), null, old.timeoutSeconds(), null, false, properties.getInstanceId(), runId);
                save(item.task);
                reader = workers.submit(() -> drain(item, output));
            }
            boolean timedOut = item.task.timeoutSeconds() != 0 && !item.process.waitFor(item.task.timeoutSeconds(), TimeUnit.SECONDS);
            if (timedOut) terminate(item, true);
            else if (item.task.timeoutSeconds() == 0) item.process.waitFor();
            if (item.process.isAlive()) item.process.waitFor(5, TimeUnit.SECONDS);
            exit = item.process.isAlive() ? null : item.process.exitValue();
            state = timedOut ? "timed_out" : item.cancel ? "cancelled" : exit != null && exit == 0 ? "succeeded" : "failed";
            if (reader != null) {
                try { reader.get(2, TimeUnit.SECONDS); }
                catch (TimeoutException ignored) { /* Detached descendants may keep stdout open; close it when tracking ends. */ }
            }
        } catch (Exception error) {
            message = error instanceof InterruptedException ? "执行监控中断" : "执行失败：" + error.getMessage();
            if (item.process != null && item.process.isAlive()) terminate(item, true);
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            if (item.process != null) try { item.process.getInputStream().close(); } catch (IOException ignored) { }
            if (reader != null) reader.cancel(true);
            synchronized (item) {
                Task old = item.task;
                item.task = new Task(old.id(), old.credentialId(), old.command(), old.cwd(), state, old.createdAt(), old.startedAt(),
                        System.currentTimeMillis(), old.pid(), exit, old.timeoutSeconds(), message, item.truncated, properties.getInstanceId(), runId);
                try { save(item.task); }
                catch (RuntimeException error) { log.error("MCP 任务结果持久化失败 task={}", old.id(), error); }
            }
            slots.release();
            // Keep only the live task in memory; results and bounded output are persisted.
            running.remove(item.task.id());
        }
    }
    private void drain(Running item,Path output){
        long written=0;OutputStream sink=OutputStream.nullOutputStream();
        try{sink=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW,LinkOption.NOFOLLOW_LINKS);}catch(IOException e){item.truncated=true;}
        try(var input=item.process.getInputStream()){
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))>=0){int keep=(int)Math.min(count,Math.max(0,outputLimit-written));
                if(keep>0){try{sink.write(buffer,0,keep);sink.flush();}catch(IOException e){item.truncated=true;try{sink.close();}catch(IOException ignored){}sink=OutputStream.nullOutputStream();}written+=keep;}
                if(keep<count)item.truncated=true;
            }
        }catch(IOException e){item.truncated=true;}finally{try{sink.close();}catch(IOException ignored){}}
    }
    public Task get(String id) {
        validateId(id); var current = running.get(id);
        var task = current == null ? store.get("task-" + id, Task.class) : current.task;
        if (task == null) throw new IllegalArgumentException("任务不存在"); return task;
    }
    public List<Task> list(int page) {
        return store.list("task", page, 30, Task.class).stream().map(task -> {
            var item = running.get(task.id()); return item == null ? task : item.task;
        }).toList();
    }
    public Output output(String id, long offset, int limit) throws IOException {
        Task task = get(id);
        requireLocal(task);
        if (offset < 0 || limit < 1 || limit > 65536) throw new IllegalArgumentException("输出偏移必须非负，每次最多读取 64 KiB");
        Path file = outputPath(id);
        if (!Files.exists(file)) return new Output("", "", 0, false, task.outputTruncated());
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long size = input.length(); if (offset > size) throw new IllegalArgumentException("输出偏移超出范围");
            input.seek(offset); byte[] bytes = new byte[(int) Math.min(limit, size - offset)]; input.readFully(bytes);
            // Base64 preserves split UTF-8 sequences for clients combining byte-oriented pages.
            var active = running.get(id);
            return new Output(new String(bytes, StandardCharsets.UTF_8), Base64.getEncoder().encodeToString(bytes), offset + bytes.length,
                    offset + bytes.length < size, task.outputTruncated() || (active != null && active.truncated));
        }
    }
    public Task cancel(String id) {
        requireLocal(get(id)); var item = running.get(id);
        if (item == null) throw new IllegalArgumentException("该任务已结束或由旧实例创建，请通过进程列表核对");
        synchronized (item) {
            if (!Set.of("queued", "running", "cancelling").contains(item.task.state())) return item.task;
            item.cancel = true;
            if (item.process != null && item.process.isAlive()) terminate(item, false);
            var old = item.task;
            item.task = new Task(old.id(),old.credentialId(),old.command(),old.cwd(),"cancelling",old.createdAt(),old.startedAt(),
                    old.finishedAt(),old.pid(),old.exitCode(),old.timeoutSeconds(),old.message(),item.truncated, properties.getInstanceId(), runId);
            save(item.task);
        }
        return item.task;
    }
    private void terminate(Running item, boolean force) {
        var process = item.process;
        if (process == null) return;
        var descendants = process.descendants().limit(10000).toList();
        descendants.reversed().forEach(handle -> { if (force) handle.destroyForcibly(); else handle.destroy(); });
        if (force) process.destroyForcibly(); else process.destroy();
        if (!force) workers.submit(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            descendants.reversed().forEach(handle -> { if (handle.isAlive()) handle.destroyForcibly(); });
            if (process.isAlive()) process.destroyForcibly();
        });
    }
    public void delete(String id) throws IOException {
        Task task = get(id);
        requireLocal(task);
        if (Set.of("queued","running","cancelling").contains(task.state())) throw new IllegalArgumentException("运行中的任务不能删除");
        if (running.containsKey(id)) throw new IllegalArgumentException("运行中的任务不能删除记录，请先取消");
        Files.deleteIfExists(outputPath(task.id())); store.delete("task-" + task.id());
    }
    public void clean() {
        if (!ready) return;
        long before = System.currentTimeMillis() - properties.getRetentionDays()*86_400_000L;
        var expired = new ArrayList<String>();
        for (int page=1;page<=40;page++) {
            var batch=store.list("task",page,50,Task.class);
            for(var task:batch) {
                if(local(task) && task.finishedAt()!=null && task.finishedAt()<before && !running.containsKey(task.id())) expired.add(task.id());
            }
            if(batch.size()<50) break;
        }
        for(var id:expired) {
            try { delete(id); } catch (IOException | IllegalArgumentException error) { log.warn("MCP 输出清理失败 task={}", id); }
        }
    }
    @PreDestroy public void shutdown() {
        stopping = true;
        // Server restart is not task cancellation. The next instance marks untracked commands unknown.
        workers.shutdown();
    }
    private boolean local(Task task){return properties.getInstanceId().equals(task.instanceId())||(task.instanceId()==null&&properties.getInstanceId().equals("local"));}
    private void requireLocal(Task task){if(!local(task))throw new McpException(409,"请访问任务所属实例: "+task.instanceId());}
    private void save(Task task) { store.save("task-" + task.id(), "task", task); }
    private Path outputPath(String id) { validateId(id); return outputs.resolve(id).toAbsolutePath().normalize(); }
    private static void validateId(String id) { if (id == null || !id.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("任务编号无效"); }
    private static final class Running {
        volatile Task task; Process process; volatile boolean cancel, truncated;
        Running(Task task) { this.task = task; }
    }
    public record Task(String id, String credentialId, String command, String cwd, String state, long createdAt, Long startedAt,
                       Long finishedAt, Long pid, Integer exitCode, int timeoutSeconds, String message, boolean outputTruncated, String instanceId, String runId) {
        @Override public String toString() { return "McpTask[id=" + id + ",state=" + state + "]"; }
    }
    public record Output(String text, String base64, long nextOffset, boolean hasMore, boolean truncated) { }
}
