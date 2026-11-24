package com.ligero.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code --controllers typed}: endpoints as annotated methods instead of register(Ligero) lambdas. */
class TypedControllersTest {

    @TempDir
    Path dir;

    private Path newTypedProject(String... extra) {
        String[] args = new String[5 + extra.length];
        args[0] = "new";
        args[1] = "app";
        args[2] = "--package";
        args[3] = "com.acme.app";
        args[4] = "--controllers";
        String[] all = new String[args.length + 1];
        System.arraycopy(args, 0, all, 0, 5);
        all[5] = "typed";
        System.arraycopy(extra, 0, all, 6, extra.length);
        assertThat(LigeroCli.run(dir, all)).isZero();
        return dir.resolve("app");
    }

    @Test
    void newWithTypedControllersGeneratesAnnotatedMethodsAndMountsGeneratedRoutes() throws IOException {
        Path root = newTypedProject();
        Path greeting = root.resolve("src/main/java/com/acme/app/greeting");

        assertThat(Files.readString(greeting.resolve("GreetingController.java")))
            .contains("import com.ligero.web.Get;")
            .contains("@Get(\"/hello/{name}\")")
            .contains("Map<String, String> hello(@PathParam String name)")
            .contains("@Post(\"/api/greetings\")")
            .contains("@Status(201)")
            .contains("@Body NewGreeting body")
            .doesNotContain("register(Ligero app)");
        // explicit wiring: the module mounts the routes class the processor generates
        assertThat(Files.readString(greeting.resolve("GreetingModule.java")))
            .contains("GreetingControllerRoutes.mount(app, beans.get(GreetingController.class));")
            .doesNotContain(".register(app);");
        // ...which is why the processor is on the build even with explicit wiring
        assertThat(Files.readString(root.resolve("build.gradle")))
            .contains("annotationProcessor \"com.ligeroframework:ligero-processor:$ligeroVersion\"");
    }

    @Test
    void lambdaStaysTheDefault() throws IOException {
        assertThat(LigeroCli.run(dir, "new", "app", "--package", "com.acme.app")).isZero();
        Path root = dir.resolve("app");
        assertThat(Files.readString(root.resolve("src/main/java/com/acme/app/greeting/GreetingController.java")))
            .contains("public void register(Ligero app)")
            .doesNotContain("com.ligero.web");
        assertThat(Files.readString(root.resolve("build.gradle"))).doesNotContain("ligero-processor");
    }

    @Test
    void typedControllersCombineWithProcessorWiring() throws IOException {
        Path root = newTypedProject("--wiring", "processor");
        // no hand-written module: the generated one mounts the typed routes itself
        assertThat(root.resolve("src/main/java/com/acme/app/greeting/GreetingModule.java")).doesNotExist();
        assertThat(Files.readString(root.resolve("src/main/java/com/acme/app/greeting/GreetingController.java")))
            .contains("@Get(\"/api/greetings\")");
        String build = Files.readString(root.resolve("build.gradle"));
        assertThat(build.split("ligero-processor", -1)).as("processor listed once").hasSize(2);
    }

    @Test
    void generatorsFollowTheProjectsControllerStyle() throws IOException {
        Path project = newTypedProject();

        assertThat(LigeroCli.run(project, "generate", "resource", "Order")).isZero();

        Path order = project.resolve("src/main/java/com/acme/app/order");
        assertThat(Files.readString(order.resolve("OrderController.java")))
            .contains("@Controller(\"/api/orders\")")
            .contains("List<Order> list()")
            .contains("Order get(@PathParam long id)")
            .contains("Response<Order> create(@Body CreateOrderRequest body)")
            .contains("void delete(@PathParam long id)")
            .doesNotContain("ctx.");
        assertThat(Files.readString(order.resolve("OrderModule.java")))
            .contains("OrderControllerRoutes.mount(app, beans.get(OrderController.class));");
    }

    @Test
    void generateControllerSkeletonCanBeTypedExplicitly() throws IOException {
        // a lambda-style project, but this one controller is requested typed
        assertThat(LigeroCli.run(dir, "new", "app", "--package", "com.acme.app")).isZero();
        Path project = dir.resolve("app");
        assertThat(LigeroCli.run(project, "generate", "service", "Invoice")).isZero();

        assertThat(LigeroCli.run(project, "generate", "controller", "Invoice", "--controllers", "typed")).isZero();

        Path greeting = project.resolve("src/main/java/com/acme/app/greeting");
        assertThat(Files.readString(greeting.resolve("InvoiceController.java")))
            .contains("@Controller(\"/api/invoices\")")
            .contains("@Get");
        assertThat(Files.readString(greeting.resolve("GreetingModule.java")))
            .contains("InvoiceControllerRoutes.mount(app, beans.get(InvoiceController.class));")
            .contains("beans.get(GreetingController.class).register(app);");
    }

    @Test
    void unknownControllerStyleIsRejected() throws IOException {
        assertThat(LigeroCli.run(dir, "new", "app", "--controllers", "magic")).isEqualTo(1);
        assertThat(LigeroCli.run(dir, "new", "app2")).isZero();
        assertThat(LigeroCli.run(dir.resolve("app2"), "generate", "resource", "Order", "--controllers", "magic"))
            .isEqualTo(1);
    }

    @Test
    void javaReservedWordsAreRejectedAsNames() throws IOException {
        assertThat(LigeroCli.run(dir, "new", "app", "--package", "com.acme.app")).isZero();
        Path project = dir.resolve("app");
        for (String reserved : new String[] {"Class", "class", "Record", "String", "Object", "New", "Switch"}) {
            assertThat(LigeroCli.run(project, "generate", "resource", reserved)).as(reserved).isEqualTo(1);
        }
        assertThat(LigeroCli.run(project, "generate", "resource", "Order")).isZero();
    }

    @Test
    void sampleDatabaseIsOnlyPublishedOnLoopback() throws IOException {
        assertThat(LigeroCli.run(dir, "new", "app", "--db", "postgres")).isZero();
        assertThat(Files.readString(dir.resolve("app/docker-compose.yml")))
            .contains("\"127.0.0.1:5432:5432\"")
            .doesNotContain("- \"5432:5432\"");
    }
}
