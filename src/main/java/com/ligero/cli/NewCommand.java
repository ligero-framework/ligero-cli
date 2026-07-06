package com.ligero.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** {@code ligero new <name>}: generates a ready-to-run Gradle project. */
final class NewCommand {

    int run(Path workingDir, List<String> args) throws IOException {
        if (args.isEmpty() || args.get(0).startsWith("--")) {
            throw new IllegalArgumentException("Usage: ligero new <project-name> [--package <base.package>]");
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

        write(root.resolve("settings.gradle"), Templates.settingsGradle(name));
        write(root.resolve("build.gradle"), Templates.buildGradle(basePackage, db));
        write(root.resolve(".gitignore"), Templates.gitignore());
        write(root.resolve("README.md"), Templates.projectReadme(name, db));
        write(root.resolve("Dockerfile"), Templates.dockerfile(name));
        write(root.resolve("docker-compose.yml"), Templates.dockerCompose(name, db));
        if ("postgres".equals(db)) {
            write(root.resolve("db/init.sql"), Templates.initSql());
        }
        write(root.resolve(packageDir).resolve("Application.java"), Templates.application(basePackage, db));

        Path featureDir = packageDir.resolve("greeting");
        write(root.resolve(featureDir).resolve("GreetingRepository.java"), Templates.greetingRepository(basePackage));
        write(root.resolve(featureDir).resolve("InMemoryGreetingRepository.java"), Templates.inMemoryGreetingRepository(basePackage));
        if (!"none".equals(db)) {
            write(root.resolve(featureDir).resolve("JdbcGreetingRepository.java"), Templates.jdbcGreetingRepository(basePackage));
        }
        write(root.resolve(featureDir).resolve("GreetingService.java"), Templates.greetingService(basePackage));
        write(root.resolve(featureDir).resolve("DefaultGreetingService.java"), Templates.defaultGreetingService(basePackage));
        write(root.resolve(featureDir).resolve("GreetingController.java"), Templates.greetingController(basePackage));

        write(root.resolve(testPackageDir).resolve("ApplicationTest.java"), Templates.applicationTest(basePackage));

        System.out.println("""
            Created project '%s' (db: %s) — layered: controller -> service -> repository

            Next steps:
              cd %s
              gradle run                # app on http://localhost:8080, devtools on /ligero/dev
              gradle test               # end-to-end test with the in-memory repository
              docker compose up --build # containerized app%s
            """.formatted(name, db, name,
                "postgres".equals(db) ? " + PostgreSQL (try /api/greetings)" : ""));
        return 0;
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        System.out.println("  create " + file);
    }
}
