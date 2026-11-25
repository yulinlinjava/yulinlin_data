package com.yulinlin.mcp.process;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

/** Display parsing only: no ps execution, process signals or application startup. */
class ProcessDetailsReaderTest {
    @Test void jarDirectoryOwnerAndOriginalParametersIdentifyTheService() {
        var details = describe("java", "java", "-Xmx1800m", "-jar", "./bot.jar", "--spring.profiles.active=dev",
                "--server.port=86", "--password=example-password", "--token", "example-token");
        assertThat(details.application()).isEqualTo("bot.jar");
        assertThat(details.launchTarget()).isEqualTo("./bot.jar");
        assertThat(details.workingDirectory()).isEqualTo("/srv/bot");
        assertThat(details.user()).isEqualTo("root"); assertThat(details.parentPid()).isEqualTo(42);
        assertThat(details.commandLine()).contains("-Xmx1800m", "--spring.profiles.active=dev", "--server.port=86",
                "--password=example-password", "example-token");
        assertThat(details.commandTruncated()).isFalse();
    }
    @Test void classpathModulesAndWrapperProcessesRetainTheirActualTargets() {
        assertThat(describe("java", "java", "-cp", "/srv/lib/*", "com.example.Main", "--port=8080").application())
                .isEqualTo("com.example.Main");
        assertThat(describe("java", "java", "--module-path", "/srv/mods", "-m", "example/com.example.Main").launchTarget())
                .isEqualTo("example/com.example.Main");
        assertThat(describe("sudo", "sudo", "nohup", "/usr/bin/java", "-jar", "./my app.jar").application())
                .isEqualTo("my app.jar");
        assertThat(describe("java", "java", "-jar", "./my app.jar").commandLine()).contains("'./my app.jar'");
    }
    @Test void missingOrOversizedMetadataDoesNotInventPathsOrReturnAnUnboundedCommand() {
        var missing = ProcessDetailsReader.describe("java", null, null, null, null, new byte[0]);
        assertThat(missing.application()).isEqualTo("java"); assertThat(missing.commandLine()).isNull();
        assertThat(missing.workingDirectory()).isNull();
        var longCommand = describe("java", "java", "-jar", "./bot.jar", "x".repeat(ProcessDetailsReader.MAX_INPUT_BYTES));
        assertThat(longCommand.commandTruncated()).isTrue();
        assertThat(longCommand.commandLine()).hasSizeLessThanOrEqualTo(ProcessDetailsReader.MAX_COMMAND_CHARS + 2);
        assertThat(longCommand.application()).isEqualTo("bot.jar");
    }
    @Test void psRowsPreserveFullArgumentsAndIgnoreMalformedRows() {
        String text = " 169221 169220 root                             java -Xmx1800m -jar ./bot.jar --server.port=86 --password=example\n"
                + " 921469 921468 app                              /usr/bin/java -jar /srv/91api.jar --spring.profiles.active=ns\n"
                + "not a process row\n";
        var rows = ProcessPsReader.parse(text.getBytes(StandardCharsets.UTF_8));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(169221L).parentPid()).isEqualTo(169220);
        assertThat(rows.get(169221L).user()).isEqualTo("root");
        assertThat(rows.get(169221L).commandLine()).contains("./bot.jar", "--server.port=86", "--password=example");
        assertThat(rows.get(921469L).commandLine()).contains("/srv/91api.jar");
    }
    private static ProcessDetailsReader.Details describe(String name, String... args) {
        return ProcessDetailsReader.describe(name, "/usr/bin/java", "root", 42L, "/srv/bot",
                (String.join("\0", args) + "\0").getBytes(StandardCharsets.UTF_8));
    }
}

