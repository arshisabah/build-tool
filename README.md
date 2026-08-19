<div align="center">

# arshi

**A Maven-style build tool, written in Java.**

`arshi.xml` instead of `pom.xml`. `arshi clean install` instead of `mvn clean install`.

![Java](https://img.shields.io/badge/Java-17%2B-orange)
![Build](https://img.shields.io/badge/build-Maven-blue)
![Status](https://img.shields.io/badge/status-early--stage-yellow)
![License](https://img.shields.io/badge/license-MIT-green)

</div>

---

## Table of contents

- [Why arshi](#why-arshi)
- [Features](#features)
- [High-Level Design (HLD)](#high-level-design-hld)
  - [1. Introduction](#1-introduction)
  - [2. Objectives](#2-objectives)
  - [3. Scope](#3-scope)
  - [4. System overview](#4-system-overview)
  - [5. Component responsibilities](#5-component-responsibilities)
  - [6. Data flow (end to end)](#6-data-flow-end-to-end)
  - [7. Technology stack](#7-technology-stack)
  - [8. Non-functional considerations](#8-non-functional-considerations)
  - [9. Assumptions and constraints](#9-assumptions-and-constraints)
  - [10. Future direction](#10-future-direction)
- [Architecture](#architecture)
  - [Module map](#module-map)
  - [Module dependency graph](#module-dependency-graph)
  - [Request lifecycle — what happens when you run `arshi package`](#request-lifecycle--what-happens-when-you-run-arshi-package)
  - [Build lifecycle model](#build-lifecycle-model)
  - [Dependency resolution engine](#dependency-resolution-engine)
- [Getting started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Build arshi from source](#build-arshi-from-source)
  - [Install globally](#install-globally)
  - [Adding `arshi` to PATH on another machine](#adding-arshi-to-path-on-another-machine)
- [Usage](#usage)
  - [Plain Java project](#plain-java-project)
  - [Spring Boot project](#spring-boot-project)
  - [`arshi.xml` reference](#arshixml-reference)
  - [Command reference](#command-reference)
- [Extending arshi with a custom plugin](#extending-arshi-with-a-custom-plugin)
- [Known limitations / roadmap](#known-limitations--roadmap)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## Why arshi

Maven is one of the most widely used JVM build tools, but its internals —
lifecycle phases, plugin binding, transitive dependency resolution via
parent POMs and BOMs — are mostly hidden behind `mvn`. **arshi is a
from-scratch, readable reimplementation of those same ideas**, built to
show exactly how each piece works: the declarative project descriptor, the
ordered phase engine, the plugin/goal system, and the dependency resolver
that walks parent POM chains and imported BOMs the way real Maven does.

It's not a drop-in Maven replacement (see [Known limitations](#known-limitations--roadmap)) — it's a
transparent, hackable clone you can read top to bottom in an afternoon.

## Features

- 📄 **`arshi.xml`** project descriptor — same shape as `pom.xml`, deliberately smaller.
- 🔁 **Two lifecycles**, same as Maven: a standalone `clean` lifecycle, and a default
  `validate → compile → test → package → install → deploy` lifecycle.
- 🧩 **Pluggable goals** via `java.util.ServiceLoader` — drop a jar with a `Goal`
  implementation on the classpath and it's auto-discovered, no core changes needed.
- 📦 **Real dependency resolution** — local cache (`~/.arshi/repository`) → Maven
  Central fallback → transitive resolution with parent-POM and BOM-import support
  → nearest-wins conflict resolution.
- ▶️ **`arshi run`** — compile-and-launch, the same role as `mvn spring-boot:run`.
- 🏗️ Produces a directly runnable jar (`Main-Class` + `Class-Path` manifest,
  dependencies copied to `target/lib/`).
- 🌳 **`arshi dependency:tree`** — inspect what actually got resolved, including a
  `[arshi] warning: ...` line on stderr for anything that couldn't be.

## High-Level Design (HLD)

### 1. Introduction

This section describes arshi at the design level — what it's for, what it
does and doesn't cover, how responsibility is split across components, and
how data moves through the system end to end. The [Architecture](#architecture)
section below goes one level deeper into the actual classes; this section
is the "read this first" summary.

### 2. Objectives

- Provide a Maven-equivalent build experience (`arshi.xml`, phased
  lifecycle, plugin/goal model, transitive dependency resolution) that is
  small enough to read and modify in full.
- Keep every major concern — parsing, lifecycle sequencing, dependency
  resolution, and the actual build work — in its own module, so each can
  be understood, tested, and replaced independently.
- Interoperate with the existing Maven ecosystem by reading real `.pom`
  files from Maven Central, rather than inventing a separate package
  registry.

### 3. Scope

**In scope:** project descriptor parsing, an ordered build lifecycle,
built-in goals for cleaning/compiling/resource-copying/jarring/installing,
`arshi run` for launching an application (including Spring Boot apps),
transitive dependency resolution against Maven Central (including parent
POM and BOM/`dependencyManagement` handling), and a ServiceLoader-based
plugin extension point.

**Out of scope (see [Known limitations](#known-limitations--roadmap)):**
version ranges, dependency `<exclusions>`, multi-module reactor builds for
*consumer* projects (arshi itself is multi-module, but a project built
*with* arshi is currently single-module), a real test runner, and a true
merged uber-jar.

### 4. System overview

```
                        ┌─────────────────────────┐
                        │        arshi-cli         │   entry point (picocli)
                        └────────────┬─────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              ▼                      ▼                      ▼
     ┌────────────────┐   ┌───────────────────┐   ┌──────────────────┐
     │   arshi-core     │   │   arshi-plugins    │   │  arshi-resolver   │
     │  (lifecycle +    │◄──│  (concrete goals:  │──►│  (local/remote    │
     │   project model) │   │   clean, compile,  │   │   repos, POM +    │
     │                  │   │   jar, run, ...)    │   │   BOM resolution) │
     └────────┬─────────┘   └─────────────────────┘   └─────────┬─────────┘
              │                                                  │
              └───────────────────┐          ┌────────────────────┘
                                   ▼          ▼
                             ┌──────────────────┐
                             │     arshi-api      │   shared model + Goal SPI
                             └──────────────────┘
```

Every component above `arshi-api` is replaceable in isolation: a new goal
only needs `arshi-api`; a new resolution strategy only touches
`arshi-resolver`; a new CLI surface (e.g. a GUI) could sit on top of
`arshi-core` without touching anything else.

### 5. Component responsibilities

| Component | Responsibility | Does **not** do |
|---|---|---|
| **CLI layer** (`arshi-cli`) | Parse command-line arguments, load `arshi.xml` for the current directory, invoke the right lifecycle phase or goal. | Any actual build logic. |
| **Lifecycle engine** (`arshi-core`) | Hold the fixed phase ordering; run every bound goal up to a target phase; discover available goals via `ServiceLoader`. | Know what a "goal" does internally. |
| **Goal implementations** (`arshi-plugins`) | Do the actual work — delete files, invoke `javac`, copy resources, build a jar, launch a process. | Decide *when* they run (that's the lifecycle engine's job). |
| **Resolver** (`arshi-resolver`) | Turn a declared dependency into a jar on disk: local cache check, remote fetch, parent/BOM version resolution, transitive graph, classpath assembly. | Compile or package anything. |
| **Shared model** (`arshi-api`) | Define the vocabulary (`Phase`, `Dependency`, `ArshiProject`, `Goal`) every other component agrees on. | Contain any logic beyond simple data validation. |

### 6. Data flow (end to end)

1. **Input**: `arshi.xml` on disk in the current working directory.
2. **Parse**: `ProjectLoader` (arshi-core) turns it into an in-memory `ArshiProject`.
3. **Dispatch**: the CLI subcommand picks a target `Phase` (or, for `clean`/`run`, a single `Goal`).
4. **Sequence**: `LifecycleRunner` walks `Phase.values()` up to the target, firing bound `Goal`s.
5. **Resolve** (as needed by a goal): `arshi-resolver` turns `ArshiProject.dependencies()` into actual jar files — checking `~/.arshi/repository` first, falling back to Maven Central, resolving versions via parent POMs/BOMs, and flattening the result with nearest-wins conflict resolution.
6. **Execute**: each `Goal` reads/writes files under `target/` (and, for `install`, `~/.arshi/repository`) or spawns a `java` subprocess (`run:run`).
7. **Output**: compiled classes, a packaged jar (+ `target/lib/`), an installed artifact, or a running application process — depending on which command was invoked.

### 7. Technology stack

| Concern | Choice | Why |
|---|---|---|
| Language / runtime | Java 17+ | Records, `javax.tools.JavaCompiler`, and modern `java.net.http.HttpClient` are all used directly — no reflection tricks needed. |
| CLI parsing | [picocli](https://picocli.info/) | Subcommand support, `--help`/`--version` generation, minimal boilerplate. |
| POM/`arshi.xml` parsing | Jackson (`jackson-dataformat-xml`) | Maps XML straight onto Java records; `FAIL_ON_UNKNOWN_PROPERTIES` disabled specifically for parsing real-world Maven POMs. |
| Plugin discovery | `java.util.ServiceLoader` | JDK-native, no extra dependency, works cleanly once jars are shaded together. |
| Packaging | Maven Shade plugin | Merges all modules + dependencies into one runnable `arshi-cli.jar`, including merging each module's `META-INF/services` entries. |
| Remote repository protocol | Plain HTTPS against Maven Central's static layout | No custom registry to run or maintain. |

### 8. Non-functional considerations

- **Resumability**: `RemoteResolver` treats an already-present local file as
  resolved and skips re-downloading, so repeated builds are fast once the
  local cache is warm.
- **Partial-failure tolerance**: a single unresolvable dependency branch
  (bad BOM, transient network issue) logs a warning to stderr and is
  skipped, rather than aborting the whole dependency graph resolution.
- **Extensibility**: adding a goal requires zero changes to `arshi-core` —
  only a new `Goal` implementation plus a `META-INF/services` entry.
- **Portability**: `ClasspathBuilder` and `RunGoal` use
  `java.io.File.pathSeparator` and `System.getProperty("java.home")` rather
  than hardcoded paths, so the same code path works on Windows, macOS, and
  Linux.

### 9. Assumptions and constraints

- A dependency's remote `.pom` is reachable over plain HTTPS at Maven
  Central's conventional URL layout; private/internal repositories would
  need `RemoteResolver`'s base URL reconfigured.
- Source layout is fixed at `src/main/java` / `src/main/resources` — not
  yet read from `arshi.xml`.
- Only one artifact is built per `arshi.xml` — there's no multi-module
  aggregation for projects built *with* arshi (arshi's own build is
  multi-module, but that's a property of arshi's own repository, not a
  capability it exposes to consumers yet).

### 10. Future direction

See [Known limitations / roadmap](#known-limitations--roadmap) for the
concrete backlog (version ranges, exclusions, a real test runner, a true
uber-jar, configurable source layout). At the design level, none of these
require changing the module boundaries above — they're all extensions
within `arshi-resolver` or `arshi-plugins`.

---

## Architecture

### Module map

arshi is a 5-module Maven reactor — yes, real Maven builds arshi itself,
once, as a bootstrap step. Each module has exactly one job:

```
arshi/
├── pom.xml                 # parent POM — bootstraps arshi with real Maven
│
├── arshi-api/               # public contract — shared model + plugin SPI
│   └── Phase, CleanPhase, Dependency, PluginBinding,
│     ArshiProject, Goal, GoalExecutionException
│
├── arshi-core/               # parses arshi.xml, runs the lifecycle engine
│   └── ProjectLoader, LifecycleRunner, PluginRegistry
│
├── arshi-resolver/           # dependency resolution
│   └── LocalRepository, RemoteResolver, PomResolver,
│     RawPom, RawDependency, DependencyGraph,
│     ResolvedDependency, ClasspathBuilder, DependencyTreePrinter
│
├── arshi-plugins/            # built-in goals (the actual work)
│   └── CleanGoal, ResourcesGoal, CompilerGoal, JarGoal,
│     InstallGoal, SurefireGoal, RunGoal
│   └── src/main/resources/META-INF/services/com.arshi.api.Goal
│
├── arshi-cli/                 # the "arshi" executable (picocli)
│   └── ArshiCli, CleanCommand, CompileCommand, TestCommand,
│     PackageCommand, InstallCommand, DependencyCommand, RunCommand
│
├── bin/arshi                  # shell wrapper: java -jar arshi-cli.jar "$@"
│
├── sample-project/            # plain Java example (no external deps)
└── spring-boot-sample/        # Spring Boot example (external deps + run)
```

### Module dependency graph

Arrows point "depends on" — this is what keeps the system layered instead
of tangled:

```
arshi-cli
   │  depends on
   ▼
arshi-core ──────────┐
   │  depends on      │  depends on
   ▼                  ▼
arshi-api  ◄──── arshi-resolver
   ▲                  ▲
   │  depends on       │  depends on
   └──── arshi-plugins ┘
```

| Module | Depends on | Responsibility |
|---|---|---|
| `arshi-api` | *(nothing)* | Data model (`ArshiProject`, `Dependency`, `Phase`) + the `Goal` interface. Zero build logic. Anyone writing a custom plugin depends only on this. |
| `arshi-resolver` | `arshi-api` | Local/remote repositories, parent-POM + BOM resolution, transitive graph, classpath building. |
| `arshi-plugins` | `arshi-api`, `arshi-resolver` | Concrete `Goal` implementations — clean, compile, resources, jar, install, run, test. |
| `arshi-core` | `arshi-api` | `ArshiProject` XML parsing + the phase-ordered `LifecycleRunner`. Discovers goals from `arshi-plugins` at *runtime* via `ServiceLoader`, never at compile time. |
| `arshi-cli` | all of the above | picocli subcommands; shades everything into one runnable `arshi-cli.jar`. |

Because `arshi-core` never compile-time-depends on `arshi-plugins`, the set
of available goals is entirely runtime-pluggable — the same architectural
trick Maven itself uses for third-party plugins.

### Request lifecycle — what happens when you run `arshi package`

```
 you type: arshi package
      │
      ▼
 ArshiCli (picocli root) ─── dispatches to ──▶ PackageCommand
      │                                              │
      │                                 CommandSupport.loadProject()
      │                                              │
      │                                              ▼
      │                                   ProjectLoader.load("arshi.xml")
      │                                        (arshi-core, Jackson XML)
      │                                              │
      │                                              ▼
      │                                        ArshiProject (in memory)
      │                                              │
      ▼                                              ▼
 LifecycleRunner.run(PACKAGE, project, PluginRegistry.defaultBindings())
      │
      │  walks Phase.values() in order, stopping after PACKAGE:
      │
      ├─ VALIDATE   (no goals bound by default)
      ├─ COMPILE    → ResourcesGoal.execute(project)
      │              → CompilerGoal.execute(project)
      │                    │
      │                    ▼
      │              needs a classpath ──▶ RemoteResolver + PomResolver
      │                                    + DependencyGraph + ClasspathBuilder
      │                                    (arshi-resolver — fetches jars from
      │                                     ~/.arshi/repository or Maven Central)
      │                    │
      │                    ▼
      │              javac -cp <resolved classpath> -d target/classes ...
      ├─ TEST       → SurefireGoal.execute(project)   (stub today)
      └─ PACKAGE    → JarGoal.execute(project)
                       → zips target/classes into target/<artifact>-<version>.jar
                       → copies dependency jars into target/lib/
                       → writes Main-Class + Class-Path into the manifest
      │
      ▼
 [arshi] BUILD SUCCESS
```

`arshi clean` and `arshi run` bypass this diagram — `clean` is its own
single-phase lifecycle (`CleanCommand` calls `CleanGoal` directly), and
`run` isn't part of the lifecycle at all (`RunCommand` calls `RunGoal`
directly), exactly like Maven's `clean` lifecycle and `spring-boot:run`
goal.

### Build lifecycle model

Like Maven, arshi has **two separate lifecycles**:

1. **`clean`** — a single-phase lifecycle. `arshi clean` deletes `target/`
   and does nothing else. It never triggers compile/test/package.
2. **the default lifecycle** — an ordered sequence of phases:

   ```
   VALIDATE → COMPILE → TEST → PACKAGE → INSTALL → DEPLOY
   ```

Running `arshi package` doesn't just run the `package` phase — it runs
**every phase up to and including it**, in order. `LifecycleRunner` does
this with a simple ordinal comparison against a fixed `Phase` enum, then
fires whatever `Goal`s are bound to each phase:

| Phase     | Goal(s) bound by default            |
|-----------|--------------------------------------|
| COMPILE   | `resources:copy`, `compiler:compile` |
| TEST      | `surefire:test`                      |
| PACKAGE   | `jar:jar`                             |
| INSTALL   | `install:install`                     |

`arshi run` is **not** part of this lifecycle at all — same as Maven's
`spring-boot:run`, it's a standalone goal invoked directly. It compiles
first (calling `CompilerGoal`/`ResourcesGoal` directly, not through the
lifecycle), then launches the app in a new JVM process.

### Dependency resolution engine

This is the part that makes real-world projects (anything using Spring
Boot, Guava, etc.) actually compile and run, and it's the most involved
piece of the whole tool.

**1. Local-first lookup.** `LocalRepository` mirrors Maven's `~/.m2` —
arshi's cache lives at
`~/.arshi/repository/<groupId path>/<artifactId>/<version>/`. Every
resolution checks here before touching the network.

**2. Remote fetch.** `RemoteResolver` fetches both the `.jar` and the
`.pom` for a coordinate from a Maven-layout remote (Maven Central by
default), using its well-known static URL structure:

```
https://repo1.maven.org/maven2/<groupId with / instead of .>/<artifactId>/<version>/<artifactId>-<version>.jar
```

**3. Parent POMs, BOMs, and property placeholders.** Real Maven POMs
almost never state an explicit `<version>` on every `<dependency>`. Spring
Boot starters, for example, get their versions from:

- a **parent POM** chain (`spring-boot-starter-web` → `spring-boot-starters` → ...),
- an **imported BOM** (`<dependencyManagement>` with `<scope>import</scope>`,
  `<type>pom</type>`, e.g. `spring-boot-dependencies`), and
- `${property}` placeholders resolved against a merged `<properties>` map.

`PomResolver` replicates this:

1. Fetches the dependency's own `.pom`.
2. Walks its `<parent>` chain (bounded depth, cycle-safe).
3. Merges every `<dependencyManagement>` section it finds — including
   recursively resolving imported BOMs — with **child-first precedence**.
4. Substitutes `${...}` placeholders in version strings using the merged
   `<properties>` map.
5. For each declared `<dependency>` with no explicit version, looks up
   `groupId:artifactId` in the merged management map.

The `XmlMapper` used here has `FAIL_ON_UNKNOWN_PROPERTIES` disabled — real
POMs contain dozens of elements (`<scm>`, `<licenses>`, `<build>`,
`<profiles>`, ...) the `RawPom` model doesn't need to understand.

**4. Transitive resolution + conflict resolution.** `DependencyGraph`
recursively resolves each dependency's own dependencies (via
`PomResolver`), tracking the **depth** at which each `groupId:artifactId`
was first reached. If the same artifact is reachable at two different
depths, **nearest wins** — the same default strategy Maven uses.

**5. Building a classpath.** `ClasspathBuilder` walks the resolved graph,
ensures every jar is downloaded, and joins the paths with the platform's
path separator — what `CompilerGoal` passes to `javac -cp` and `RunGoal`
passes to `java -cp`.

If a branch can't be resolved (unreachable BOM, network issue), arshi logs
`[arshi] warning: could not resolve dependencies for ...` to stderr and
continues with what it could resolve.

---

## Getting started

### Prerequisites

- **JDK 17+** — needed both to *build* arshi and to *run* it. arshi uses
  the in-JVM `javax.tools.JavaCompiler` API, so the runtime JVM must be a
  full JDK, not just a JRE.
- **Maven 3.6+** — only to bootstrap arshi itself the first time. Once
  built, arshi doesn't need Maven installed to build other projects.
- Internet access to `repo1.maven.org` (Maven Central) for dependency
  resolution, or point `RemoteResolver` at an internal mirror.

### Build arshi from source

```bash
git clone https://github.com/<your-username>/arshi.git
cd arshi
mvn clean install
```

This builds all 5 modules in reactor order and produces one shaded,
runnable jar:

```
arshi-cli/target/arshi-cli.jar
```

### Install globally

<details>
<summary><strong>Linux / macOS</strong></summary>

```bash
mkdir -p ~/.arshi/bin
cp arshi-cli/target/arshi-cli.jar ~/.arshi/bin/arshi-cli.jar
cp bin/arshi ~/.arshi/bin/arshi
chmod +x ~/.arshi/bin/arshi
echo 'export PATH="$HOME/.arshi/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```
</details>

<details>
<summary><strong>Windows (PowerShell)</strong></summary>

```powershell
mkdir $HOME\.arshi\bin -Force
copy arshi-cli\target\arshi-cli.jar $HOME\.arshi\bin\arshi-cli.jar

@'
@echo off
java -jar "%~dp0arshi-cli.jar" %*
'@ | Out-File -Encoding ascii $HOME\.arshi\bin\arshi.bat

[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "User") + ";$HOME\.arshi\bin",
    "User"
)
```
</details>

Open a **new** terminal (PATH changes don't apply to already-open shells)
and verify:

```bash
arshi --help
```

> **Reinstalling after a code change:** rebuild and re-copy the jar over
> the old global install — the wrapper script always points at
> `arshi-cli.jar` next to itself, so it silently keeps running the old
> version otherwise.
> ```bash
> mvn clean install -rf :<module-that-changed>
> cp arshi-cli/target/arshi-cli.jar ~/.arshi/bin/arshi-cli.jar
> ```

### Adding `arshi` to PATH on another machine

If you've already copied `arshi-cli.jar` and the wrapper script into
`~/.arshi/bin` (or `%USERPROFILE%\.arshi\bin` on Windows) on a new machine
but `arshi` isn't recognized as a command, add that folder to PATH using
whichever method matches your shell.

<details>
<summary><strong>Windows — GUI method</strong></summary>

1. Press `Win`, type `env`, open **"Edit environment variables for your account"**.
2. Under **User variables**, select `Path`, click **Edit**.
3. Click **New**, enter `%USERPROFILE%\.arshi\bin`.
4. Click OK on every dialog, then close and reopen your terminal.
</details>

<details>
<summary><strong>Windows — PowerShell (run once)</strong></summary>

```powershell
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "User") + ";$HOME\.arshi\bin",
    "User"
)
```
</details>

<details>
<summary><strong>Windows — Command Prompt (run once)</strong></summary>

```cmd
setx PATH "%PATH%;%USERPROFILE%\.arshi\bin"
```
</details>

<details>
<summary><strong>macOS / Linux — bash</strong></summary>

```bash
echo 'export PATH="$HOME/.arshi/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```
</details>

<details>
<summary><strong>macOS — zsh (default on newer macOS)</strong></summary>

```bash
echo 'export PATH="$HOME/.arshi/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```
</details>

<details>
<summary><strong>Git Bash on Windows</strong></summary>

Git Bash reads `~/.bashrc` too, so the same command as macOS/Linux bash
works — `$HOME` resolves to your Windows user profile:

```bash
echo 'export PATH="$HOME/.arshi/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

You only need this **or** the PowerShell method above, not both — they
both add the same folder to the same underlying Windows PATH.
</details>

Verify in a **new** terminal window (PATH changes never apply to a
terminal that was already open):

```bash
arshi --help
```

> **Order matters:** the jar and wrapper script must already exist at
> `~/.arshi/bin` (or `%USERPROFILE%\.arshi\bin`) *before* you add that
> folder to PATH — copy `arshi-cli.jar` and the wrapper (`arshi` or
> `arshi.bat`) there first, then set PATH, then open a fresh terminal.

---

## Usage

### Plain Java project

```bash
cd my-project        # contains arshi.xml, src/main/java/...
arshi clean
arshi compile
arshi package         # -> target/my-app-1.0.0.jar
arshi install          # -> copies into ~/.arshi/repository
arshi dependency:tree
```

### Spring Boot project

Two things `arshi.xml` needs that plain projects don't: `mainClass`, and
the starter dependency itself.

```xml
<mainClass>com.example.demo.DemoApplication</mainClass>
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.3.0</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

```bash
arshi run
```

This compiles first (resolving the starter's full transitive dependency
tree — Tomcat, Jackson, Spring core, etc.), then launches `mainClass` in
its own process with `inheritIO()`, so the real Spring Boot banner and
embedded Tomcat startup log stream live in your terminal.

For a deployable artifact instead of "run in place":

```bash
arshi clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

`arshi package` copies every resolved dependency jar into `target/lib/`
and writes `Main-Class` + `Class-Path` into the manifest, so the jar runs
directly with `java -jar` **as long as `target/lib/` sits next to it**.
This is a classpath-manifest jar, not a merged uber-jar — simpler, and it
avoids resource collisions (e.g. duplicate `META-INF/spring.factories`)
that naive uber-jar merging runs into with Spring Boot specifically.

### `arshi.xml` reference

```xml
<project>
    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <!-- Required for "arshi run" and for a directly-runnable packaged jar -->
    <mainClass>com.example.Main</mainClass>

    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>33.0.0-jre</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>
</project>
```

Source layout is fixed (not yet configurable):

```
src/main/java/...          # compiled by compiler:compile
src/main/resources/...     # copied by resources:copy
```

### Command reference

| Command              | What it runs                                         |
|-----------------------|-------------------------------------------------------|
| `arshi clean`         | Deletes `target/` (standalone lifecycle)              |
| `arshi compile`       | validate → compile                                    |
| `arshi test`          | validate → compile → test                             |
| `arshi package`       | validate → compile → test → package                   |
| `arshi install`       | validate → compile → test → package → install         |
| `arshi run`           | compile, then launch `mainClass` (not part of the lifecycle) |
| `arshi dependency:tree` | Print the resolved transitive dependency tree       |

---

## Extending arshi with a custom plugin

Implement `com.arshi.api.Goal`:

```java
public class MyGoal implements Goal {
    public String name() { return "my:goal"; }
    public void execute(ArshiProject project) throws GoalExecutionException {
        // ...
    }
}
```

List it in a `META-INF/services/com.arshi.api.Goal` file on your plugin
jar's classpath, and drop that jar next to `arshi-cli.jar`.
`PluginRegistry` discovers it automatically via `ServiceLoader` — no
changes to arshi's own source needed.

---

## Known limitations / roadmap

- [ ] **Version ranges** — `PomResolver` resolves a single concrete version
      per coordinate; Maven-style `[1.0,2.0)` ranges aren't supported yet.
- [ ] **`<exclusions>`** aren't honored yet — a transitive dependency can't
      currently be excluded from `arshi.xml`.
- [ ] **`surefire:test` is a stub** — prints a placeholder instead of
      discovering and running `@Test`-annotated classes. Wiring it to the
      JUnit Platform Launcher API is the next real gap to close.
- [ ] **No true uber-jar** — `arshi package` produces a jar + `target/lib/`
      folder, not a single self-contained fat jar.
- [ ] **Fixed source layout** — `src/main/java` and `src/main/resources`
      are hardcoded, not configurable via `arshi.xml`.

Contributions closing any of these are very welcome — see
[Contributing](#contributing).

---

## Troubleshooting

- **"Unmatched argument" on a command that should exist** — you're running
  a stale global `arshi-cli.jar`. Rebuild and re-copy it (see
  [Install globally](#install-globally)).
- **"package X does not exist" during compile** — check
  `arshi dependency:tree` first. If a branch is missing entirely, look for
  a `[arshi] warning: could not resolve dependencies for ...` line on
  stderr — it names the exact coordinate that failed and why.
- **Build errors in arshi's own source** — make sure you're on the latest
  commit; a couple of early bugs (missing `-parameters` compiler flag,
  wrong `Comparator` import, `FAIL_ON_UNKNOWN_PROPERTIES` left enabled on
  the POM-parsing `XmlMapper`) were found and fixed during initial
  development.

---

## Contributing

Issues and PRs are welcome. Good first areas to dig into are anything in
[Known limitations](#known-limitations--roadmap) — each is a
self-contained, well-scoped piece of work:

1. Fork the repo and create a branch off `main`.
2. `mvn clean install` to make sure the baseline build passes.
3. Make your change in the relevant module (see the [module map](#module-map)
   to figure out where it belongs).
4. Open a PR describing what changed and why.

## License

MIT — see `LICENSE` for details.
