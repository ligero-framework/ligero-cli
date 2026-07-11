<p align="center">
  <img src="logo.svg" alt="Ligero CLI" width="360">
</p>

<p align="center">
  Scaffolding CLI for the <a href="https://github.com/ligero-framework/ligero">Ligero web framework</a> —
  generate modular, ready-to-run apps and wire features for you.
</p>

<p align="center">
  <em>Zero runtime dependencies · Java 21+</em>
</p>

<p align="center">
  <a href="https://ligero-framework.github.io/ligero-docs/">📖 Documentation</a> ·
  <a href="https://ligero-framework.github.io/ligero-docs/getting-started/cli">CLI guide</a>
</p>

---

## Install

**macOS / Linux** — native binary, no JVM required:

```bash
curl -fsSL https://github.com/ligero-framework/ligero-cli/releases/latest/download/install.sh | sh
```

**Windows** (PowerShell):

```powershell
irm https://github.com/ligero-framework/ligero-cli/releases/latest/download/install.ps1 | iex
```

Both download the native `ligero` for your OS into `~/.ligero/bin` and add it to your PATH.

<details>
<summary>Other ways</summary>

- **Manual download**: grab the binary for your OS (or the `ligero-<version>.zip` that needs a JVM) from the [latest release](https://github.com/ligero-framework/ligero-cli/releases/latest), put it on your PATH.
- **From source**: `./gradlew installDist` then add `build/install/ligero/bin` to your PATH.
- **SDKMAN!** *(planned)*: `sdk install ligero`.
</details>

## Usage

```bash
ligero new my-api --package com.acme.api   # ready-to-run project with tests
cd my-api && gradle run                     # http://localhost:8080

ligero generate controller User             # CRUD controller with validation
ligero version
```

`ligero new` generates: Gradle build wired to `ligero-core`/`ligero-server-jdk`/`ligero-json`,
an `Application` with routes and middleware, and an end-to-end test using `ligero-test`.

> **Note:** the generated project resolves Ligero from `mavenLocal()` until the framework
> is published to Maven Central — run `./gradlew publishToMavenLocal` in the framework repo first.

## License

Apache 2.0
