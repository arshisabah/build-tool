package com.arshi.resolver;

import com.arshi.api.Dependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the full transitive dependency set for a project.
 * Conflict strategy: "nearest wins" (same default as Maven) — if the same
 * groupId:artifactId is reachable at two depths, the shallower one wins.
 */
public final class DependencyGraph {

    private final Map<String, ResolvedDependency> resolved = new HashMap<>();
    private final Map<String, List<Dependency>> children = new HashMap<>();
    private final PomResolver pomResolver;

    public DependencyGraph(RemoteResolver remoteResolver) {
        this.pomResolver = new PomResolver(remoteResolver);
    }

    public void resolve(List<Dependency> rootDependencies) {
        for (Dependency dep : rootDependencies) {
            resolveRecursive(dep, 1);
        }
    }

    private void resolveRecursive(Dependency dep, int depth) {
        String key = dep.coordinateKey();
        ResolvedDependency existing = resolved.get(key);
        if (existing != null && existing.depth() <= depth) {
            return; // an equal-or-closer resolution already wins this slot
        }
        resolved.put(key, new ResolvedDependency(dep, depth));

        // Walks dep's parent POM chain + any imported BOMs to resolve versions
        // that Maven-style projects normally leave implicit (see PomResolver).
        List<Dependency> transitive = pomResolver.resolveDeclaredDependencies(
                dep.groupId(), dep.artifactId(), dep.version());
        children.put(key, transitive);
        for (Dependency child : transitive) {
            resolveRecursive(child, depth + 1);
        }
    }

    public List<ResolvedDependency> flatten() {
        return new ArrayList<>(resolved.values());
    }

    public List<Dependency> childrenOf(Dependency dep) {
        return children.getOrDefault(dep.coordinateKey(), List.of());
    }
}
