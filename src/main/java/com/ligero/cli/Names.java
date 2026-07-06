package com.ligero.cli;

/** Name normalization shared by the scaffolder and the generators. */
final class Names {

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
        return raw;
    }
}
