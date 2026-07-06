package com.ligero.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code ligero generate <kind> <Name>}: creates an artifact and
 * auto-registers it in its module (bindings, routes and, for modules, in
 * {@code Application.modules()}) — the same "write it and wire it" workflow
 * as Angular's CLI.
 *
 * <pre>
 * ligero generate module     Billing
 * ligero generate repository Order   [--module Orders]
 * ligero generate service    Order   [--module Orders]
 * ligero generate controller Order   [--module Orders]
 * ligero generate resource   Order            # module + repository + service + controller, wired
 * </pre>
 */
final class GenerateCommand {

    int run(Path workingDir, List<String> args) throws IOException {
        if (args.size() < 2) {
            throw new IllegalArgumentException(
                "Usage: ligero generate <module|controller|service|repository|resource> <Name> [--module <Name>]");
        }
        String kind = args.get(0);
        String name = Names.pascal(Names.requireIdentifier(args.get(1), kind));
        Project project = Project.locate(workingDir);
        String moduleOption = LigeroCli.option(args, "--module");

        switch (kind) {
            case "module" -> generateModule(project, name);
            case "repository" -> generateRepository(project, project.resolveModule(moduleOption), name);
            case "service" -> generateService(project, project.resolveModule(moduleOption), name);
            case "controller" -> generateController(project, project.resolveModule(moduleOption), name);
            case "resource" -> generateResource(project, name);
            default -> throw new IllegalArgumentException(
                "Unknown generator '" + kind + "'. Try: module, controller, service, repository, resource");
        }
        return 0;
    }

    // ------------------------------------------------------------------ module

    private void generateModule(Project project, String name) throws IOException {
        String moduleClass = name + "Module";
        String segment = Names.packageSegment(name);
        String packageName = project.basePackage() + "." + segment;
        Path file = project.packageDir(packageName).resolve(moduleClass + ".java");
        writeNew(file, Templates.emptyModule(packageName, moduleClass));
        registerModule(project, packageName, moduleClass);
        System.out.println("  registered " + moduleClass + " in Application.modules()");
    }

    /** Adds {@code new XModule()} to Application.modules() and imports it. */
    private void registerModule(Project project, String packageName, String moduleClass) throws IOException {
        SourceEditor.ensureImport(project.applicationFile(),
            "import " + packageName + "." + moduleClass + ";");
        SourceEditor.insertBeforeAnchor(project.applicationFile(),
            SourceEditor.MODULES_ANCHOR, "new " + moduleClass + "(),");
    }

    // -------------------------------------------------------------- repository

    private void generateRepository(Project project, Project.ModuleRef module, String name) throws IOException {
        Path dir = project.packageDir(module.packageName());
        writeNew(dir.resolve(name + "Repository.java"),
            Templates.repositoryInterface(module.packageName(), name));
        writeNew(dir.resolve("InMemory" + name + "Repository.java"),
            Templates.inMemoryRepository(module.packageName(), name));
        bind(module, name + "Repository", "new InMemory" + name + "Repository()");
    }

    // ----------------------------------------------------------------- service

    private void generateService(Project project, Project.ModuleRef module, String name) throws IOException {
        Path dir = project.packageDir(module.packageName());
        boolean hasRepo = Files.exists(dir.resolve(name + "Repository.java"));
        writeNew(dir.resolve(name + "Service.java"),
            Templates.serviceInterface(module.packageName(), name));
        writeNew(dir.resolve("Default" + name + "Service.java"),
            Templates.defaultService(module.packageName(), name, hasRepo));
        String construction = hasRepo
            ? "new Default" + name + "Service(b.get(" + name + "Repository.class))"
            : "new Default" + name + "Service()";
        bind(module, name + "Service", construction);
    }

    // -------------------------------------------------------------- controller

    private void generateController(Project project, Project.ModuleRef module, String name) throws IOException {
        Path dir = project.packageDir(module.packageName());
        if (!Files.exists(dir.resolve(name + "Service.java"))) {
            throw new IllegalArgumentException(
                name + "Service not found in module " + module.className()
                + " — generate it first (ligero g service " + name + ") or use ligero g resource " + name);
        }
        writeNew(dir.resolve(name + "Controller.java"),
            Templates.controllerSkeleton(module.packageName(), name));
        bind(module, name + "Controller", "new " + name + "Controller(b.get(" + name + "Service.class))");
        route(module, name);
    }

    // ----------------------------------------------------------------- resource

    private void generateResource(Project project, String name) throws IOException {
        // A resource is its own module holding a full CRUD slice.
        String moduleClass = name + "Module";
        String segment = Names.packageSegment(name);
        String packageName = project.basePackage() + "." + segment;
        Path dir = project.packageDir(packageName);

        writeNew(dir.resolve(moduleClass + ".java"), Templates.emptyModule(packageName, moduleClass));
        registerModule(project, packageName, moduleClass);

        writeNew(dir.resolve(name + ".java"), Templates.resourceDomain(packageName, name));
        writeNew(dir.resolve(name + "Repository.java"), Templates.resourceRepositoryInterface(packageName, name));
        writeNew(dir.resolve("InMemory" + name + "Repository.java"), Templates.resourceInMemoryRepository(packageName, name));
        writeNew(dir.resolve(name + "Service.java"), Templates.resourceServiceInterface(packageName, name));
        writeNew(dir.resolve("Default" + name + "Service.java"), Templates.resourceDefaultService(packageName, name));
        writeNew(dir.resolve(name + "Controller.java"), Templates.resourceController(packageName, name));

        Project.ModuleRef module = new Project.ModuleRef(packageName, moduleClass, dir.resolve(moduleClass + ".java"));
        bind(module, name + "Repository", "new InMemory" + name + "Repository()");
        bind(module, name + "Service", "new Default" + name + "Service(b.get(" + name + "Repository.class))");
        bind(module, name + "Controller", "new " + name + "Controller(b.get(" + name + "Service.class))");
        route(module, name);
        System.out.println("  registered " + moduleClass + " in Application.modules()");
    }

    // -------------------------------------------------------------- registration

    /** Inserts a {@code builder.bind(Type.class, b -> ...);} into the module's beans(). */
    private void bind(Project.ModuleRef module, String type, String construction) throws IOException {
        SourceEditor.insertBeforeAnchor(module.file(), SourceEditor.BEANS_ANCHOR,
            "builder.bind(" + type + ".class, b -> " + construction + ");");
        System.out.println("  wired  " + type + " into " + module.className());
    }

    /** Inserts a {@code beans.get(XController.class).register(app);} into the module's routes(). */
    private void route(Project.ModuleRef module, String name) throws IOException {
        SourceEditor.insertBeforeAnchor(module.file(), SourceEditor.ROUTES_ANCHOR,
            "beans.get(" + name + "Controller.class).register(app);");
    }

    private void writeNew(Path file, String content) throws IOException {
        if (Files.exists(file)) {
            throw new IllegalArgumentException("File already exists: " + file);
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        System.out.println("  create " + file);
    }
}
