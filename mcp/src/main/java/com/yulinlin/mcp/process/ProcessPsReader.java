package com.yulinlin.mcp.process;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Fixed ps arguments; filtering never enters a shell command. Output stays in memory. */
final class ProcessPsReader {
    static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final Pattern ROW = Pattern.compile("^\\s*(\\d+)\\s+(\\d+)\\s+(\\S+)\\s+(.*)$");
    private ProcessPsReader() { }

    static Map<Long, Command> read() {
        Process process = null; FutureTask<byte[]> output = null;
        try {
            process = new ProcessBuilder("ps", "-ww", "-eo", "pid=,ppid=,user:32=,args=")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            var stream = process.getInputStream();
            output = new FutureTask<>(() -> { try (stream) { return stream.readNBytes(MAX_BYTES + 1); } });
            Thread.ofVirtual().name("process-ps-output").start(output);
            byte[] bytes = output.get(3, TimeUnit.SECONDS);
            return parse(bytes);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); return Map.of();
        } catch (Exception error) {
            // Minimal containers may lack ps; /proc still supplies the per-process fallback.
            return Map.of();
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly(); // Only our short-lived ps helper.
            if (output != null) output.cancel(true);
        }
    }

    static Map<Long, Command> parse(byte[] bytes) {
        String text = new String(bytes, 0, Math.min(bytes.length, MAX_BYTES), StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            int lastLine = text.lastIndexOf('\n'); text = lastLine < 0 ? "" : text.substring(0, lastLine);
        }
        var commands = new HashMap<Long, Command>();
        try (var lines = text.lines()) {
            var iterator = lines.iterator();
            while (iterator.hasNext() && commands.size() < 10_000) {
                var match = ROW.matcher(iterator.next());
                if (!match.matches()) continue;
                try {
                    long pid = Long.parseLong(match.group(1)), parent = Long.parseLong(match.group(2));
                    commands.put(pid, new Command(parent, match.group(3), match.group(4)));
                } catch (NumberFormatException ignored) { }
            }
        }
        return commands;
    }
    record Command(long parentPid, String user, String commandLine) { }
}
