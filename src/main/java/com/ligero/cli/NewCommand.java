package com.ligero.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** {@code ligero new <name>}: generates a ready-to-run modular Gradle project. */
final class NewCommand {

    int run(Path workingDir, List<String> args) throws IOException {
        if (args.isEmpty() || args.get(0).startsWith("--")) {
            throw new IllegalArgumentException("Usage: ligero new <project-name> [--package <base.package>] [--db none|h2|postgres] [--wiring explicit|processor] [--devtools true|false]");
        }
        String name = args.get(0);
        if (!name.matches("[a-zA-Z][a-zA-Z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid project name: " + name);
        }
        String db = LigeroCli.option(args, "--db");
        if (db == null) {
            db = "none";
        }
        if (!java.util.Set.of("none", "postgres", "h2").contains(db)) {
            throw new IllegalArgumentException("--db must be one of: none, postgres, h2");
        }
        String wiring = LigeroCli.option(args, "--wiring");
        if (wiring == null) {
            wiring = "explicit";
        }
        if (!java.util.Set.of("explicit", "processor").contains(wiring)) {
            throw new IllegalArgumentException("--wiring must be one of: explicit, processor");
        }
        boolean processor = "processor".equals(wiring);
        // Devtools are wired by default; opt out with `--devtools false`.
        String devtoolsOpt = LigeroCli.option(args, "--devtools");
        if (devtoolsOpt != null && !java.util.Set.of("true", "false").contains(devtoolsOpt)) {
            throw new IllegalArgumentException("--devtools must be one of: true, false");
        }
        boolean devtools = !"false".equals(devtoolsOpt);
        String basePackage = LigeroCli.option(args, "--package");
        if (basePackage == null) {
            basePackage = "com.example." + name.toLowerCase().replaceAll("[^a-z0-9]", "");
        }
        if (!basePackage.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")) {
            throw new IllegalArgumentException("Invalid package name: " + basePackage);
        }

        Path root = workingDir.resolve(name);
        if (Files.exists(root)) {
            throw new IllegalArgumentException("Directory already exists: " + root);
        }
        Path packageDir = Path.of("src/main/java", basePackage.split("\\."));
        Path testPackageDir = Path.of("src/test/java", basePackage.split("\\."));
        Path greetingDir = packageDir.resolve("greeting");

        write(root.resolve("settings.gradle"), Templates.settingsGradle(name));
        write(root.resolve("build.gradle"), Templates.buildGradle(basePackage, db, processor, devtools));
        write(root.resolve(".gitignore"), Templates.gitignore());
        write(root.resolve("README.md"), Templates.projectReadme(name, db, processor, devtools));
        write(root.resolve("Dockerfile"), Templates.dockerfile(name));
        write(root.resolve("docker-compose.yml"), Templates.dockerCompose(name, db));
        if ("postgres".equals(db)) {
            write(root.resolve("db/init.sql"), Templates.initSql());
        }

        // Layer classes are identical in both wirings — they carry the stereotype
        // annotations either way. Only the wiring around them differs.
        write(root.resolve(greetingDir).resolve("GreetingRepository.java"), Templates.greetingRepository(basePackage));
        write(root.resolve(greetingDir).resolve("GreetingService.java"), Templates.greetingService(basePackage));
        write(root.resolve(greetingDir).resolve("DefaultGreetingService.java"), Templates.defaultGreetingService(basePackage));
        write(root.resolve(greetingDir).resolve("GreetingController.java"), Templates.greetingController(basePackage));

        if (processor) {
            // Processor mode: no hand-written module; annotate exactly one repository
            // impl (two would generate a duplicate binding), add a @Provides DataSource.
            write(root.resolve(packageDir).resolve("Application.java"), Templates.applicationProcessor(basePackage, db, devtools));
            if ("none".equals(db)) {
                write(root.resolve(greetingDir).resolve("InMemoryGreetingRepository.java"), Templates.inMemoryGreetingRepository(basePackage));
            } else {
                write(root.resolve(greetingDir).resolve("JdbcGreetingRepository.java"), Templates.jdbcGreetingRepository(basePackage));
                write(root.resolve(greetingDir).resolve("GreetingConfig.java"), Templates.greetingConfigProvides(basePackage, db));
            }
        } else {
            // Explicit mode: a GreetingModule owns the binds; both repo impls exist.
            write(root.resolve(packageDir).resolve("Application.java"), Templates.application(basePackage, db, devtools));
            write(root.resolve(greetingDir).resolve("GreetingModule.java"), Templates.greetingModule(basePackage, db));
            write(root.resolve(greetingDir).resolve("InMemoryGreetingRepository.java"), Templates.inMemoryGreetingRepository(basePackage));
            if (!"none".equals(db)) {
                write(root.resolve(greetingDir).resolve("JdbcGreetingRepository.java"), Templates.jdbcGreetingRepository(basePackage));
            }
        }

        write(root.resolve(testPackageDir).resolve("ApplicationTest.java"), Templates.applicationTest(basePackage));

        String wiringNote = processor
            ? "compile-time processor: annotate a class and it's wired — no module to edit"
            : "explicit: Application lists modules, GreetingModule owns the greeting slice";
        String addFeatures = processor
            ? """

            Add features (just annotate — the processor wires them):
              create a class with @Service / @Repository / @Controller in a package
            """
            : """

            Add features (auto-registered):
              ligero generate resource Order    # a whole CRUD slice
              ligero generate module Billing     # an empty feature module
            """;
        System.out.println("""
            Created project '%s' (db: %s, wiring: %s, devtools: %s) — %s

            Next steps:
              cd %s
              gradle run                 # app on http://localhost:8080%s
              gradle test                # end-to-end test
              docker compose up --build  # containerized app%s
            %s""".formatted(name, db, wiring, devtools, wiringNote, name,
                devtools ? ", devtools on /ligero/dev" : "",
                "postgres".equals(db) ? " + PostgreSQL (try /api/greetings)" : "", addFeatures));
        return 0;
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        System.out.println("  create " + file);
    }
}
