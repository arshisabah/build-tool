package com.arshi.resolver;

import com.arshi.api.Dependency;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a resolved DependencyGraph into an actual classpath: every
 * transitive dependency's jar, fetched to the local repo if needed,
 * joined with the platform path separator — exactly what CompilerGoal
 * needs for "-cp" and RunGoal needs for "java -cp".
 */
public final class ClasspathBuilder {

    private final RemoteResolver remoteResolver;

    public ClasspathBuilder(RemoteResolver remoteResolver) {
        this.remoteResolver = remoteResolver;
    }

    /** Resolves (including transitively) and returns the jar paths for a project's declared dependencies. */
    public List<Path> resolveJars(List<Dependency> declaredDependencies) throws IOException, InterruptedException {
        DependencyGraph graph = new DependencyGraph(remoteResolver);
        graph.resolve(declaredDependencies);

        List<Path> jars = new ArrayList<>();
        for (ResolvedDependency resolved : graph.flatten()) {
            // "test" scope isn't needed to compile/run the app itself
            if ("test".equals(resolved.dependency().scope())) continue;
            jars.add(remoteResolver.fetchJar(resolved.dependency()));
        }
        return jars;
    }

    /** Same as resolveJars, but joined into a single -cp/classpath string using this OS's separator. */
    public String buildClasspathString(List<Dependency> declaredDependencies) throws IOException, InterruptedException {
        List<Path> jars = resolveJars(declaredDependencies);
        StringBuilder sb = new StringBuilder();
        for (Path jar : jars) {
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(jar.toAbsolutePath());
        }
        return sb.toString();
    }
}
