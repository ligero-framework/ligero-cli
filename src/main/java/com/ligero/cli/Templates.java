package com.ligero.cli;

/** Code templates emitted by the scaffolder. */
final class Templates {

    private Templates() {
    }

    static final String LIGERO_VERSION = "0.2.0-SNAPSHOT";

    static String settingsGradle(String name) {
        return "rootProject.name = '" + name + "'\n";
    }

    static String buildGradle(String basePackage, String db) {
        String dbDependency = switch (db) {
            case "postgres" -> "    implementation 'org.postgresql:postgresql:42.7.4'\n";
            case "h2" -> "    implementation 'com.h2database:h2:2.3.232'\n";
            default -> "";
        };
        return """
            plugins {
                id 'application'
            }

            java {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            application {
                mainClass = '%s.Application'
            }

            dependencies {
                implementation 'com.ligero:ligero-core:%s'
                runtimeOnly 'com.ligero:ligero-server-jdk:%s'
                runtimeOnly 'com.ligero:ligero-json:%s'
                runtimeOnly 'org.slf4j:slf4j-simple:2.0.16'

                testImplementation 'com.ligero:ligero-test:%s'
                testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            %s}

            test {
                useJUnitPlatform()
            }
            """.formatted(basePackage, LIGERO_VERSION, LIGERO_VERSION, LIGERO_VERSION, LIGERO_VERSION, dbDependency);
    }

    static String gitignore() {
        return """
            build/
            .gradle/
            *.class
            .idea/
            *.iml
            """;
    }

    static String projectReadme(String name, String db) {
        String dbSection = switch (db) {
            case "postgres" -> """

                ## Database

                `docker compose up` starts PostgreSQL alongside the app
                (schema + sample data from `db/init.sql`). Try `GET /db/greetings`.
                Locally without Docker, point `DB_URL`/`DB_USER`/`DB_PASSWORD`
                at your own instance.
                """;
            case "h2" -> """

                ## Database

                In-memory H2, created on startup — nothing to install.
                Try `GET /db/greetings`.
                """;
            default -> "";
        };
        return """
            # %s

            A [Ligero](https://github.com/ligero-framework/ligero) application.

            ```bash
            gradle run                 # http://localhost:8080
            gradle test
            docker compose up --build # containerized
            ```
            %s""".formatted(name, dbSection);
    }

    static String application(String basePackage, String db) {
        String dbImports = db.equals("none") ? "" : """

            import com.ligero.middleware.HealthMiddleware;
            import javax.sql.DataSource;
            import java.sql.Connection;
            import java.sql.ResultSet;
            import java.sql.Statement;
            import java.util.ArrayList;
            import java.util.List;
            """;
        String dbWiring = switch (db) {
            case "postgres" -> """

                    DataSource dataSource = dataSource();
                    app.register(DataSource.class, dataSource);
                    app.use(HealthMiddleware.builder()
                        .check("db", () -> isDbUp(dataSource))
                        .build());
                    app.get("/db/greetings", ctx -> ctx.json(greetings(ctx.get(DataSource.class))));
            """;
            case "h2" -> """

                    DataSource dataSource = dataSource();
                    initSchema(dataSource);
                    app.register(DataSource.class, dataSource);
                    app.use(HealthMiddleware.builder()
                        .check("db", () -> isDbUp(dataSource))
                        .build());
                    app.get("/db/greetings", ctx -> ctx.json(greetings(ctx.get(DataSource.class))));
            """;
            default -> "";
        };
        String dbHelpers = switch (db) {
            case "postgres" -> """

                private static DataSource dataSource() {
                    var ds = new org.postgresql.ds.PGSimpleDataSource();
                    ds.setUrl(env("DB_URL", "jdbc:postgresql://localhost:5432/app"));
                    ds.setUser(env("DB_USER", "app"));
                    ds.setPassword(env("DB_PASSWORD", "app"));
                    return ds;
                }
            """ + COMMON_DB_HELPERS;
            case "h2" -> """

                private static DataSource dataSource() {
                    var ds = new org.h2.jdbcx.JdbcDataSource();
                    ds.setURL("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1");
                    return ds;
                }

                private static void initSchema(DataSource dataSource) {
                    try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                        s.execute("CREATE TABLE IF NOT EXISTS greetings(id INT PRIMARY KEY, message VARCHAR(100))");
                        s.execute("MERGE INTO greetings KEY(id) VALUES (1, 'Hola desde H2'), (2, 'Hello from H2')");
                    } catch (Exception e) {
                        throw new IllegalStateException("Could not init schema", e);
                    }
                }
            """ + COMMON_DB_HELPERS;
            default -> "";
        };
        return """
            package %s;

            import com.ligero.Ligero;
            import com.ligero.middleware.RequestLoggingMiddleware;

            import java.util.Map;

            public class Application {

                public static void main(String[] args) throws Exception {
                    Ligero app = create();
                    app.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
                    System.out.println("Running at http://localhost:" + app.port());
                }

                /** App wiring, separated from main() so tests can start it on an ephemeral port. */
                public static Ligero create() {
                    Ligero app = Ligero.create(8080);
                    app.use(new RequestLoggingMiddleware());

                    app.get("/", ctx -> ctx.text("It works!"));
                    app.get("/hello/{name}", ctx ->
                        ctx.json(Map.of("hello", ctx.pathParam("name"))));

                    return app;
                }
            %s}
            """.formatted(basePackage, dbHelpers)
            .replace("import com.ligero.middleware.RequestLoggingMiddleware;",
                "import com.ligero.middleware.RequestLoggingMiddleware;" + dbImports)
            .replace("return app;", dbWiring.isEmpty() ? "return app;" : dbWiring + "        return app;");
    }

    private static final String COMMON_DB_HELPERS = """

                private static String env(String key, String fallback) {
                    String value = System.getenv(key);
                    return value == null || value.isBlank() ? fallback : value;
                }

                private static boolean isDbUp(DataSource dataSource) {
                    try (Connection c = dataSource.getConnection()) {
                        return c.isValid(1);
                    } catch (Exception e) {
                        return false;
                    }
                }

                private static List<String> greetings(DataSource dataSource) {
                    try (Connection c = dataSource.getConnection();
                         Statement s = c.createStatement();
                         ResultSet rs = s.executeQuery("SELECT message FROM greetings ORDER BY id")) {
                        List<String> messages = new ArrayList<>();
                        while (rs.next()) {
                            messages.add(rs.getString(1));
                        }
                        return messages;
                    } catch (Exception e) {
                        throw new IllegalStateException("DB query failed", e);
                    }
                }
    """;

    static String applicationTest(String basePackage) {
        return """
            package %s;

            import com.ligero.test.LigeroTest;

            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            class ApplicationTest {

                @Test
                void helloEndpointResponds() {
                    try (LigeroTest test = LigeroTest.create(app ->
                            app.get("/hello/{name}", ctx ->
                                ctx.json(java.util.Map.of("hello", ctx.pathParam("name")))))) {
                        LigeroTest.TestResponse response = test.get("/hello/world").execute();
                        assertEquals(200, response.status());
                        assertTrue(response.body().contains("world"));
                    }
                }
            }
            """.formatted(basePackage);
    }

    static String dockerfile(String name) {
        return """
            # Build stage
            FROM gradle:8.14-jdk21 AS build
            WORKDIR /app
            COPY . .
            RUN gradle installDist --no-daemon

            # Runtime stage
            FROM eclipse-temurin:21-jre
            WORKDIR /app
            COPY --from=build /app/build/install/%s/ .
            EXPOSE 8080
            USER 1000
            ENTRYPOINT ["bin/%s"]
            """.formatted(name, name);
    }

    static String dockerCompose(String name, String db) {
        String appService = """
            services:
              app:
                build: .
                ports:
                  - "8080:8080"
            """;
        if ("postgres".equals(db)) {
            return appService + """
                environment:
                  DB_URL: jdbc:postgresql://db:5432/app
                  DB_USER: app
                  DB_PASSWORD: app
                depends_on:
                  db:
                    condition: service_healthy

              db:
                image: postgres:16-alpine
                environment:
                  POSTGRES_DB: app
                  POSTGRES_USER: app
                  POSTGRES_PASSWORD: app
                ports:
                  - "5432:5432"
                volumes:
                  - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
                healthcheck:
                  test: ["CMD-SHELL", "pg_isready -U app -d app"]
                  interval: 3s
                  timeout: 3s
                  retries: 10
            """;
        }
        return appService;
    }

    static String initSql() {
        return """
            CREATE TABLE IF NOT EXISTS greetings(id INT PRIMARY KEY, message VARCHAR(100));
            INSERT INTO greetings(id, message) VALUES
              (1, 'Hola desde PostgreSQL'),
              (2, 'Hello from PostgreSQL')
            ON CONFLICT (id) DO NOTHING;
            """;
    }

    static String controller(String basePackage, String name) {
        String variable = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return """
            package %s;

            import com.ligero.Ligero;
            import com.ligero.http.NotFoundException;

            import java.util.List;
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicLong;

            /** CRUD controller for %s resources. */
            public class %sController {

                public record %s(Long id, String name) {
                }

                private final Map<Long, %s> store = new ConcurrentHashMap<>();
                private final AtomicLong ids = new AtomicLong();

                /** Attaches this controller's routes to the app. */
                public void register(Ligero app) {
                    app.group("/api/%ss", api -> {
                        api.get("", ctx -> ctx.json(List.copyOf(store.values())));

                        api.get("/{id}", ctx -> {
                            %s found = store.get(ctx.pathParamAsLong("id"));
                            if (found == null) {
                                throw new NotFoundException("%s not found");
                            }
                            ctx.json(found);
                        });

                        api.post("", ctx -> {
                            %s body = ctx.bodyValidator(%s.class)
                                .check(v -> v.name() != null && !v.name().isBlank(), "name is required")
                                .get();
                            long id = ids.incrementAndGet();
                            %s created = new %s(id, body.name());
                            store.put(id, created);
                            ctx.status(201).json(created);
                        });

                        api.delete("/{id}", ctx -> {
                            store.remove(ctx.pathParamAsLong("id"));
                            ctx.status(204).res().end();
                        });
                    });
                }
            }
            """.formatted(basePackage, name, name, name, name, variable,
                name, name, name, name, name, name);
    }
}
