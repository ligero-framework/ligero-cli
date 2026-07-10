package com.ligero.cli;

/**
 * Code templates emitted by the scaffolder. Generated files carry
 * {@code // ligero-cli:*} anchors so {@code ligero generate} can weave new
 * artifacts in (see {@link SourceEditor}).
 */
final class Templates {

    private Templates() {
    }

    static final String LIGERO_VERSION = "0.5.0";

    // ---------------------------------------------------------------- project

    static String settingsGradle(String name) {
        return "rootProject.name = '" + name + "'\n";
    }

    static String buildGradle(String basePackage, String db, boolean processor) {
        String dbDependency = switch (db) {
            case "postgres" -> "    implementation 'org.postgresql:postgresql:42.7.4'\n";
            case "h2" -> "    implementation 'com.h2database:h2:2.3.232'\n";
            default -> "";
        };
        // Opt-in compile-time DI: generates the explicit bind() wiring from your
        // annotated classes. Remove this line to hand-write the wiring instead.
        String processorDependency = processor
            ? "    annotationProcessor 'com.ligeroframework:ligero-processor:" + LIGERO_VERSION + "'\n"
            : "";
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
                implementation 'com.ligeroframework:ligero-core:%s'
                // Visual debugger at /ligero/dev — development only, drop it for production builds.
                implementation 'com.ligeroframework:ligero-devtools:%s'
            %s    runtimeOnly 'com.ligeroframework:ligero-server-jdk:%s'
                runtimeOnly 'com.ligeroframework:ligero-json:%s'
                runtimeOnly 'org.slf4j:slf4j-simple:2.0.16'

                testImplementation 'com.ligeroframework:ligero-test:%s'
                testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            %s}

            test {
                useJUnitPlatform()
            }
            """.formatted(basePackage, LIGERO_VERSION, LIGERO_VERSION, processorDependency,
                LIGERO_VERSION, LIGERO_VERSION, LIGERO_VERSION, dbDependency);
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

    static String projectReadme(String name, String db, boolean processor) {
        String wiringSection = processor
            ? """

                ## Wiring: compile-time processor

                This project uses `ligero-processor`. You **annotate** your classes
                (`@Service`, `@Repository`, `@Controller`) and the processor generates
                the explicit `bind(...)` wiring at compile time into
                `GeneratedModules.all()` — no module to edit, and still zero runtime
                reflection. Add a feature by writing an annotated class; provide
                third-party beans (a `DataSource`) with a `@Provides` static method.
                Remove the `annotationProcessor` line in `build.gradle` to switch back
                to hand-written modules.
                """
            : """

                ## Wiring: explicit modules

                `Application` lists modules; a `GreetingModule` owns the greeting
                slice (its `bind(...)` calls and routes). Use `ligero generate` to add
                more — each generator writes the file and wires it into its module.
                Prefer annotations? Regenerate with `--wiring=processor`.
                """;
        return projectReadmeBody(name, db) + wiringSection;
    }

    private static String projectReadmeBody(String name, String db) {
        String dbSection = switch (db) {
            case "postgres" -> """

                ## Database

                `docker compose up` starts PostgreSQL alongside the app
                (schema + sample data from `db/init.sql`). Try `GET /api/greetings`.
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

            A modular [Ligero](https://github.com/ligero-framework/ligero) application.

            ```bash
            gradle run                 # http://localhost:8080  (devtools at /ligero/dev)
            gradle test
            docker compose up --build  # containerized
            ```

            ## Architecture

            The app is organized into **feature modules**. Each module owns one
            slice of the app — its controller, service and repository — and
            declares its own wiring, so `Application` never touches dependency
            injection: it just lists modules.

            ```
            greeting/
              GreetingModule.java          declares the slice's beans + routes
              GreetingController.java       @Controller  — HTTP -> service
              GreetingService.java          interface    — business layer
              DefaultGreetingService.java   @Service
              GreetingRepository.java       interface    — data access
              InMemoryGreetingRepository.java  @Repository
            ```

            ## Generators (auto-registered, like Angular's CLI)

            ```bash
            ligero generate module Orders            # new feature module (registered in Application)
            ligero generate repository Order         # + binding in the module
            ligero generate service Order            # + binding (injects the repository if present)
            ligero generate controller Order         # + binding and route
            ligero generate resource Order           # a whole CRUD slice at once
            ```

            Each generator writes the file **and** wires it into its module for you.

            ## Devtools

            While the app runs, open **http://localhost:8080/ligero/dev**: the bean
            dependency graph and a live trace of every request through the layers
            (arguments, results, timing). Development only — remove `ligero-devtools`
            or set `LIGERO_DEVTOOLS=false` for production.
            %s""".formatted(name, dbSection);
    }

    // ------------------------------------------------------------ application

    static String application(String basePackage, String db) {
        return """
            package %s;

            import %s.greeting.GreetingModule;
            // ligero-cli:imports

            import com.ligero.Ligero;
            import com.ligero.LigeroModule;
            import com.ligero.Modules;
            import com.ligero.beans.Beans;
            import com.ligero.devtools.Devtools;
            import com.ligero.middleware.RequestLoggingMiddleware;

            public class Application {

                public static void main(String[] args) throws Exception {
                    Ligero app = create();
                    app.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
                    System.out.println("Running at  http://localhost:" + app.port());
                    System.out.println("Devtools at http://localhost:" + app.port() + "/ligero/dev");
                }

                /** Assembles the app from its modules — no wiring here, that lives in the modules. */
                public static Ligero create() {
                    Ligero app = Ligero.create(8080);
                    app.use(new RequestLoggingMiddleware());

                    // Visual debugger at /ligero/dev (set LIGERO_DEVTOOLS=false to disable).
                    Devtools devtools = Devtools.create();
                    Beans beans = Modules.install(app, devtools.recorder(), modules());
                    devtools.install(app, beans);

                    return app;
                }

                /** The application's modules. `ligero generate module <Name>` adds one here. */
                static LigeroModule[] modules() {
                    return new LigeroModule[] {
                        new GreetingModule(),
                        // ligero-cli:modules
                    };
                }
            }
            """.formatted(basePackage, basePackage);
    }

    static String applicationProcessor(String basePackage, String db) {
        boolean hasDb = !"none".equals(db);
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
        String isDbUp = hasDb ? """

                private static boolean isDbUp(DataSource dataSource) {
                    try (Connection c = dataSource.getConnection()) {
                        return c.isValid(1);
                    } catch (Exception e) {
                        return false;
                    }
                }
            """ : "";
        return """
            package %s;

            import com.ligero.Ligero;
            import com.ligero.Modules;
            import com.ligero.beans.Beans;
            import com.ligero.devtools.Devtools;
            import com.ligero.generated.GeneratedModules;
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

                /** No wiring here: GeneratedModules is written by ligero-processor from your annotations. */
                public static Ligero create() {
                    Ligero app = Ligero.create(8080);
                    app.use(new RequestLoggingMiddleware());

                    Devtools devtools = Devtools.create();
                    Beans beans = Modules.install(app, devtools.recorder(), GeneratedModules.all());
                    devtools.install(app, beans);
            %s
                    return app;
                }
            %s}
            """.formatted(basePackage, dbImports, health, isDbUp);
    }

    static String greetingConfigProvides(String basePackage, String db) {
        String body = switch (db) {
            case "postgres" -> """

                    @Provides
                    static DataSource dataSource() {
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
            """;
            case "h2" -> """

                    @Provides
                    static DataSource dataSource() {
                        var ds = new org.h2.jdbcx.JdbcDataSource();
                        ds.setURL("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1");
                        try (var c = ds.getConnection(); var s = c.createStatement()) {
                            s.execute("CREATE TABLE IF NOT EXISTS greetings("
                                + "id INT AUTO_INCREMENT PRIMARY KEY, message VARCHAR(200))");
                            s.execute("INSERT INTO greetings(message) VALUES ('Hola desde H2'), ('Hello from H2')");
                        } catch (Exception e) {
                            throw new IllegalStateException("Could not init schema", e);
                        }
                        return ds;
                    }
            """;
            default -> "";
        };
        return """
            package %s.greeting;

            import com.ligero.beans.Provides;

            import javax.sql.DataSource;

            /** Supplies the DataSource bean to the processor via a @Provides factory. */
            public final class GreetingConfig {
            %s}
            """.formatted(basePackage, body);
    }

    static String applicationTest(String basePackage) {
        return """
            package %s;

            import com.ligero.test.LigeroTest;

            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            class ApplicationTest {

                @Test
                void greetingFlowsThroughTheLayers() {
                    try (LigeroTest test = LigeroTest.start(Application.create())) {
                        LigeroTest.TestResponse hello = test.get("/hello/world").execute();
                        assertEquals(200, hello.status());
                        assertTrue(hello.body().contains("world"));

                        LigeroTest.TestResponse list = test.get("/api/greetings").execute();
                        assertEquals(200, list.status());
                    }
                }
            }
            """.formatted(basePackage);
    }

    // --------------------------------------------------- greeting module (new)

    static String greetingModule(String basePackage, String db) {
        boolean hasDb = !"none".equals(db);
        String pkg = basePackage + ".greeting";
        String repoImpl = hasDb ? "JdbcGreetingRepository" : "InMemoryGreetingRepository";
        String repoArg = hasDb ? "b.get(javax.sql.DataSource.class)" : "";
        String dbImports = hasDb ? """
            import com.ligero.middleware.HealthMiddleware;
            import javax.sql.DataSource;
            import java.sql.Connection;
            """ : "";
        String dbBeanBinding = hasDb
            ? "        builder.bind(DataSource.class,          b -> dataSource());\n"
            : "";
        String healthRoute = hasDb
            ? "        app.use(HealthMiddleware.builder()\n"
              + "            .check(\"db\", () -> isDbUp(beans.get(DataSource.class)))\n"
              + "            .build());\n"
            : "";
        String dbHelpers = switch (db) {
            case "postgres" -> POSTGRES_DATASOURCE + IS_DB_UP;
            case "h2" -> H2_DATASOURCE + IS_DB_UP;
            default -> "";
        };
        return """
            package %s;

            import com.ligero.Ligero;
            import com.ligero.LigeroModule;
            import com.ligero.beans.Beans;
            %s
            /** The "greeting" feature: its beans and routes, wired in one place. */
            public final class GreetingModule implements LigeroModule {

                @Override
                public void beans(Beans.Builder builder) {
            %s        builder.bind(GreetingRepository.class,  b -> new %s(%s));
                    builder.bind(GreetingService.class,     b -> new DefaultGreetingService(b.get(GreetingRepository.class)));
                    builder.bind(GreetingController.class,  b -> new GreetingController(b.get(GreetingService.class)));
                    // ligero-cli:beans
                }

                @Override
                public void routes(Ligero app, Beans beans) {
            %s        beans.get(GreetingController.class).register(app);
                    // ligero-cli:routes
                }
            %s}
            """.formatted(pkg, dbImports, dbBeanBinding, repoImpl, repoArg, healthRoute, dbHelpers);
    }

    private static final String POSTGRES_DATASOURCE = """

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
            """;

    private static final String H2_DATASOURCE = """

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
            """;

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

            /** Data-access layer. An interface so implementations swap freely and devtools can trace it. */
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

            @Repository
            public class InMemoryGreetingRepository implements GreetingRepository {

                private final List<String> greetings = new CopyOnWriteArrayList<>(List.of("Hola desde Ligero"));

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
                         PreparedStatement ps = c.prepareStatement("INSERT INTO greetings(message) VALUES (?)")) {
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

            /** Business layer. An interface so devtools can trace calls through it. */
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

                public void register(Ligero app) {
                    app.get("/hello/{name}", ctx ->
                        ctx.json(Map.of("hello", service.greet(ctx.pathParam("name")))));

                    app.group("/api/greetings", api -> {
                        api.get("", ctx -> ctx.json(service.greetings()));
                        api.post("", ctx -> {
                            NewGreeting body = ctx.bodyValidator(NewGreeting.class)
                                .check(g -> g.message() != null && !g.message().isBlank(), "message is required")
                                .get();
                            ctx.status(201).json(Map.of("added", service.add(body.message())));
                        });
                    });
                }
            }
            """.formatted(basePackage);
    }

    // ------------------------------------------------ generic module (generate)

    static String emptyModule(String packageName, String moduleClass) {
        return """
            package %s;

            import com.ligero.Ligero;
            import com.ligero.LigeroModule;
            import com.ligero.beans.Beans;

            /** A feature module: declare this slice's beans and routes here. */
            public final class %s implements LigeroModule {

                @Override
                public void beans(Beans.Builder builder) {
                    // ligero-cli:beans
                }

                @Override
                public void routes(Ligero app, Beans beans) {
                    // ligero-cli:routes
                }
            }
            """.formatted(packageName, moduleClass);
    }

    // ---------------------------------------------- generic layer skeletons

    static String repositoryInterface(String packageName, String name) {
        return """
            package %s;

            import java.util.List;

            /** Data-access layer for %s. Bound as an interface (swappable, traceable). */
            public interface %sRepository {

                List<String> findAll(); // TODO: replace String with your domain type
            }
            """.formatted(packageName, name, name);
    }

    static String inMemoryRepository(String packageName, String name) {
        return """
            package %s;

            import com.ligero.beans.stereotype.Repository;

            import java.util.List;
            import java.util.concurrent.CopyOnWriteArrayList;

            @Repository
            public class InMemory%sRepository implements %sRepository {

                private final List<String> items = new CopyOnWriteArrayList<>();

                @Override
                public List<String> findAll() {
                    return List.copyOf(items);
                }
            }
            """.formatted(packageName, name, name);
    }

    static String serviceInterface(String packageName, String name) {
        return """
            package %s;

            import java.util.List;

            /** Business layer for %s. Bound as an interface so devtools can trace it. */
            public interface %sService {

                List<String> list();
            }
            """.formatted(packageName, name, name);
    }

    static String defaultService(String packageName, String name, boolean withRepository) {
        String field = withRepository
            ? """

                private final %sRepository repository;

                public Default%sService(%sRepository repository) {
                    this.repository = repository;
                }
            """.formatted(name, name, name)
            : """

                public Default%sService() {
                }
            """.formatted(name);
        String body = withRepository
            ? "return repository.findAll();"
            : "return java.util.List.of(); // TODO: implement";
        return """
            package %s;

            import com.ligero.beans.stereotype.Service;

            import java.util.List;

            @Service
            public class Default%sService implements %sService {
            %s
                @Override
                public List<String> list() {
                    %s
                }
            }
            """.formatted(packageName, name, name, field, body);
    }

    static String controllerSkeleton(String packageName, String name) {
        String var = Names.camel(name);
        String route = "/api/" + Names.packageSegment(name) + "s";
        return """
            package %s;

            import com.ligero.Ligero;
            import com.ligero.beans.stereotype.Controller;

            /** Web layer for %s: HTTP in, service out. */
            @Controller
            public class %sController {

                private final %sService %sService;

                public %sController(%sService %sService) {
                    this.%sService = %sService;
                }

                public void register(Ligero app) {
                    app.get("%s", ctx -> ctx.json(%sService.list()));
                    // TODO: add POST / PUT / DELETE routes
                }
            }
            """.formatted(packageName, name, name, name, var, name, name, var, var, var, route, var);
    }

    // ------------------------------------------------------ resource (CRUD)

    static String resourceDomain(String packageName, String name) {
        return """
            package %s;

            /** Domain entity for %s. */
            public record %s(Long id, String name) {
            }
            """.formatted(packageName, name, name);
    }

    static String resourceRepositoryInterface(String packageName, String name) {
        return """
            package %s;

            import java.util.List;
            import java.util.Optional;

            public interface %sRepository {

                List<%s> findAll();

                Optional<%s> findById(long id);

                %s save(%s %s);

                void deleteById(long id);
            }
            """.formatted(packageName, name, name, name, name, name, Names.camel(name));
    }

    static String resourceInMemoryRepository(String packageName, String name) {
        String var = Names.camel(name);
        return """
            package %s;

            import com.ligero.beans.stereotype.Repository;

            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicLong;

            @Repository
            public class InMemory%sRepository implements %sRepository {

                private final Map<Long, %s> store = new ConcurrentHashMap<>();
                private final AtomicLong ids = new AtomicLong();

                @Override
                public List<%s> findAll() {
                    return List.copyOf(store.values());
                }

                @Override
                public Optional<%s> findById(long id) {
                    return Optional.ofNullable(store.get(id));
                }

                @Override
                public %s save(%s %s) {
                    long id = %s.id() != null ? %s.id() : ids.incrementAndGet();
                    %s saved = new %s(id, %s.name());
                    store.put(id, saved);
                    return saved;
                }

                @Override
                public void deleteById(long id) {
                    store.remove(id);
                }
            }
            """.formatted(packageName, name, name, name, name, name, name, name, var,
                var, var, name, name, var);
    }

    static String resourceServiceInterface(String packageName, String name) {
        return """
            package %s;

            import java.util.List;

            public interface %sService {

                List<%s> list();

                %s get(long id);

                %s create(String name);

                void delete(long id);
            }
            """.formatted(packageName, name, name, name, name);
    }

    static String resourceDefaultService(String packageName, String name) {
        String var = Names.camel(name);
        return """
            package %s;

            import com.ligero.beans.stereotype.Service;
            import com.ligero.http.NotFoundException;

            import java.util.List;

            @Service
            public class Default%sService implements %sService {

                private final %sRepository repository;

                public Default%sService(%sRepository repository) {
                    this.repository = repository;
                }

                @Override
                public List<%s> list() {
                    return repository.findAll();
                }

                @Override
                public %s get(long id) {
                    return repository.findById(id)
                        .orElseThrow(() -> new NotFoundException("%s " + id + " not found"));
                }

                @Override
                public %s create(String name) {
                    return repository.save(new %s(null, name));
                }

                @Override
                public void delete(long id) {
                    get(id); // 404 if absent
                    repository.deleteById(id);
                }
            }
            """.formatted(packageName, name, name, name, name, name, name, name, name, name, name);
    }

    static String resourceController(String packageName, String name) {
        String var = Names.camel(name);
        String route = "/api/" + Names.packageSegment(name) + "s";
        return """
            package %s;

            import com.ligero.Ligero;
            import com.ligero.beans.stereotype.Controller;

            @Controller
            public class %sController {

                public record Create%sRequest(String name) {
                }

                private final %sService service;

                public %sController(%sService service) {
                    this.service = service;
                }

                public void register(Ligero app) {
                    app.group("%s", api -> {
                        api.get("", ctx -> ctx.json(service.list()));
                        api.get("/{id}", ctx -> ctx.json(service.get(ctx.pathParamAsLong("id"))));
                        api.post("", ctx -> {
                            Create%sRequest body = ctx.bodyValidator(Create%sRequest.class)
                                .check(r -> r.name() != null && !r.name().isBlank(), "name is required")
                                .get();
                            %s created = service.create(body.name());
                            ctx.status(201).json(created);
                        });
                        api.delete("/{id}", ctx -> {
                            service.delete(ctx.pathParamAsLong("id"));
                            ctx.status(204).res().end();
                        });
                    });
                }
            }
            """.formatted(packageName, name, name, name, name, name, route, name, name, name);
    }

    // ----------------------------------------------------------- docker etc.

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
}
