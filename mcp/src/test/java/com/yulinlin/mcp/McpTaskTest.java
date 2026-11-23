package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.McpCredentials;
import com.yulinlin.mcp.task.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class McpTaskTest extends McpTestSupport {
    @TempDir Path directory;
    MemoryStore store; McpCredentials credentials; McpCredentials.Credential credential;
    Policy policy; McpProperties properties; McpTasks tasks;

    @BeforeEach void setup() {
        store = new MemoryStore(); policy = new Policy(); properties = properties();
        properties.setDataDirectory(directory); properties.getCommand().setMaxOutputBytes(8);
        credentials = new McpCredentials(store, id -> ADMIN, policy); credentials.enable(true);
        credential = credentials.byId(credentials.create(ADMIN, "test").credential().id());
    }
    @AfterEach void close() { if (tasks != null) tasks.shutdown(); }
    @Test void credentialsExpiringAfterAdmissionCannotLaunchAProcess() throws Exception {
        var time=new McpExpirationTest.Time();var starts=new AtomicInteger();
        store=new MemoryStore(){
            @Override public void create(String key,String kind,Object value){
                super.create(key,kind,value);
                if(kind.equals("task"))time.now=2000;
            }
        };
        credentials=new McpCredentials(store,id->ADMIN,policy,time);credentials.enable(true);
        credential=credentials.byId(credentials.create(ADMIN,"temporary",2000L).credential().id());
        service((command,cwd)->{starts.incrementAndGet();return new FakeProcess("");});
        var task=tasks.start(credential,"never start",directory.toString(),0,null);
        awaitFinished(tasks,task.id());assertEquals("failed",tasks.get(task.id()).state());assertEquals(0,starts.get());
        assertThrows(McpException.class,()->tasks.start(credential,"expired",directory.toString(),0,null));
    }
    McpTasks service(CommandExecutor executor) {
        tasks = new McpTasks(store, credentials, properties, executor, policy); tasks.recover(); return tasks;
    }
    @Test void persistBeforeDispatchAndRequestIdIsIdempotentAcrossServiceInstances() throws Exception {
        var starts = new AtomicInteger(); var process = new FakeProcess("0123456789abcdefghijklmnopqrstuvwxyz");
        var service = service((command, cwd) -> {
            assertEquals(1, store.count("task")); starts.incrementAndGet(); return process;
        });
        var task = service.start(credential, "command", directory.toString(), 0, "request-1");
        assertEquals(task.id(), service.start(credential, "command", directory.toString(), 0, "request-1").id());
        process.finish(); awaitFinished(service, task.id());
        assertEquals(1, starts.get()); assertTrue(process.consumed.get() > 8);
        var output = service.output(task.id(), 0, 65536);
        assertEquals("01234567", output.text()); assertTrue(output.truncated());
        assertEquals(output.text(), new String(Base64.getDecoder().decode(output.base64())));
        assertEquals(409, assertThrows(McpException.class,
            () -> service.start(credential, "other", directory.toString(), 0, "request-1")).status());
        try {
            var another = new McpTasks(store, credentials, properties, (command, cwd) -> {
                fail("retry must never execute again"); return null;
            }, policy);
            another.recover();
            try { assertEquals(task.id(), another.start(credential, "command", directory.toString(), 0, "request-1").id()); }
            finally { another.shutdown(); }
        } finally { process.finish(); }
    }
    @Test void databaseAdmissionFailureMustNotStartProcess() {
        var starts = new AtomicInteger();
        var broken = new MemoryStore() {
            @Override public void create(String key, String kind, Object value) {
                if (kind.equals("task")) throw new IllegalStateException("database unavailable");
                super.create(key, kind, value);
            }
        };
        tasks = new McpTasks(broken, credentials, properties, (command, cwd) -> {
            starts.incrementAndGet(); return new FakeProcess("");
        }, policy); tasks.recover();
        assertThrows(IllegalStateException.class, () -> tasks.start(credential,"x",directory.toString(),1,null));
        assertEquals(0, starts.get());
    }
    @Test void failedOutputFileStillDrainsTheProcess() throws Exception {
        var process=new FakeProcess("x".repeat(1000));
        Path outputIsDirectory=Files.createDirectory(directory.resolve("output-is-a-directory"));
        tasks=new McpTasks(store,credentials,properties,(command,cwd)->process,policy,id->outputIsDirectory);
        tasks.recover();var task=tasks.start(credential,"output",directory.toString(),0,null);
        process.finish();awaitFinished(tasks,task.id());
        assertEquals("succeeded",tasks.get(task.id()).state());
        assertEquals(1000,process.consumed.get());assertTrue(tasks.get(task.id()).outputTruncated());
    }
    @Test void concurrentRequestsAcrossInstancesOnlyDispatchOneCommand() throws Exception {
        var process=new FakeProcess("output");var starts=new AtomicInteger();
        CommandExecutor executor=(command,cwd)->{starts.incrementAndGet();return process;};
        var first=service(executor);var secondProperties=properties();secondProperties.setInstanceId("second");secondProperties.setDataDirectory(directory);
        var second=new McpTasks(store,credentials,secondProperties,executor,policy);second.recover();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var barrier=new CountDownLatch(1);
            var a=pool.submit(()->{barrier.await();return first.start(credential,"same",directory.toString(),0,"retry");});
            var b=pool.submit(()->{barrier.await();return second.start(credential,"same",directory.toString(),0,"retry");});
            barrier.countDown();var one=a.get(5,TimeUnit.SECONDS);var two=b.get(5,TimeUnit.SECONDS);
            assertEquals(one.id(),two.id());process.finish();
            awaitFinished("local".equals(one.instanceId())?first:second,one.id());
            assertEquals(1,starts.get());assertEquals(1,store.count("task"));
        }finally{process.finish();second.shutdown();}
    }
    @Test void customResolverCanReadLegacyOutputWithoutMovingFiles() throws Exception {
        String id="c".repeat(32);Path legacy=directory.resolve(id+".log");Files.writeString(legacy,"legacy output");
        store.create("task-"+id,"task",old(id,"local"));
        tasks=new McpTasks(store,credentials,properties,(command,cwd)->{fail("must not restart");return null;},policy,key->directory.resolve(key+".log"));
        tasks.recover();assertEquals("legacy output",tasks.output(id,0,100).text());
    }
    @Test void timeoutsAndCancellationAreTrackedWithoutKillingUnrelatedProcesses() throws Exception {
        var processes = new CopyOnWriteArrayList<FakeProcess>();
        var service = service((command, cwd) -> {var p = new FakeProcess("output"); processes.add(p); return p;});
        var timed = service.start(credential, "slow", directory.toString(), 1, null);
        awaitFinished(service, timed.id()); assertEquals("timed_out", service.get(timed.id()).state());
        var cancelled = service.start(credential,"cancel",directory.toString(),0,null);
        service.cancel(cancelled.id()); awaitFinished(service,cancelled.id());
        assertEquals("cancelled",service.get(cancelled.id()).state());
        assertTrue(processes.stream().noneMatch(Process::isAlive));
    }
    @Test void restartOnlyMarksLocalActiveTasksUnknownAndNeverReruns() {
        String local="a".repeat(32), remote="b".repeat(32);
        store.create("task-"+local,"task",old(local,"local"));
        store.create("task-"+remote,"task",old(remote,"other"));
        var service = service((command,cwd)->{fail("recovery must not execute");return null;});
        assertEquals("unknown",service.get(local).state());
        assertEquals("running",service.get(remote).state());
        assertThrows(IllegalArgumentException.class,()->service.cancel(local));
        assertEquals(409,assertThrows(McpException.class,()->service.output(remote,0,10)).status());
    }
    McpTasks.Task old(String id,String instance) {
        return new McpTasks.Task(id,credential.id(),"old",directory.toString(),"running",1L,2L,null,
            12345L,null,0,null,false,instance,"previous-run");
    }
    static void awaitFinished(McpTasks service,String id) throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(8),()->{
            while(service.get(id).finishedAt()==null)Thread.sleep(10);
        });
    }
    static class FakeProcess extends Process {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger consumed = new AtomicInteger();
        final InputStream input;
        FakeProcess(String output) {
            input = new ByteArrayInputStream(output.getBytes()) {
                @Override public synchronized int read(byte[] b,int off,int len) {
                    int n = super.read(b,off,len); if(n>0) consumed.addAndGet(n); return n;
                }
            };
        }
        void finish(){done.countDown();}
        public OutputStream getOutputStream(){return OutputStream.nullOutputStream();}
        public InputStream getInputStream(){return input;}
        public InputStream getErrorStream(){return InputStream.nullInputStream();}
        public int waitFor() throws InterruptedException {done.await();return 0;}
        public boolean waitFor(long timeout,TimeUnit unit)throws InterruptedException{return done.await(timeout,unit);}
        public int exitValue(){if(isAlive())throw new IllegalThreadStateException();return 0;}
        public void destroy(){finish();} public Process destroyForcibly(){finish();return this;}
        public boolean isAlive(){return done.getCount()!=0;}public long pid(){return 987654321L;}
        public Stream<ProcessHandle> descendants(){return Stream.empty();}
    }
}
