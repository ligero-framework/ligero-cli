package com.ligero.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@code ligero image}: builds an OCI container image of the project with
 * Cloud Native Buildpacks (the {@code pack} CLI and the Paketo Java
 * buildpacks) — no Dockerfile to maintain, a patched JRE and OS layer on
 * every rebuild, and a software bill of materials (SBOM) in the image.
 *
 * <pre>
 * ligero image                                 # image named after the project, tag latest
 * ligero image --name registry.example.com/shop/api --tag 1.4.0 --publish
 * ligero image --sbom build/sbom               # also write the SBOM files
 * ligero image --dry-run                       # print the pack command only
 * </pre>
 *
 * <p>The buildpacks run the project's Gradle build ({@code distZip}) and
 * start the app with the generated start script on Java 21. Without
 * {@code pack} installed, the generated {@code Dockerfile} still works:
 * {@code docker build -t <name> .}</p>
 */
final class ImageCommand {

    static final String DEFAULT_BUILDER = "paketobuildpacks/builder-noble-java-tiny";

    /** Docker image references: [registry[:port]/]path[:tag] — no spaces, no shell characters. */
    private static final Pattern IMAGE_NAME =
        Pattern.compile("[a-z0-9]+([._-][a-z0-9]+)*(:[0-9]{1,5})?(/[a-z0-9]+([._-][a-z0-9]+)*)*");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern BUILDER = Pattern.compile("[a-z0-9][a-z0-9._/:@-]{0,254}");

    record Options(String name, String tag, String builder, boolean publish, Path sbom, boolean dryRun) {
    }

    int run(Path workingDir, List<String> args) throws IOException {
        if (!Files.exists(workingDir.resolve("build.gradle")) && !Files.exists(workingDir.resolve("build.gradle.kts"))) {
            throw new IllegalArgumentException("No build.gradle here — run 'ligero image' inside a Ligero project.");
        }
        Options options = parse(workingDir, args);
        List<String> command = packCommand(workingDir, options);
        if (options.dryRun()) {
            System.out.println(String.join(" ", command));
            return 0;
        }
        if (findOnPath("pack") == null) {
            System.err.println("""
                'pack' (Cloud Native Buildpacks) is not installed: https://buildpacks.io/docs/for-platform-operators/how-to/integrate-ci/pack/
                Or build with the project's Dockerfile instead:
                  docker build -t %s:%s .""".formatted(options.name(), options.tag()));
            return 1;
        }
        System.out.println("ligero image — " + String.join(" ", command));
        Process process = new ProcessBuilder(command).directory(workingDir.toFile()).inheritIO().start();
        try {
            int exit = process.waitFor();
            if (exit == 0) {
                System.out.println("\nImage ready: " + options.name() + ":" + options.tag()
                    + (options.publish() ? " (pushed)" : "")
                    + "\nRun it:      docker run --rm -p 8080:8080 " + options.name() + ":" + options.tag());
            }
            return exit;
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    static Options parse(Path workingDir, List<String> args) {
        String name = defaultName(workingDir);
        String tag = "latest";
        String builder = DEFAULT_BUILDER;
        boolean publish = false;
        Path sbom = null;
        boolean dryRun = false;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            switch (arg) {
                case "--name" -> name = value(args, ++i, arg);
                case "--tag" -> tag = value(args, ++i, arg);
                case "--builder" -> builder = value(args, ++i, arg);
                case "--sbom" -> sbom = Path.of(value(args, ++i, arg));
                case "--publish" -> publish = true;
                case "--dry-run" -> dryRun = true;
                default -> throw new IllegalArgumentException("Unknown option for 'ligero image': " + arg);
            }
        }
        if (!IMAGE_NAME.matcher(name).matches() || name.length() > 255) {
            throw new IllegalArgumentException("Not a valid image name: " + name
                + " (lowercase letters, digits, '.', '_', '-' and '/', e.g. registry.example.com/team/app)");
        }
        if (!TAG.matcher(tag).matches()) {
            throw new IllegalArgumentException("Not a valid image tag: " + tag);
        }
        if (!BUILDER.matcher(builder).matches()) {
            throw new IllegalArgumentException("Not a valid builder image: " + builder);
        }
        return new Options(name, tag, builder, publish, sbom, dryRun);
    }

    static List<String> packCommand(Path workingDir, Options options) {
        List<String> command = new ArrayList<>(List.of(
            "pack", "build", options.name() + ":" + options.tag(),
            "--builder", options.builder(),
            "--path", ".",
            "--env", "BP_JVM_VERSION=21",
            // build the start-script distribution, not a fat jar, and start the app from it
            "--env", "BP_GRADLE_BUILD_ARGUMENTS=--no-daemon -x test distZip",
            "--env", "BP_GRADLE_BUILT_ARTIFACT=build/distributions/*.zip"));
        if (options.publish()) {
            command.add("--publish");
        }
        if (options.sbom() != null) {
            command.add("--sbom-output-dir");
            command.add(options.sbom().toString());
        }
        return command;
    }

    /** The project directory's name, made into a valid image name. */
    static String defaultName(Path workingDir) {
        Path name = workingDir.toAbsolutePath().normalize().getFileName();
        String raw = name == null ? "app" : name.toString();
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("^[._-]+|[._-]+$", "").replaceAll("[._-]{2,}", "-");
        return cleaned.isEmpty() ? "app" : cleaned;
    }

    private static String value(List<String> args, int index, String option) {
        if (index >= args.size()) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args.get(index);
    }

    static Path findOnPath(String program) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (String dir : path.split(File.pathSeparator)) {
            for (String candidate : windows ? List.of(program + ".exe", program + ".cmd") : List.of(program)) {
                Path file = Path.of(dir, candidate);
                if (Files.isExecutable(file)) {
                    return file;
                }
            }
        }
        return null;
    }
}
