# arshi

A Maven-style build tool: `arshi.xml` instead of `pom.xml`, `arshi clean` /
`arshi install` instead of `mvn clean` / `mvn install`.

## Project layout

```
arshi/
├── pom.xml                 # parent POM bootstrapping arshi itself (Maven builds Maven-like tool, once)
├── arshi-api/               # public model + Goal SPI — third-party plugins depend on this only
│   └── src/main/java/com/arshi/api/
│       ├── ArshiProject.java
│       ├── Dependency.java
│       ├── Phase.java
│       ├── CleanPhase.java
│       ├── PluginBinding.java
│       ├── Goal.java
│       └── GoalExecutionException.java
│
├── arshi-core/               # arshi.xml parsing + lifecycle engine
│   └── src/main/java/com/arshi/core/
│       ├── ProjectLoader.java
│       ├── LifecycleRunner.java
│       └── PluginRegistry.java
│
├── arshi-resolver/           # local/remote repositories + transitive dependency graph
│   └── src/main/java/com/arshi/resolver/
│       ├── LocalRepository.java
│       ├── RemoteResolver.java
│       ├── ResolvedDependency.java
│       ├── DependencyGraph.java
│       └── DependencyTreePrinter.java
│
├── arshi-plugins/            # built-in goal implementations, wired via ServiceLoader
│   └── src/main/java/com/arshi/plugins/
│       ├── CleanGoal.java          -> clean:clean
│       ├── ResourcesGoal.java      -> resources:copy
│       ├── CompilerGoal.java       -> compiler:compile
│       ├── JarGoal.java            -> jar:jar
│       ├── InstallGoal.java        -> install:install
│       ├── SurefireGoal.java       -> surefire:test  (stub, see Milestone 7)
│       └── resources/META-INF/services/com.arshi.api.Goal
│
├── arshi-cli/                 # the "arshi" executable (picocli subcommands)
│   └── src/main/java/com/arshi/cli/
│       ├── ArshiCli.java
│       ├── CleanCommand.java
│       ├── CompileCommand.java
│       ├── TestCommand.java
│       ├── PackageCommand.java
│       ├── InstallCommand.java
│       └── DependencyCommand.java
│
├── bin/
│   └── arshi                 # shell wrapper: `java -jar arshi-cli.jar "$@"`
│
└── sample-project/           # a tiny project you can build WITH arshi once it compiles
    ├── arshi.xml
    └── src/main/java/com/example/Main.java
```

## Build arshi itself (bootstrap step, one time, using real Maven)

```bash
mvn clean install
```

This produces `arshi-cli/target/arshi-cli.jar` (a shaded, runnable jar
containing every module + picocli + jackson).

## Install arshi globally

```bash
mkdir -p ~/.arshi/bin
cp arshi-cli/target/arshi-cli.jar ~/.arshi/bin/arshi-cli.jar
cp bin/arshi ~/.arshi/bin/arshi
chmod +x ~/.arshi/bin/arshi
echo 'export PATH="$HOME/.arshi/bin:$PATH"' >> ~/.bashrc && source ~/.bashrc
```

## Try it on the sample project

```bash
cd sample-project
arshi clean
arshi compile
arshi package
arshi install
arshi dependency:tree
```

## Running a Spring Boot project

See `spring-boot-sample/` for a working example. Two things are required in
`arshi.xml` that plain jar projects don't need:

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

Then, from that project's directory:

```bash
arshi run
```

This compiles the project (resolving `spring-boot-starter-web` and all of
its transitive dependencies — Tomcat, Jackson, Spring core, etc. — onto the
classpath first), then launches `mainClass` in a new `java` process with
`inheritIO()`, so you see the real Spring Boot banner, embedded Tomcat
startup log, and any `System.out` from your app live in your terminal —
same experience as `mvn spring-boot:run`.

For a deployable artifact instead of "run in place":

```bash
arshi clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

`arshi package` now also copies every dependency jar into `target/lib/` and
writes `Main-Class` + `Class-Path` into the jar's manifest, so the packaged
jar is directly runnable with `java -jar` as long as `target/lib/` sits next
to it. This is a classpath-manifest jar, not a merged uber-jar — simpler,
and it avoids the resource-collision issues (e.g. duplicate
`META-INF/spring.factories`) that naive uber-jar merging runs into with
Spring Boot specifically. If you outgrow it and want a single self-contained
jar with no external `lib/` folder, that's the next milestone (a proper
`spring-boot:repackage`-style nested-jar loader).

## Extending arshi with a custom plugin

Implement `com.arshi.api.Goal`, list the class in a
`META-INF/services/com.arshi.api.Goal` file on your plugin jar's classpath,
and drop that jar next to `arshi-cli.jar`. `PluginRegistry` discovers it
automatically via `ServiceLoader` — no core code changes needed.

See `arshi-build-tool-guide.md` for the full step-by-step design writeup.




#############################################################
The modules, one at a time

arshi-api — the contract everything else agrees on. It has zero build logic itself, just plain data classes (ArshiProject, Dependency, Phase) and one interface (Goal). This is the module a third party would depend on if they wanted to write a custom plugin for arshi — they'd never need arshi-core or arshi-plugins, just this. Think of it as Maven's maven-plugin-api.

arshi-core — the engine, but dumb by design. ProjectLoader turns arshi.xml into an ArshiProject object. LifecycleRunner just walks the Phase enum in order and fires whatever goals are bound to each phase — it has no idea what "compile" or "jar" actually means, it just calls Goal.execute(). PluginRegistry is the glue: it uses ServiceLoader to discover every Goal implementation sitting on the classpath and binds the built-in ones to their default phases.

arshi-resolver — handles anything dependency-related. LocalRepository knows the folder layout under ~/.arshi/repository. RemoteResolver fetches jars/poms from Maven Central's URL structure when they're not cached locally. DependencyGraph recursively resolves transitive dependencies with nearest-wins conflict resolution. DependencyTreePrinter is just the pretty-printer behind arshi dependency:tree.

arshi-plugins — the actual work. Each class (CleanGoal, CompilerGoal, ResourcesGoal, JarGoal, InstallGoal, SurefireGoal) implements Goal from arshi-api and does one concrete thing — delete target/, invoke javac, zip a jar, etc. They're registered in META-INF/services/com.arshi.api.Goal, which is the file PluginRegistry's ServiceLoader.load() call reads. Nothing in arshi-core references these classes directly — that's what makes them swappable/extendable.

arshi-cli — the thin shell around all of the above. ArshiCli is the picocli root command; CleanCommand, CompileCommand, etc. are subcommands that just load the project and call into arshi-core/arshi-resolver. This module's pom.xml also runs the shade plugin, which is why building it produces one fat runnable arshi-cli.jar containing every module.

bin/arshi (or your arshi.bat on Windows) — not Java at all, just a wrapper script that runs java -jar arshi-cli.jar "$@". Its only job is to exist somewhere on your PATH so typing arshi from any directory works.