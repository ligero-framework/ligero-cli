package com.ligero.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LigeroCliTest {

    @TempDir
    Path dir;

    // ---------------------------------------------------------------- new

    @Test
    void newGeneratesModularProject() throws IOException {
        int exit = LigeroCli.run(dir, "new", "my-api", "--package", "com.acme.api");

        assertThat(exit).isZero();
        Path root = dir.resolve("my-api");
        assertThat(root.resolve("settings.gradle")).exists();
        assertThat(Files.readString(root.resolve("build.gradle")))
            .contains("com.ligero:ligero-core")
            .contains("com.ligero:ligero-devtools")
            .contains("mainClass = 'com.acme.api.Application'");
        // Application lists modules, no wiring inline
        assertThat(Files.readString(root.resolve("src/main/java/com/acme/api/Application.java")))
            .contains("Modules.install(app, devtools.recorder(), modules())")
            .contains("new GreetingModule()")
            .contains(SourceEditor.MODULES_ANCHOR);
        // the greeting slice
        Path greeting = root.resolve("src/main/java/com/acme/api/greeting");
        assertThat(Files.readString(greeting.resolve("GreetingModule.java")))
            .contains("implements LigeroModule")
            .contains("builder.bind(GreetingController.class")
            .contains(SourceEditor.BEANS_ANCHOR)
            .contains(SourceEditor.ROUTES_ANCHOR);
        assertThat(greeting.resolve("GreetingService.java")).exists();
        assertThat(greeting.resolve("DefaultGreetingService.java")).exists();
        assertThat(greeting.resolve("GreetingRepository.java")).exists();
        assertThat(greeting.resolve("InMemoryGreetingRepository.java")).exists();
        assertThat(Files.readString(greeting.resolve("GreetingController.java"))).contains("@Controller");
        assertThat(root.resolve("src/test/java/com/acme/api/ApplicationTest.java")).exists();
    }

    @Test
    void newGeneratesDockerAssets() throws IOException {
        LigeroCli.run(dir, "new", "dockered", "--package", "com.acme.d");
        Path root = dir.resolve("dockered");
        assertThat(Files.readString(root.resolve("Dockerfile")))
            .contains("gradle installDist").contains("bin/dockered");
        assertThat(Files.readString(root.resolve("docker-compose.yml")))
            .contains("build: .").doesNotContain("postgres");
    }

    @Test
    void newWithPostgresWiresDbInTheModule() throws IOException {
        LigeroCli.run(dir, "new", "pgapp", "--package", "com.acme.pg", "--db", "postgres");
        Path root = dir.resolve("pgapp");
        assertThat(Files.readString(root.resolve("docker-compose.yml")))
            .contains("postgres:16-alpine").contains("init.sql").contains("service_healthy");
        assertThat(Files.readString(root.resolve("db/init.sql"))).contains("greetings");
        assertThat(Files.readString(root.resolve("build.gradle"))).contains("org.postgresql:postgresql");
        // DB wiring lives in the module, not in Application
        assertThat(Files.readString(root.resolve("src/main/java/com/acme/pg/greeting/GreetingModule.java")))
            .contains("PGSimpleDataSource").contains("HealthMiddleware").contains("JdbcGreetingRepository");
        assertThat(root.resolve("src/main/java/com/acme/pg/greeting/JdbcGreetingRepository.java")).exists();
    }

    @Test
    void newWithH2WiresInMemoryDb() throws IOException {
        LigeroCli.run(dir, "new", "h2app", "--package", "com.acme.h2app", "--db", "h2");
        Path root = dir.resolve("h2app");
        assertThat(Files.readString(root.resolve("build.gradle"))).contains("com.h2database:h2");
        assertThat(Files.readString(root.resolve("src/main/java/com/acme/h2app/greeting/GreetingModule.java")))
            .contains("jdbc:h2:mem:app").contains("initSchema");
    }

    @Test
    void newRejectsUnknownDb() {
        assertThat(LigeroCli.run(dir, "new", "bad", "--db", "oracle")).isEqualTo(1);
    }

    @Test
    void newDefaultsPackageFromName() throws IOException {
        assertThat(LigeroCli.run(dir, "new", "shop")).isZero();
        assertThat(dir.resolve("shop/src/main/java/com/example/shop/Application.java")).exists();
    }

    @Test
    void newRefusesExistingDirectoryAndBadNames() throws IOException {
        Files.createDirectory(dir.resolve("taken"));
        assertThat(LigeroCli.run(dir, "new", "taken")).isEqualTo(1);
        assertThat(LigeroCli.run(dir, "new", "1bad name")).isEqualTo(1);
        assertThat(LigeroCli.run(dir, "new", "ok", "--package", "Bad.Package")).isEqualTo(1);
    }

    // ----------------------------------------------------------- generators

    private Path newProject() {
        LigeroCli.run(dir, "new", "app", "--package", "com.acme.app");
        return dir.resolve("app");
    }

    @Test
    void generateModuleRegistersItInApplication() throws IOException {
        Path project = newProject();

        assertThat(LigeroCli.run(project, "generate", "module", "Billing")).isZero();

        assertThat(project.resolve("src/main/java/com/acme/app/billing/BillingModule.java")).exists();
        assertThat(Files.readString(project.resolve("src/main/java/com/acme/app/Application.java")))
            .contains("import com.acme.app.billing.BillingModule;")
            .contains("new BillingModule(),");
    }

    @Test
    void generateResourceCreatesAWiredCrudSlice() throws IOException {
        Path project = newProject();

        assertThat(LigeroCli.run(project, "generate", "resource", "Order")).isZero();

        Path order = project.resolve("src/main/java/com/acme/app/order");
        assertThat(order.resolve("Order.java")).exists();
        assertThat(order.resolve("OrderRepository.java")).exists();
        assertThat(order.resolve("InMemoryOrderRepository.java")).exists();
        assertThat(order.resolve("OrderService.java")).exists();
        assertThat(order.resolve("DefaultOrderService.java")).exists();
        assertThat(order.resolve("OrderController.java")).exists();
        // module wired: all three bindings + the route + registered in Application
        assertThat(Files.readString(order.resolve("OrderModule.java")))
            .contains("builder.bind(OrderRepository.class, b -> new InMemoryOrderRepository());")
            .contains("builder.bind(OrderService.class, b -> new DefaultOrderService(b.get(OrderRepository.class)));")
            .contains("builder.bind(OrderController.class, b -> new OrderController(b.get(OrderService.class)));")
            .contains("beans.get(OrderController.class).register(app);");
        assertThat(Files.readString(project.resolve("src/main/java/com/acme/app/Application.java")))
            .contains("new OrderModule(),");
    }

    @Test
    void generateLayersIntoTheSingleModuleAndWiresThem() throws IOException {
        Path project = newProject();
        Path greetingModule = project.resolve("src/main/java/com/acme/app/greeting/GreetingModule.java");

        // repository -> service (injects repo) -> controller (route), all into greeting
        assertThat(LigeroCli.run(project, "generate", "repository", "Invoice")).isZero();
        assertThat(LigeroCli.run(project, "generate", "service", "Invoice")).isZero();
        assertThat(LigeroCli.run(project, "generate", "controller", "Invoice")).isZero();

        Path greeting = project.resolve("src/main/java/com/acme/app/greeting");
        assertThat(greeting.resolve("InvoiceRepository.java")).exists();
        assertThat(greeting.resolve("DefaultInvoiceService.java")).exists();
        assertThat(greeting.resolve("InvoiceController.java")).exists();
        assertThat(Files.readString(greetingModule))
            .contains("builder.bind(InvoiceRepository.class, b -> new InMemoryInvoiceRepository());")
            .contains("builder.bind(InvoiceService.class, b -> new DefaultInvoiceService(b.get(InvoiceRepository.class)));")
            .contains("builder.bind(InvoiceController.class, b -> new InvoiceController(b.get(InvoiceService.class)));")
            .contains("beans.get(InvoiceController.class).register(app);");
    }

    @Test
    void generateServiceWithoutRepositoryUsesNoArgConstructor() throws IOException {
        Path project = newProject();

        assertThat(LigeroCli.run(project, "generate", "service", "Standalone")).isZero();

        assertThat(Files.readString(project.resolve("src/main/java/com/acme/app/greeting/GreetingModule.java")))
            .contains("builder.bind(StandaloneService.class, b -> new DefaultStandaloneService());");
    }

    @Test
    void generateControllerRequiresItsService() throws IOException {
        Path project = newProject();
        // no PaymentService yet
        assertThat(LigeroCli.run(project, "generate", "controller", "Payment")).isEqualTo(1);
    }

    @Test
    void generateIntoNamedModule() throws IOException {
        Path project = newProject();
        LigeroCli.run(project, "generate", "module", "Billing");

        // two modules now exist -> must disambiguate with --module
        assertThat(LigeroCli.run(project, "generate", "repository", "Invoice")).isEqualTo(1);
        assertThat(LigeroCli.run(project, "generate", "repository", "Invoice", "--module", "Billing")).isZero();
        assertThat(project.resolve("src/main/java/com/acme/app/billing/InvoiceRepository.java")).exists();
    }

    @Test
    void generateOutsideProjectFails() {
        assertThat(LigeroCli.run(dir, "generate", "controller", "User")).isEqualTo(1);
    }

    @Test
    void generateRejectsUnknownKindAndBadName() throws IOException {
        Path project = newProject();
        assertThat(LigeroCli.run(project, "generate", "widget", "Foo")).isEqualTo(1);
        assertThat(LigeroCli.run(project, "generate", "service", "1bad")).isEqualTo(1);
    }

    @Test
    void versionAndHelpAndUnknown() {
        assertThat(LigeroCli.run(dir, "version")).isZero();
        assertThat(LigeroCli.run(dir, "help")).isZero();
        assertThat(LigeroCli.run(dir, "wat")).isEqualTo(1);
        assertThat(LigeroCli.run(dir)).isEqualTo(1);
    }
}
