package com.arshi.resolver;

import com.arshi.api.Dependency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Mirrors Maven's ~/.m2/repository — Arshi's cache lives at ~/.arshi/repository. */
public final class LocalRepository {

    private final Path root;

    public LocalRepository() {
        this(Paths.get(System.getProperty("user.home"), ".arshi", "repository"));
    }

    public LocalRepository(Path root) {
        this.root = root;
    }

    public Path artifactPath(Dependency dep) {
        return root.resolve(dep.groupId().replace('.', '/'))
                   .resolve(dep.artifactId())
                   .resolve(dep.version())
                   .resolve(dep.artifactId() + "-" + dep.version() + ".jar");
    }

    public Path pomPath(Dependency dep) {
        return root.resolve(dep.groupId().replace('.', '/'))
                   .resolve(dep.artifactId())
                   .resolve(dep.version())
                   .resolve(dep.artifactId() + "-" + dep.version() + ".pom");
    }

    public boolean has(Dependency dep) {
        return Files.exists(artifactPath(dep));
    }

    public Path root() {
        return root;
    }
}
