package com.yulinlin.mcp.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded display metadata; command parameters are intentionally shown to the authenticated administrator. */
final class ProcessDetailsReader {
    static final int MAX_INPUT_BYTES = 16_384, MAX_COMMAND_CHARS = 16_384, MAX_ARGUMENTS = 128;
    private static final Pattern SAFE_WORD = Pattern.compile("[\\p{Alnum}_./:@%+=,\\-]+");
    private static final Set<String> JAVA_VALUE_OPTIONS = Set.of("-cp", "-classpath", "--class-path", "-p", "--module-path",
            "--upgrade-module-path", "--add-modules", "--limit-modules", "--add-exports", "--add-opens", "--add-reads", "--patch-module", "--enable-native-access");

    private ProcessDetailsReader() { }

    static Details read(ProcessHandle handle, ProcessHandle.Info info, String name, ProcessPsReader.Command ps) {
        long pid = handle.pid();
        String executable = info.command().orElseGet(() -> link(pid, "exe"));
        Long parentPid;
        try { parentPid = handle.parent().map(ProcessHandle::pid).orElse(null); }
        catch (RuntimeException error) { parentPid = null; }
        byte[] bytes;
        try (var input = Files.newInputStream(Path.of("/proc", Long.toString(pid), "cmdline"))) {
            bytes = input.readNBytes(MAX_INPUT_BYTES + 1);
        } catch (IOException | RuntimeException error) { bytes = new byte[0]; }
        var details = describe(name, executable, ps == null ? info.user().orElse(null) : ps.user(),
                ps == null ? parentPid : ps.parentPid(), link(pid, "cwd"), bytes);
        if (ps == null || ps.commandLine().isBlank()) return details;
        boolean truncated = ps.commandLine().length() > MAX_COMMAND_CHARS;
        return new Details(details.application(), details.executable(), details.user(), details.parentPid(), details.workingDirectory(),
                details.launchTarget(), clean(ps.commandLine(), MAX_COMMAND_CHARS) + (truncated ? " …" : ""), truncated);
    }

    static Details describe(String name, String executable, String user, Long parentPid, String cwd, byte[] bytes) {
        var arguments = arguments(bytes);
        var displayArguments = arguments.items().stream().map(value -> clean(value, MAX_INPUT_BYTES)).toList();
        String target = javaTarget(name, displayArguments);
        String application = target == null ? name : basename(target);
        String command = displayArguments.isEmpty() ? null : String.join(" ", displayArguments.stream().map(ProcessDetailsReader::quote).toList());
        boolean truncated = arguments.truncated() || (command != null && command.length() > MAX_COMMAND_CHARS);
        if (command != null) command = clean(command, MAX_COMMAND_CHARS) + (truncated ? " …" : "");
        return new Details(clean(application, 512), clean(executable, 4096), clean(user, 256), parentPid,
                clean(cwd, 4096), clean(target, 4096), command, truncated);
    }

    private static Arguments arguments(byte[] bytes) {
        int limit = Math.min(bytes.length, MAX_INPUT_BYTES), start = 0;
        boolean truncated = bytes.length > MAX_INPUT_BYTES;
        var items = new ArrayList<String>();
        for (int index = 0; index < limit; index++) {
            if (bytes[index] != 0) continue;
            if (items.size() == MAX_ARGUMENTS) return new Arguments(items, true);
            items.add(new String(bytes, start, index - start, StandardCharsets.UTF_8)); start = index + 1;
        }
        // Avoid presenting a partially read final argument as complete.
        if (start < limit && !truncated) {
            if (items.size() < MAX_ARGUMENTS) items.add(new String(bytes, start, limit - start, StandardCharsets.UTF_8));
            else truncated = true;
        }
        return new Arguments(items, truncated);
    }

    private static String quote(String value) {
        return SAFE_WORD.matcher(value).matches() ? value : "'" + value.replace("'", "'\"'\"'") + "'";
    }
    private static String javaTarget(String name, List<String> arguments) {
        int java = -1;
        for (int index = 0; index < arguments.size(); index++) {
            String base = basename(arguments.get(index));
            if (base.equals("java") || base.equals("java.exe")) { java = index; break; }
        }
        if (java < 0 && !(name.equals("java") || name.equals("java.exe"))) return null;
        if (java < 0) java = 0;
        for (int index = java + 1; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            if ((argument.equals("-jar") || argument.equals("-m") || argument.equals("--module")) && index + 1 < arguments.size())
                return arguments.get(index + 1);
            if (argument.startsWith("--module=")) return argument.substring("--module=".length());
            if (JAVA_VALUE_OPTIONS.contains(argument)) { index++; continue; }
            if (argument.startsWith("@")) return null; // Do not read or execute argument files.
            if (!argument.startsWith("-")) return argument;
        }
        return null;
    }
    private static String basename(String value) {
        String normalized = value.replace('\\', '/'); return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
    private static String clean(String value, int limit) {
        if (value == null) return null;
        var text = new StringBuilder(Math.min(value.length(), limit));
        for (int index = 0; index < value.length() && text.length() < limit; index++) {
            char ch = value.charAt(index);
            text.append(Character.isISOControl(ch) ? ' ' : ch);
        }
        return text.toString();
    }
    private static String link(long pid, String name) {
        try { return Files.readSymbolicLink(Path.of("/proc", Long.toString(pid), name)).toString(); }
        catch (IOException | RuntimeException error) { return null; }
    }
    private record Arguments(List<String> items, boolean truncated) { }
    record Details(String application, String executable, String user, Long parentPid, String workingDirectory,
                   String launchTarget, String commandLine, boolean commandTruncated) { }
}
