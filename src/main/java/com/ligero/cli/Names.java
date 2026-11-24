package com.ligero.cli;

/** Name normalization shared by the scaffolder and the generators. */
final class Names {

    /** java.lang types a generated class must not shadow. */
    private static final java.util.Set<String> RESERVED_TYPES = java.util.Set.of(
        "Object", "String", "Class", "Record", "Enum", "Thread", "System", "Module",
        "Integer", "Long", "Double", "Boolean", "Void", "Error", "Exception", "Override");

    private Names() {
    }

    /** {@code "product"}, {@code "Product"} -> {@code "Product"}. */
    static String pascal(String raw) {
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /** {@code "Product"}, {@code "product"} -> {@code "product"}. */
    static String camel(String raw) {
        return Character.toLowerCase(raw.charAt(0)) + raw.substring(1);
    }

    /** Lowercase, letters/digits only — the sub-package a module and its layers live in. */
    static String packageSegment(String raw) {
        return raw.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** Rejects anything that is not a bare identifier (letters then letters/digits). */
    static String requireIdentifier(String raw, String what) {
        if (raw == null || !raw.matches("[A-Za-z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("Invalid " + what + " name: " + raw);
        }
        // "class", "Record", "Object"... would produce code that does not compile
        // or shadows java.lang; javax.lang.model knows the reserved words.
        if (javax.lang.model.SourceVersion.isKeyword(camel(raw)) || RESERVED_TYPES.contains(pascal(raw))) {
            throw new IllegalArgumentException(
                "'" + raw + "' is reserved in Java — pick another " + what + " name");
        }
        return raw;
    }
}
