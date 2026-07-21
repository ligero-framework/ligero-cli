package com.ligero.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * {@code ligero add <module>} — wires an optional Ligero module into the current
 * project: adds the dependency to {@code build.gradle} (when the module is not
 * already in {@code ligero-core}) and drops a ready-to-use starter config.
 * {@code query} is a built-in HTTP method, so it just prints how to use it.
 */
final class AddCommand {

    /** artifact = null means the module lives in ligero-core (no new dependency). */
    private record Spec(String artifact, String configClass, String docSlug) {
    }

    private static final Map<String, Spec> MODULES = Map.of(
        "scheduler",  new Spec("ligero-scheduler",  "SchedulerConfig",  "scheduler"),
        "cache",      new Spec(null,                 "CacheConfig",      "cache"),
        "redis",      new Spec("ligero-redis",       "RedisCacheConfig", "cache"),
        "resilience", new Spec("ligero-resilience",  "ResilienceConfig", "resilience"),
        "auth",       new Spec("ligero-auth",        "AuthConfig",       "auth-asymmetric"),
        "events",     new Spec(null,                 "AppEvents",        "events"),
        "jdbc",       new Spec("ligero-jdbc",        "DatabaseConfig",   "jdbc-pooling"),
        "mcp",        new Spec("ligero-mcp",         "McpConfig",        "mcp"));

    int run(Path workingDir, List<String> args) throws IOException {
        if (args.isEmpty()) {
            throw new IllegalArgumentException(
                "Usage: ligero add <module>   (scheduler | cache | redis | resilience | auth | events | jdbc | mcp | query)");
        }
        String module = args.get(0);
        if ("query".equals(module)) {
            return explainQuery();
        }
        Spec spec = MODULES.get(module);
        if (spec == null) {
            System.err.println("Unknown module: " + module
                + "\nAvailable: scheduler, cache, redis, resilience, auth, events, jdbc, mcp, query");
            return 1;
        }
        boolean pool = args.contains("--pool");
        return addModule(workingDir, module, spec, pool);
    }

    private int addModule(Path workingDir, String module, Spec spec, boolean pool) throws IOException {
        Path buildFile = buildFile(workingDir);
        Project project = Project.locate(workingDir);

        if (spec.artifact() != null) {
            String coordinate = "com.ligeroframework:" + spec.artifact() + ":" + Templates.LIGERO_VERSION;
            if (addDependency(buildFile, coordinate)) {
                System.out.println("✓ Added " + coordinate + " to " + buildFile.getFileName());
            } else {
                System.out.println("• " + coordinate + " already in " + buildFile.getFileName());
            }
        } else {
            System.out.println("• " + module + " is part of ligero-core — no dependency to add");
        }

        Path configFile = project.packageDir(project.basePackage() + ".config")
            .resolve(spec.configClass() + ".java");
        if (Files.exists(configFile)) {
            System.out.println("• " + rel(workingDir, configFile) + " already exists — left as is");
        } else {
            Files.createDirectories(configFile.getParent());
            String serverName = workingDir.toAbsolutePath().getFileName().toString();
            Files.writeString(configFile, template(module, spec, project.basePackage(), serverName, pool));
            System.out.println("✓ Created " + rel(workingDir, configFile));
        }

        System.out.println("→ Docs: https://doc.ligeroframework.com/guides/" + spec.docSlug());
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

    // ---- build file helpers -------------------------------------------------

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

    // ---- starter templates --------------------------------------------------

    private static String template(String module, Spec spec, String basePackage, String serverName, boolean pool) {
        String body = switch (module) {
            case "scheduler" -> """
                import com.ligero.scheduler.Scheduler;

                import java.time.Duration;

                /** App scheduler. Close it in Application: {@code app.onStop(scheduler::close);} */
                public final class SchedulerConfig {

                    private SchedulerConfig() {
                    }

                    public static Scheduler scheduler() {
                        Scheduler scheduler = new Scheduler();
                        scheduler.fixedRate(Duration.ofMinutes(5), () -> {
                            // TODO: your periodic job
                        });
                        return scheduler;
                    }
                }
                """;
            case "cache" -> """
                import com.ligero.cache.Cache;
                import com.ligero.cache.InMemoryCache;

                /** In-process cache. Swap for RedisCache (ligero add redis) to share it. */
                public final class CacheConfig {

                    private CacheConfig() {
                    }

                    public static <K, V> Cache<K, V> cache() {
                        return new InMemoryCache<>();
                    }
                }
                """;
            case "redis" -> """
                import com.ligero.cache.Cache;
                import com.ligero.redis.RedisCache;

                import redis.clients.jedis.JedisPool;

                /** Distributed cache over Redis (shared across instances). */
                public final class RedisCacheConfig {

                    private RedisCacheConfig() {
                    }

                    public static Cache<String, String> cache(JedisPool pool) {
                        return RedisCache.usingJedis(pool);
                    }
                }
                """;
            case "resilience" -> """
                import com.ligero.resilience.CircuitBreaker;
                import com.ligero.resilience.Retry;

                import java.time.Duration;

                /** Shared resilience policies for outbound calls. */
                public final class ResilienceConfig {

                    private ResilienceConfig() {
                    }

                    public static final Retry RETRY = Retry.of(3, Duration.ofMillis(200)).exponential();
                    public static final CircuitBreaker BREAKER = new CircuitBreaker(5, Duration.ofSeconds(30));
                }
                """;
            case "auth" -> """
                import com.ligero.auth.Jwt;
                import com.ligero.spi.BodyMapper;

                /** JWT config. HS256 by default; see the docs for RS256/ES256 + JWKS. */
                public final class AuthConfig {

                    private AuthConfig() {
                    }

                    public static Jwt jwt(BodyMapper mapper) {
                        String secret = System.getenv().getOrDefault(
                            "JWT_SECRET", "change-me-change-me-change-me-32b"); // >= 32 bytes
                        return Jwt.hs256(secret, mapper);
                    }
                }
                """;
            case "events" -> """
                import com.ligero.events.Events;

                /** The application event bus. Inject it where you publish or subscribe. */
                public final class AppEvents {

                    private AppEvents() {
                    }

                    public static final Events BUS = new Events();
                }
                """;
            case "jdbc" -> pool ? """
                import com.ligero.jdbc.DataSources;
                import com.ligero.jdbc.Jdbc;

                import com.zaxxer.hikari.HikariDataSource;

                /** Pooled (HikariCP) database access. Close the pool: app.onStop(dataSource()::close). */
                public final class DatabaseConfig {

                    private DatabaseConfig() {
                    }

                    public static HikariDataSource dataSource() {
                        return DataSources.pooled(
                            System.getenv().getOrDefault("DB_URL", "jdbc:h2:mem:app"),
                            System.getenv().getOrDefault("DB_USER", "sa"),
                            System.getenv().getOrDefault("DB_PASSWORD", ""));
                    }

                    public static Jdbc jdbc() {
                        return new Jdbc(dataSource());
                    }
                }
                """ : """
                import com.ligero.jdbc.Jdbc;

                import javax.sql.DataSource;

                /** Database access. Bring your own DataSource, or `ligero add jdbc --pool` for HikariCP. */
                public final class DatabaseConfig {

                    private DatabaseConfig() {
                    }

                    public static Jdbc jdbc(DataSource dataSource) {
                        return new Jdbc(dataSource);
                    }
                }
                """;
            case "mcp" -> """
                import com.ligero.mcp.McpServer;

                import java.util.Map;

                /** MCP server. Mount it in Application: {@code app.use(McpConfig.mcp().http("/mcp"));} */
                public final class McpConfig {

                    private McpConfig() {
                    }

                    public static McpServer mcp() {
                        return McpServer.create("%SERVER%", "1.0.0")
                            .tool("echo", "Echo the input text",
                                McpServer.objectSchema(
                                    Map.of("text", McpServer.stringParam("text to echo")), "text"),
                                args -> String.valueOf(args.get("text")));
                    }
                }
                """;
            default -> throw new IllegalStateException("No template for " + module);
        };
        return ("package " + basePackage + ".config;\n\n" + body).replace("%SERVER%", serverName);
    }
}
