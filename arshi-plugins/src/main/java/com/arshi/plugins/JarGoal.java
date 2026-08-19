package com.arshi.plugins;

import com.arshi.api.ArshiProject;
import com.arshi.api.Goal;
import com.arshi.api.GoalExecutionException;
import com.arshi.resolver.ClasspathBuilder;
import com.arshi.resolver.LocalRepository;
import com.arshi.resolver.RemoteResolver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * "jar:jar" — packages target/classes into target/<artifact>-<version>.jar.
 *
 * Also copies every resolved dependency jar into target/lib/ and, if
 * project.mainClass() is set, writes Main-Class + a Class-Path manifest
 * entry pointing at those lib/ jars — so "java -jar target/xxx.jar" runs
 * the app standalone (Spring Boot's embedded server included) as long as
 * target/lib/ travels alongside the jar. This is a plain classpath-manifest
 * jar, not a merged uber-jar — simpler, and avoids dependency resource
 * collisions that uber-jars are prone to.
 */
public class JarGoal implements Goal {

    @Override
    public String name() {
        return "jar:jar";
    }

    @Override
    public void execute(ArshiProject project) throws GoalExecutionException {
        Path classesDir = Path.of("target", "classes");
        Path jarPath = Path.of("target", project.artifactId() + "-" + project.version() + ".jar");

        try {
            List<String> libEntries = copyDependenciesToLib(project);
            Manifest manifest = buildManifest(project, libEntries);

            try (OutputStream fos = Files.newOutputStream(jarPath);
                 JarOutputStream jos = new JarOutputStream(fos, manifest)) {

                if (Files.exists(classesDir)) {
                    try (Stream<Path> walk = Files.walk(classesDir)) {
                        for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                            String entryName = classesDir.relativize(path).toString().replace('\\', '/');
                            jos.putNextEntry(new JarEntry(entryName));
                            Files.copy(path, jos);
                            jos.closeEntry();
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new GoalExecutionException("Failed to build jar", e);
        }
        System.out.println("[arshi] built " + jarPath
                + (project.mainClass() != null ? " (runnable, Main-Class: " + project.mainClass() + ")" : ""));
    }

    /** Copies every resolved dependency jar into target/lib/, returning "lib/<filename>.jar" entries for the manifest. */
    private List<String> copyDependenciesToLib(ArshiProject project) throws IOException, InterruptedException {
        if (project.dependencies() == null || project.dependencies().isEmpty()) return List.of();

        Path libDir = Path.of("target", "lib");
        Files.createDirectories(libDir);

        RemoteResolver remote = new RemoteResolver(new LocalRepository());
        List<Path> jars = new ClasspathBuilder(remote).resolveJars(project.dependencies());

        for (Path jar : jars) {
            Files.copy(jar, libDir.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        return jars.stream().map(j -> "lib/" + j.getFileName()).collect(Collectors.toList());
    }

    private Manifest buildManifest(ArshiProject project, List<String> libEntries) {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (project.mainClass() != null && !project.mainClass().isBlank()) {
            attrs.put(Attributes.Name.MAIN_CLASS, project.mainClass());
        }
        if (!libEntries.isEmpty()) {
            attrs.put(Attributes.Name.CLASS_PATH, String.join(" ", libEntries));
        }
        return manifest;
    }
}
