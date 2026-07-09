package com.ligero.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DevCommandTest {

    @TempDir
    Path dir;

    @Test
    void failsOutsideAProject() {
        assertThat(LigeroCli.run(dir, "dev")).isEqualTo(1); // no build.gradle
    }

    @Test
    void prefersTheGradleWrapperWhenPresent() throws IOException {
        Files.writeString(dir.resolve("gradlew"), "#!/bin/sh\n");
        List<String> command = DevCommand.gradleCommand(dir);
        assertThat(command.get(0)).endsWith("gradlew");
        assertThat(command).contains("run", "-q", "--console=plain");
    }

    @Test
    void fallsBackToSystemGradle() {
        List<String> command = DevCommand.gradleCommand(dir); // no wrapper
        assertThat(command.get(0)).isIn("gradle", "gradle.bat");
        assertThat(command).contains("run");
    }
}
