package com.ligero.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageCommandTest {

    @TempDir
    Path dir;

    @Test
    void failsOutsideAProject() {
        assertThat(LigeroCli.run(dir, "image")).isEqualTo(1);
    }

    @Test
    void buildsThePackCommandWithSafeDefaults() throws IOException {
        Path project = Files.createDirectories(dir.resolve("My Shop_API"));
        ImageCommand.Options options = ImageCommand.parse(project, List.of());
        assertThat(options.name()).isEqualTo("my-shop_api");
        assertThat(ImageCommand.packCommand(project, options)).containsExactly(
            "pack", "build", "my-shop_api:latest",
            "--builder", ImageCommand.DEFAULT_BUILDER,
            "--path", ".",
            "--env", "BP_JVM_VERSION=21",
            "--env", "BP_GRADLE_BUILD_ARGUMENTS=--no-daemon -x test distZip",
            "--env", "BP_GRADLE_BUILT_ARTIFACT=build/distributions/*.zip");
    }

    @Test
    void publishTagAndSbom() {
        ImageCommand.Options options = ImageCommand.parse(dir, List.of(
            "--name", "registry.example.com:5000/shop/api", "--tag", "1.4.0", "--publish", "--sbom", "build/sbom",
            "--builder", "paketobuildpacks/builder-noble-java-tiny:0.0.1"));
        List<String> command = ImageCommand.packCommand(dir, options);
        assertThat(command).contains("registry.example.com:5000/shop/api:1.4.0", "--publish",
            "--sbom-output-dir", "build/sbom", "paketobuildpacks/builder-noble-java-tiny:0.0.1");
    }

    @Test
    void refusesNamesThatAreNotImageReferences() {
        for (String bad : new String[] {"Upper", "a b", "x;rm -rf /", "-flag", "a//b", "../x"}) {
            assertThatThrownBy(() -> ImageCommand.parse(dir, List.of("--name", bad))).as(bad)
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> ImageCommand.parse(dir, List.of("--tag", "-bad"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageCommand.parse(dir, List.of("--builder", "Bad Builder")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageCommand.parse(dir, List.of("--tag"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageCommand.parse(dir, List.of("--wat"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultNamesAreSanitized() {
        assertThat(ImageCommand.defaultName(Path.of("/tmp/__Weird..Name--"))).isEqualTo("weird-name");
        assertThat(ImageCommand.defaultName(Path.of("/tmp/!!!"))).isEqualTo("app");
    }

    @Test
    void dryRunPrintsTheCommand() throws IOException {
        Files.writeString(dir.resolve("build.gradle"), "");
        PrintStream original = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true));
        try {
            assertThat(LigeroCli.run(dir, "image", "--name", "shop", "--dry-run")).isZero();
        } finally {
            System.setOut(original);
        }
        assertThat(out.toString()).startsWith("pack build shop:latest --builder");
    }

    @Test
    void withoutPackItPointsToTheDockerfile() throws IOException {
        Files.writeString(dir.resolve("build.gradle"), "");
        if (ImageCommand.findOnPath("pack") == null) {
            assertThat(LigeroCli.run(dir, "image", "--name", "shop")).isEqualTo(1);
        }
        assertThat(ImageCommand.findOnPath("definitely-not-a-program-xyz")).isNull();
    }
}
