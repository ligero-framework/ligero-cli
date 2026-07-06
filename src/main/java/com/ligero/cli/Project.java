package com.ligero.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * A generated Ligero project on disk: locates the base package (where
 * {@code Application.java} lives) and the feature modules, so generators can
 * place new files and auto-register them.
 */
final class Project {

    private final Path sourceRoot;
    private final String basePackage;
    private final Path applicationFile;

    private Project(Path sourceRoot, String basePackage, Path applicationFile) {
        this.sourceRoot = sourceRoot;
        this.basePackage = basePackage;
        this.applicationFile = applicationFile;
    }

    /** Locates the project rooted at {@code workingDir}, or fails with guidance. */
    static Project locate(Path workingDir) throws IOException {
        Path sourceRoot = workingDir.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException(
                "src/main/java not found — run this inside a Ligero project.");
        }
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            Path app = paths.filter(p -> p.getFileName().toString().equals("Application.java"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Could not locate Application.java — run this inside a Ligero project."));
            String basePackage = sourceRoot.relativize(app.getParent()).toString()
                .replace(java.io.File.separatorChar, '.');
            return new Project(sourceRoot, basePackage, app);
        }
    }

    String basePackage() {
        return basePackage;
    }

    Path applicationFile() {
        return applicationFile;
    }

    /** Directory backing a package (created lazily by callers when writing). */
    Path packageDir(String packageName) {
        return sourceRoot.resolve(Path.of("", packageName.split("\\.")));
    }

    /**
     * Resolves the module to generate into. With {@code --module x}, uses
     * {@code <base>.x}. Otherwise: if the project has exactly one module, uses
     * it; if it has none or several, asks the user to pass {@code --module}.
     */
    ModuleRef resolveModule(String requested) throws IOException {
        if (requested != null) {
            String segment = Names.packageSegment(requested);
            String moduleClass = Names.pascal(requested) + "Module";
            Path file = packageDir(basePackage + "." + segment).resolve(moduleClass + ".java");
            if (!Files.exists(file)) {
                throw new IllegalArgumentException(
                    "Module '" + moduleClass + "' not found — create it with: ligero generate module "
                    + Names.pascal(requested));
            }
            return new ModuleRef(basePackage + "." + segment, moduleClass, file);
        }
        List<ModuleRef> modules = modules();
        if (modules.size() == 1) {
            return modules.get(0);
        }
        if (modules.isEmpty()) {
            throw new IllegalArgumentException(
                "No modules found. Create one first: ligero generate module <Name>");
        }
        throw new IllegalArgumentException(
            "Several modules exist (" + modules.stream().map(m -> m.className).toList()
            + ") — choose one with --module <Name>");
    }

    /** Every {@code *Module.java} implementing a module, discovered on disk. */
    List<ModuleRef> modules() throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                .filter(p -> p.getFileName().toString().endsWith("Module.java"))
                .map(p -> {
                    String pkg = sourceRoot.relativize(p.getParent()).toString()
                        .replace(java.io.File.separatorChar, '.');
                    String cls = p.getFileName().toString().replace(".java", "");
                    return new ModuleRef(pkg, cls, p);
                })
                .sorted(java.util.Comparator.comparing(m -> m.className))
                .toList();
        }
    }

    /** A feature module located on disk. */
    record ModuleRef(String packageName, String className, Path file) {
    }
}
