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
                // Visual debugger at /ligero/dev — development only, drop it for production builds.
                implementation 'com.ligero:ligero-devtools:%s'
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
            """.formatted(basePackage, LIGERO_VERSION, LIGERO_VERSION, LIGERO_VERSION,
                LIGERO_VERSION, LIGERO_VERSION, dbDependency);
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
                (schema + sample data from `db/init.sql`). Try `GET /api/greetings`.
                Locally without Docker, point `DB_URL`/`DB_USER`/`DB_PASSWORD`
                at your own instance.
                """;
            case "h2" -> """

                ## Database

                In-memory H2, created on startup — nothing to install.
                Try `GET /api/greetings`.
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

            ## Layers

            The app is wired layer by layer in `Application.wire(...)` — the
            composition root — using Ligero's `Beans` container (plain lambdas,
            checked by the compiler, validated at startup):

            ```
            greeting/GreetingController   @Controller  routes -> service
            greeting/GreetingService      (interface)  business logic
            greeting/GreetingRepository   (interface)  data access
            ```

            Repositories and services are bound **as interfaces**, so tests swap
            them (see `ApplicationTest`) and devtools can trace calls through them.

            ## Devtools

            While the app runs, open **http://localhost:8080/ligero/dev** to see
            the bean dependency graph and a live trace of every request through
            the layers (arguments, results, timing per call). Development only —
            remove the `ligero-devtools` dependency for production builds, or set
            `LIGERO_DEVTOOLS=false`.
            %s""".formatted(name, dbSection);
    }

    static String application(String basePackage, String db) {
        boolean hasDb = !"none".equals(db);
        String repoImport = hasDb ? "JdbcGreetingRepository" : "InMemoryGreetingRepository";
        String dbImports = hasDb ? """

            import com.ligero.middleware.HealthMiddleware;
            import javax.sql.DataSource;
            import java.sql.Connection;
            """ : "";
        String health = hasDb ? """

                    app.use(HealthMiddleware.builder()
                        .check("db", () -> isDbUp(beans.get(DataSource.class)))
                        .build());
            """ : "";
        String repoBindings = hasDb
            ? "            .bind(DataSource.class,          b -> dataSource())\n"
              + "            .bind(GreetingRepository.class,  b -> new JdbcGreetingRepository(b.get(DataSource.class)))"
            : "            .bind(GreetingRepository.class,  b -> new InMemoryGreetingRepository())";
        String dbHelpers = switch (db) {
            case "postgres" -> """

                private static DataSource dataSource() {
                    var ds = new org.postgresql.ds.PGSimpleDataSource();
                    ds.setUrl(env("DB_URL", "jdbc:postgresql://localhost:5432/app"));
                    ds.setUser(env("DB_USER", "app"));
                    ds.setPassword(env("DB_PASSWORD", "app"));
                    return ds;
                }

                private static String env(String key, String fallback) {
                    String value = System.getenv(key);
                    return value == null || value.isBlank() ? fallback : value;
                }
            """ + IS_DB_UP;
            case "h2" -> """

                private static DataSource dataSource() {
                    var ds = new org.h2.jdbcx.JdbcDataSource();
                    ds.setURL("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1");
                    initSchema(ds);
                    return ds;
                }

                private static void initSchema(DataSource dataSource) {
                    try (Connection c = dataSource.getConnection();
                         var s = c.createStatement()) {
                        s.execute("CREATE TABLE IF NOT EXISTS greetings("
                            + "id INT AUTO_INCREMENT PRIMARY KEY, message VARCHAR(200))");
                        s.execute("INSERT INTO greetings(message) VALUES ('Hola desde H2'), ('Hello from H2')");
                    } catch (Exception e) {
                        throw new IllegalStateException("Could not init schema", e);
                    }
                }
            """ + IS_DB_UP;
            default -> "";
        };
        return """
            package %s;

            import %s.greeting.DefaultGreetingService;
            import %s.greeting.GreetingController;
            import %s.greeting.GreetingRepository;
            import %s.greeting.GreetingService;
            import %s.greeting.%s;

            import com.ligero.Ligero;
            import com.ligero.beans.Beans;
            import com.ligero.devtools.Devtools;
            import com.ligero.middleware.RequestLoggingMiddleware;
            %s
            public class Application {

                public static void main(String[] args) throws Exception {
                    Ligero app = create();
                    app.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
                    System.out.println("Running at  http://localhost:" + app.port());
                    System.out.println("Devtools at http://localhost:" + app.port() + "/ligero/dev");
                }

                /** App wiring, separated from main() so tests can start it on an ephemeral port. */
                public static Ligero create() {
                    Ligero app = Ligero.create(8080);
                    app.use(new RequestLoggingMiddleware());

                    // Visual debugger at /ligero/dev (set LIGERO_DEVTOOLS=false to disable).
                    Devtools devtools = Devtools.create();
                    Beans beans = wire(devtools);
                    app.beans(beans);
                    devtools.install(app, beans);
            %s
                    app.get("/", ctx -> ctx.text("It works!"));
                    beans.get(GreetingController.class).register(app);

                    return app;
                }

                /** Composition root: the whole object graph, layer by layer, checked by the compiler. */
                static Beans wire(Devtools devtools) {
                    return Beans.builder()
            %s
                        .bind(GreetingService.class,     b -> new DefaultGreetingService(b.get(GreetingRepository.class)))
                        .bind(GreetingController.class,  b -> new GreetingController(b.get(GreetingService.class)))
                        .instrument(devtools.recorder())
                        .start();
                }
            %s}
            """.formatted(basePackage, basePackage, basePackage, basePackage, basePackage,
                basePackage, repoImport, dbImports, health, repoBindings, dbHelpers);
    }

    private static final String IS_DB_UP = """

                private static boolean isDbUp(DataSource dataSource) {
                    try (Connection c = dataSource.getConnection()) {
                        return c.isValid(1);
                    } catch (Exception e) {
                        return false;
                    }
                }
    """;

    static String greetingRepository(String basePackage) {
        return """
            package %s.greeting;

            import java.util.List;

            /** Data-access layer. Bound as an interface so implementations swap freely. */
            public interface GreetingRepository {

                List<String> all();

                void add(String message);
            }
            """.formatted(basePackage);
    }

    static String inMemoryGreetingRepository(String basePackage) {
        return """
            package %s.greeting;

            import com.ligero.beans.stereotype.Repository;

            import java.util.List;
            import java.util.concurrent.CopyOnWriteArrayList;

            /** In-memory implementation — used by tests (and by the app when no DB is configured). */
            @Repository
            public class InMemoryGreetingRepository implements GreetingRepository {

                private final List<String> greetings = new CopyOnWriteArrayList<>(
                    List.of("Hola desde Ligero"));

                @Override
                public List<String> all() {
                    return List.copyOf(greetings);
                }

                @Override
                public void add(String message) {
                    greetings.add(message);
                }
            }
            """.formatted(basePackage);
    }

    static String jdbcGreetingRepository(String basePackage) {
        return """
            package %s.greeting;

            import com.ligero.beans.stereotype.Repository;

            import javax.sql.DataSource;
            import java.sql.Connection;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.Statement;
            import java.util.ArrayList;
            import java.util.List;

            /** JDBC implementation backed by the {@code greetings} table. */
            @Repository
            public class JdbcGreetingRepository implements GreetingRepository {

                private final DataSource dataSource;

                public JdbcGreetingRepository(DataSource dataSource) {
                    this.dataSource = dataSource;
                }

                @Override
                public List<String> all() {
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

                @Override
                public void add(String message) {
                    try (Connection c = dataSource.getConnection();
                         PreparedStatement ps = c.prepareStatement(
                             "INSERT INTO greetings(message) VALUES (?)")) {
                        ps.setString(1, message);
                        ps.executeUpdate();
                    } catch (Exception e) {
                        throw new IllegalStateException("DB insert failed", e);
                    }
                }
            }
            """.formatted(basePackage);
    }

    static String greetingService(String basePackage) {
        return """
            package %s.greeting;

            import java.util.List;

            /** Business layer. Bound as an interface so devtools can trace calls through it. */
            public interface GreetingService {

                String greet(String name);

                List<String> greetings();

                String add(String message);
            }
            """.formatted(basePackage);
    }

    static String defaultGreetingService(String basePackage) {
        return """
            package %s.greeting;

            import com.ligero.beans.stereotype.Service;

            import java.util.List;

            @Service
            public class DefaultGreetingService implements GreetingService {

                private final GreetingRepository repository;

                public DefaultGreetingService(GreetingRepository repository) {
                    this.repository = repository;
                }

                @Override
                public String greet(String name) {
                    return "Hola, " + name + "!";
                }

                @Override
                public List<String> greetings() {
                    return repository.all();
                }

                @Override
                public String add(String message) {
                    repository.add(message);
                    return message;
                }
            }
            """.formatted(basePackage);
    }

    static String greetingController(String basePackage) {
        return """
            package %s.greeting;

            import com.ligero.Ligero;
            import com.ligero.beans.stereotype.Controller;

            import java.util.Map;

            /** Web layer: translates HTTP to service calls; no business logic here. */
            @Controller
            public class GreetingController {

                public record NewGreeting(String message) {
                }

                private final GreetingService service;

                public GreetingController(GreetingService service) {
                    this.service = service;
                }

                /** Attaches this controller's routes to the app. */
                public void register(Ligero app) {
                    app.get("/hello/{name}", ctx ->
                        ctx.json(Map.of("hello", service.greet(ctx.pathParam("name")))));

                    app.group("/api/greetings", api -> {
                        api.get("", ctx -> ctx.json(service.greetings()));

                        api.post("", ctx -> {
                            NewGreeting body = ctx.bodyValidator(NewGreeting.class)
                                .check(g -> g.message() != null && !g.message().isBlank(),
                                       "message is required")
                                .get();
                            ctx.status(201).json(Map.of("added", service.add(body.message())));
                        });
                    });
                }
            }
            """.formatted(basePackage);
    }

    static String applicationTest(String basePackage) {
        return """
            package %s;

            import %s.greeting.DefaultGreetingService;
            import %s.greeting.GreetingController;
            import %s.greeting.GreetingRepository;
            import %s.greeting.GreetingService;
            import %s.greeting.InMemoryGreetingRepository;

            import com.ligero.beans.Beans;
            import com.ligero.test.LigeroTest;

            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            class ApplicationTest {

                @Test
                void greetingFlowsThroughTheLayers() {
                    // Same layered graph as production, with the repository swapped in-memory.
                    Beans beans = Beans.builder()
                        .bind(GreetingRepository.class, b -> new InMemoryGreetingRepository())
                        .bind(GreetingService.class,    b -> new DefaultGreetingService(b.get(GreetingRepository.class)))
                        .bind(GreetingController.class, b -> new GreetingController(b.get(GreetingService.class)))
                        .start();

                    try (LigeroTest test = LigeroTest.create(app -> {
                        app.beans(beans);
                        beans.get(GreetingController.class).register(app);
                    })) {
                        LigeroTest.TestResponse hello = test.get("/hello/world").execute();
                        assertEquals(200, hello.status());
                        assertTrue(hello.body().contains("world"));

                        LigeroTest.TestResponse list = test.get("/api/greetings").execute();
                        assertEquals(200, list.status());
                        assertTrue(list.body().contains("Hola"));
                    }
                }
            }
            """.formatted(basePackage, basePackage, basePackage, basePackage, basePackage, basePackage);
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
            CREATE TABLE IF NOT EXISTS greetings(
              id SERIAL PRIMARY KEY,
              message VARCHAR(200)
            );
            INSERT INTO greetings(message) VALUES
              ('Hola desde PostgreSQL'),
              ('Hello from PostgreSQL');
            """;
    }

    static String controller(String basePackage, String name) {
        String variable = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return """
            package %s;

            import com.ligero.Ligero;
            import com.ligero.beans.stereotype.Controller;
            import com.ligero.http.NotFoundException;

            import java.util.List;
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicLong;

            /** CRUD controller for %s resources. */
            @Controller
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
