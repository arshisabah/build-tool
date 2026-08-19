package com.arshi.plugins;

import com.arshi.api.ArshiProject;
import com.arshi.api.Goal;
import com.arshi.api.GoalExecutionException;
import com.arshi.resolver.ClasspathBuilder;
import com.arshi.resolver.LocalRepository;
import com.arshi.resolver.RemoteResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * "run:run" — runs project.mainClass() with target/classes + every resolved
 * dependency jar on the classpath. This is arshi's equivalent of
 * "mvn spring-boot:run": it does NOT go through the normal lifecycle
 * (validate/compile/test/package/install) — it's invoked directly, the same
 * way Spring Boot's own Maven plugin goal is.
 *
 * For a Spring Boot app, project.mainClass() should point at the
 * @SpringBootApplication class (e.g. com.example.DemoApplication). Because
 * embedded Tomcat and the whole Spring context boot up inside that process,
 * this behaves like a real "run the app" command, not just "run a jar".
 */
public class RunGoal implements Goal {

    @Override
    public String name() {
        return "run:run";
    }

    @Override
    public void execute(ArshiProject project) throws GoalExecutionException {
        if (project.mainClass() == null || project.mainClass().isBlank()) {
            throw new GoalExecutionException(
                "No <mainClass> declared in arshi.xml — add e.g. <mainClass>com.example.DemoApplication</mainClass>");
        }

        // Make sure there's something compiled to run.
        new CompilerGoal().execute(project);
        new ResourcesGoal().execute(project);

        try {
            String classpath = buildRuntimeClasspath(project);

            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-cp");
            command.add(classpath);
            command.add(project.mainClass());

            System.out.println("[arshi] run:run :: " + project.mainClass());
            Process process = new ProcessBuilder(command)
                    .inheritIO()   // stream stdout/stderr live, e.g. Spring Boot's banner + Tomcat startup log
                    .start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GoalExecutionException("Application exited with code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new GoalExecutionException("Failed to run " + project.mainClass(), e);
        }
    }

    private String buildRuntimeClasspath(ArshiProject project) throws IOException, InterruptedException {
        Path classesDir = Path.of("target", "classes").toAbsolutePath();
        StringBuilder cp = new StringBuilder(classesDir.toString());

        if (project.dependencies() != null && !project.dependencies().isEmpty()) {
            RemoteResolver remote = new RemoteResolver(new LocalRepository());
            String depsClasspath = new ClasspathBuilder(remote).buildClasspathString(project.dependencies());
            if (!depsClasspath.isEmpty()) {
                cp.append(java.io.File.pathSeparator).append(depsClasspath);
            }
        }
        return cp.toString();
    }

    private String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        String exeName = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(javaHome, "bin", exeName).toString();
    }
}
