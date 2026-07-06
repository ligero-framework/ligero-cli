package com.ligero.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ligero scaffolding CLI. Zero runtime dependencies, plain argument parsing.
 *
 * <pre>
 * ligero new my-api [--package com.example.api]
 * ligero generate controller User [--package com.example.api]
 * ligero version | help
 * </pre>
 */
public final class LigeroCli {

    static final String VERSION = "0.1.0";

    public static void main(String[] args) {
        int exit = run(Path.of("."), args);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    /** Entry point with injectable working directory (testable). */
    static int run(Path workingDir, String... args) {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
            System.out.println(usage());
            return args.length == 0 ? 1 : 0;
        }
        try {
            return switch (args[0]) {
                case "version", "--version" -> {
                    System.out.println("ligero-cli " + VERSION);
                    yield 0;
                }
                case "new" -> new NewCommand().run(workingDir, rest(args));
                case "generate", "g" -> new GenerateCommand().run(workingDir, rest(args));
                default -> {
                    System.err.println("Unknown command: " + args[0] + "\n\n" + usage());
                    yield 1;
                }
            };
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            return 1;
        }
    }

    private static List<String> rest(String[] args) {
        return new ArrayList<>(Arrays.asList(args).subList(1, args.length));
    }

    static String usage() {
        return """
            Ligero CLI — scaffolding for the Ligero web framework

            Usage:
              ligero new <project-name> [--package <base.package>] [--db none|h2|postgres] [--wiring explicit|processor]
              ligero generate <kind> <Name> [--module <Name>]

            Wiring (ligero new):
              explicit   (default) hand-written modules with bind(...); `ligero generate` weaves into them
              processor  annotate classes; ligero-processor generates the bind(...) at compile time
              ligero version
              ligero help

            Generators (each writes the file AND auto-registers it in its module):
              module <Name>       an empty feature module (registered in Application)
              repository <Name>   interface + in-memory impl, bound in the module
              service <Name>      interface + default impl (injects the repository if present)
              controller <Name>   a controller with a route, bound in the module
              resource <Name>     a whole CRUD slice: module + repository + service + controller

            Examples:
              ligero new my-api --package com.acme.api --db h2
              ligero generate resource Order
              ligero generate module Billing
              ligero generate service Invoice --module Billing
            """;
    }

    /** Extracts the value of a --flag from the argument list (null if absent). */
    static String option(List<String> args, String flag) {
        int index = args.indexOf(flag);
        if (index < 0) {
            return null;
        }
        if (index + 1 >= args.size()) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args.get(index + 1);
    }
}
