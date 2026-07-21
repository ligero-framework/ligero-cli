package com.ligero.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code ligero add <module>} — wires an optional Ligero module into the current
 * project: adds the dependency to {@code build.gradle} and drops a ready-to-use
 * starter config. {@code query} is a built-in HTTP method, so it just prints how
 * to use it.
 */
final class AddCommand {

    int run(Path workingDir, List<String> args) throws IOException {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Usage: ligero add <module>   (available: mcp, query)");
        }
        return switch (args.get(0)) {
            case "mcp" -> addMcp(workingDir);
            case "query" -> explainQuery();
            default -> {
                System.err.println("Unknown module: " + args.get(0) + "\nAvailable: mcp, query");
                yield 1;
            }
        };
    }

    private int addMcp(Path workingDir) throws IOException {
        Path buildFile = buildFile(workingDir);
        Project project = Project.locate(workingDir);
        String coordinate = "com.ligeroframework:ligero-mcp:" + Templates.LIGERO_VERSION;

        boolean added = addDependency(buildFile, coordinate);
        if (added) {
            System.out.println("✓ Added " + coordinate + " to " + buildFile.getFileName());
        } else {
            System.out.println("• " + coordinate + " already in " + buildFile.getFileName());
        }

        Path configFile = project.packageDir(project.basePackage() + ".config").resolve("McpConfig.java");
        if (Files.exists(configFile)) {
            System.out.println("• " + rel(workingDir, configFile) + " already exists — left as is");
        } else {
            Files.createDirectories(configFile.getParent());
            String serverName = workingDir.toAbsolutePath().getFileName().toString();
            Files.writeString(configFile, mcpConfig(project.basePackage(), serverName));
            System.out.println("✓ Created " + rel(workingDir, configFile));
        }

        System.out.println("→ Mount it in Application:  app.use(McpConfig.mcp().http(\"/mcp\"));");
        System.out.println("→ Docs: https://doc.ligeroframework.com/guides/mcp");
        return 0;
    }

    private int explainQuery() {
        System.out.println("""
            QUERY is a built-in HTTP method — no dependency to add.

            Register a handler directly (read the body as you would for POST):
                app.query("/search", ctx -> ctx.json(search(ctx.body(Filter.class))));

            → Docs: https://doc.ligeroframework.com/guides/query""");
        return 0;
    }

    // ---- helpers ------------------------------------------------------------

    private static Path buildFile(Path workingDir) {
        Path groovy = workingDir.resolve("build.gradle");
        Path kotlin = workingDir.resolve("build.gradle.kts");
        if (Files.exists(groovy)) {
            return groovy;
        }
        if (Files.exists(kotlin)) {
            return kotlin;
        }
        throw new IllegalArgumentException("No build.gradle here — run this inside a Ligero project.");
    }

    /** Inserts an {@code implementation} dependency into the build file; false if already present. */
    private static boolean addDependency(Path buildFile, String coordinate) throws IOException {
        List<String> lines = Files.readAllLines(buildFile);
        if (lines.stream().anyMatch(line -> line.contains(coordinate))) {
            return false;
        }
        boolean kts = buildFile.getFileName().toString().endsWith(".kts");
        String entry = kts
            ? "    implementation(\"" + coordinate + "\")"
            : "    implementation '" + coordinate + "'";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).replace(" ", "").startsWith("dependencies{")) {
                lines.add(i + 1, entry);
                Files.write(buildFile, lines);
                return true;
            }
        }
        throw new IllegalArgumentException("No dependencies { } block found in " + buildFile.getFileName());
    }

    private static String rel(Path workingDir, Path file) {
        return workingDir.toAbsolutePath().relativize(file.toAbsolutePath()).toString();
    }

    private static String mcpConfig(String basePackage, String serverName) {
        return """
            package %s.config;

            import com.ligero.mcp.McpServer;

            import java.util.Map;

            /**
             * MCP server configuration. Mount it in Application:
             * <pre>app.use(McpConfig.mcp().http("/mcp"));</pre>
             */
            public final class McpConfig {

                private McpConfig() {
                }

                public static McpServer mcp() {
                    return McpServer.create("%s", "1.0.0")
                        .tool("echo", "Echo the input text",
                            McpServer.objectSchema(
                                Map.of("text", McpServer.stringParam("text to echo")), "text"),
                            args -> String.valueOf(args.get("text")));
                }
            }
            """.formatted(basePackage, serverName);
    }
}
