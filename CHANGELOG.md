# Changelog

All notable changes to the Ligero CLI are documented here. The format is based
on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **`ligero add <module>`** — wire an optional module into the current project:
  adds the dependency (Groovy or Kotlin DSL, when the module isn't in
  `ligero-core`) and drops a starter `<Module>Config` in `<base>.config`.
  Supports **`scheduler`**, **`cache`**, **`redis`**, **`resilience`**,
  **`auth`**, **`events`**, **`jdbc`** (with **`--pool`** for HikariCP),
  **`mcp`**, and **`query`** (the built-in HTTP method — prints usage). Idempotent.
- `--devtools true|false` option for `ligero new` — the devtools workbench is
  scaffolded **on by default**; pass `--devtools false` to skip it.

### Changed
- Generated projects now target **Ligero 0.7.0** (scheduler, cache, resilience,
  asymmetric JWT, events, pooling, MCP, the QUERY method).
- A single `ligeroVersion` ext property in the generated `build.gradle`, so every
  Ligero dependency reads one variable instead of repeating the version.
- `framework-bump` workflow: a `repository_dispatch` from a framework release
  opens a PR bumping the framework version `ligero new` pins.

### Changed
- Generated `main()` no longer emits `System.out.println` boilerplate — the
  framework logs the startup line, the devtools mount and (when present) the
  OpenAPI path itself.
- The CLI's own version now lives in `gradle.properties`; release CI overrides it
  from the tag and auto-opens the next `-SNAPSHOT` bump PR.

### Docs
- Processor-wiring projects note that `com.ligero.generated.GeneratedModules` is
  generated at compile time (IDEs flag it until the first build).

## [0.1.0]

### Added
- `ligero new` — scaffolds a modular, layered, Docker-ready project (with
  `--package`, `--db none|h2|postgres`, `--wiring explicit|processor`).
- `ligero generate module|controller|service|repository|resource` — generators
  that write the files **and** auto-wire them into their module.
- `ligero dev` — watch/rebuild/restart loop for local development.
- Native binaries (GraalVM) for Linux/macOS/Windows plus install scripts, built
  and attached to each GitHub Release.

[Unreleased]: https://github.com/ligero-framework/ligero-cli/compare/0.1.0...HEAD
[0.1.0]: https://github.com/ligero-framework/ligero-cli/releases/tag/0.1.0
