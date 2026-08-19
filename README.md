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
