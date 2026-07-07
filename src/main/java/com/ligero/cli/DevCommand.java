package com.ligero.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code ligero dev}: runs the app and restarts it whenever a source file
 * changes — a simple, dependency-free dev loop (watch → rebuild → restart).
 *
 * <p>It launches the app via Gradle ({@code ./gradlew run} when a wrapper is
 * present, otherwise {@code gradle run}) and watches {@code src/main}. On a
 * change it stops the app and starts it again; Gradle recompiles as part of
 * {@code run}. Press Ctrl+C to stop.</p>
 */
final class DevCommand {

    private static final long DEBOUNCE_MS = 300;

    private volatile Process app;

    int run(Path workingDir, List<String> args) throws IOException {
        if (!Files.exists(workingDir.resolve("build.gradle"))
                && !Files.exists(workingDir.resolve("build.gradle.kts"))) {
            throw new IllegalArgumentException("No build.gradle here — run 'ligero dev' inside a Ligero project.");
        }
        Path watchRoot = workingDir.resolve("src/main");
        if (!Files.isDirectory(watchRoot)) {
            throw new IllegalArgumentException("src/main not found — is this a Ligero project?");
        }

        List<String> command = gradleCommand(workingDir);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopApp));

        System.out.println("ligero dev — watching " + watchRoot + " (Ctrl+C to stop)");
        startApp(workingDir, command);

        try (WatchService watcher = watchRoot.getFileSystem().newWatchService()) {
            registerAll(watchRoot, watcher);
            while (true) {
                WatchKey key = watcher.take();               // blocks until a change
                key.pollEvents();
                debounceAndDrain(watcher);
                System.out.println("\nligero dev — change detected, restarting…\n");
                startApp(workingDir, command);               // stops the old one first
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } finally {
            stopApp();
        }
    }

    /** Resolves the Gradle invocation: the wrapper if present, else system gradle. */
    static List<String> gradleCommand(Path workingDir) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String wrapper = windows ? "gradlew.bat" : "gradlew";
        String launcher = Files.exists(workingDir.resolve(wrapper))
            ? workingDir.resolve(wrapper).toString()
            : (windows ? "gradle.bat" : "gradle");
        return List.of(launcher, "run", "-q", "--console=plain");
    }

    private void startApp(Path workingDir, List<String> command) throws IOException {
        stopApp();
        app = new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .inheritIO()
            .start();
    }

    private void stopApp() {
        Process current = app;
        if (current != null && current.isAlive()) {
            current.destroy();
            try {
                if (!current.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    current.destroyForcibly();
                }
            } catch (InterruptedException e) {
                current.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void debounceAndDrain(WatchService watcher) throws InterruptedException {
        Thread.sleep(DEBOUNCE_MS);
        WatchKey extra;
        while ((extra = watcher.poll()) != null) {   // collapse a burst of saves into one restart
            extra.pollEvents();
            extra.reset();
        }
    }

    /** WatchService only watches one directory, so register the whole tree. */
    private static void registerAll(Path root, WatchService watcher) throws IOException {
        try (Stream<Path> dirs = Files.walk(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                dir.register(watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            }
        }
    }
}
