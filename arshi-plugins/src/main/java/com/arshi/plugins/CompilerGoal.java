package com.arshi.plugins;

import com.arshi.api.ArshiProject;
import com.arshi.api.Goal;
import com.arshi.api.GoalExecutionException;
import com.arshi.resolver.ClasspathBuilder;
import com.arshi.resolver.LocalRepository;
import com.arshi.resolver.RemoteResolver;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * "compiler:compile" — compiles src/main/java into target/classes using the
 * in-JVM javac API, with declared dependencies resolved onto the classpath
 * first. Without this step, any project with external dependencies
 * (Guava, Spring Boot starters, anything) fails to compile with
 * "package X does not exist".
 */
public class CompilerGoal implements Goal {

    @Override
    public String name() {
        return "compiler:compile";
    }

    @Override
    public void execute(ArshiProject project) throws GoalExecutionException {
        Path srcDir = Path.of("src", "main", "java");
        Path outDir = Path.of("target", "classes");

        try {
            Files.createDirectories(outDir);
            List<String> sources = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(srcDir)) {
                walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> sources.add(p.toString()));
            }
            if (sources.isEmpty()) return;

            String classpath = buildClasspath(project);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            List<String> args = new ArrayList<>(List.of("-d", outDir.toString()));
            if (!classpath.isEmpty()) {
                args.add("-cp");
                args.add(classpath);
            }
            args.addAll(sources);

            int result = compiler.run(null, System.out, System.err, args.toArray(new String[0]));
            if (result != 0) {
                throw new GoalExecutionException("Compilation failed with exit code " + result);
            }
        } catch (IOException | InterruptedException e) {
            throw new GoalExecutionException("Compilation failed", e);
        }
    }

    private String buildClasspath(ArshiProject project) throws IOException, InterruptedException {
        if (project.dependencies() == null || project.dependencies().isEmpty()) return "";
        RemoteResolver remote = new RemoteResolver(new LocalRepository());
        return new ClasspathBuilder(remote).buildClasspathString(project.dependencies());
    }
}
