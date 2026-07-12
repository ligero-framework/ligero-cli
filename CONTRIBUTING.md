# Contributing to the Ligero CLI

Thanks for your interest! `ligero-cli` scaffolds and maintains Ligero projects.
It is written in **plain Java (Java 21+) with zero runtime dependencies** — the
same "ligero" ethos as the framework it serves.

## Design rules

1. **No runtime dependencies.** The CLI ships as a single native binary (GraalVM)
   and a JVM distribution; keep it dependency-free and reflection-free so it
   builds native cleanly.
2. **Templates are type-checked Java.** Generated code lives in `Templates.java`;
   keep it valid, formatted Java so what users get is what we test.
3. **Generators auto-wire.** Every `generate` command writes the file **and**
   registers it (binding, route, module entry) via the `// ligero-cli:*` anchors
   in `SourceEditor` — no manual wiring step for the user.
4. **Pin, don't float.** Generated projects pin an exact published framework
   version (`Templates.LIGERO_VERSION`); it is bumped automatically by the
   `framework-bump` workflow, never resolved dynamically.

## Development

```bash
./gradlew test          # unit tests (project generation + generators)
./gradlew installDist   # build the CLI under build/install/ligero/bin/ligero
./gradlew nativeCompile # GraalVM native binary (needs a GraalVM toolchain)
```

Run the freshly built CLI against a scratch dir:

```bash
build/install/ligero/bin/ligero new demo --package com.acme.demo --db h2
```

## Branching strategy

The same convention applies across all four repos (`ligero`, `ligero-cli`,
`ligero-examples`, `ligero-docs`).

- **Trunk-based.** `main` is always releasable and protected — never push to it
  directly. Every change lands through a reviewed PR.
- **Branch from the latest `main`.** Name it by intent: `feature/<name>`,
  `fix/<name>`, `docs/<name>`, `chore/<name>`, `release/x.y.z`.
- **One PR = one logical change, based directly on `main`.** Keep it small and
  avoid stacked PRs.
- **Squash-merge**, then delete the branch.

## Pull requests

1. Branch from `main`; keep commits focused, messages imperative.
2. Add/adjust tests in `LigeroCliTest` for any change to generation or wiring.
3. Update `CHANGELOG.md` under *Unreleased*.
4. CI (build + tests) must be green.

## Releases

The CLI is tag-driven: publishing a GitHub Release (`vX.Y.Z`) builds the native
binaries + installers and opens the next `-SNAPSHOT` bump PR. Cut a CLI release
on a framework **MINOR/MAJOR** or when the CLI's own behaviour changes — not for
every framework patch (the `framework-bump` PR keeps the pinned version current
in between).
