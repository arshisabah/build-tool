package com.arshi.resolver;

import com.arshi.api.Dependency;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Computes the "effective" set of a POM's own declared dependencies, with
 * versions fully resolved the way real Maven resolves them:
 *
 *   1. Walk the <parent> chain (starters typically inherit from a parent
 *      that carries no real dependencies, only <properties> and
 *      <dependencyManagement>).
 *   2. Merge in any <dependencyManagement> entries with scope=import,
 *      type=pom (BOM imports) — e.g. spring-boot-dependencies.
 *   3. Substitute ${property} placeholders in version strings using the
 *      merged <properties> map.
 *   4. For a <dependency> with no explicit version, fall back to the
 *      merged dependencyManagement map.
 *
 * Without this step, starter/BOM-based projects (Spring Boot, Micronaut,
 * Quarkus, etc.) silently resolve to zero transitive dependencies, because
 * their versions are declared once in a BOM rather than on every dependency.
 */
public final class PomResolver {

    private static final int MAX_CHAIN_DEPTH = 12;

    private final RemoteResolver remoteResolver;
    private final XmlMapper mapper = XmlMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public PomResolver(RemoteResolver remoteResolver) {
        this.remoteResolver = remoteResolver;
    }

    /** The dependencies this artifact itself declares (not its parent's), with every version resolved. */
    public List<Dependency> resolveDeclaredDependencies(String groupId, String artifactId, String version) {
        try {
            RawPom pom = fetchRawPom(groupId, artifactId, version);

            Map<String, String> props = new LinkedHashMap<>();
            props.put("project.version", version);
            props.put("project.groupId", groupId);
            props.put("project.artifactId", artifactId);

            Map<String, String> managed = new LinkedHashMap<>();
            mergeChain(pom, props, managed, 0, new HashSet<>());

            List<Dependency> result = new ArrayList<>();
            if (pom.dependencies() != null) {
                for (RawDependency raw : pom.dependencies()) {
                    if (!raw.propagatesTransitively()) continue;
                    String resolvedVersion = resolveDependencyVersion(raw, props, managed);
                    if (resolvedVersion == null) continue; // unresolvable — skip rather than fail the whole build
                    String scope = (raw.scope() == null || raw.scope().isBlank()) ? "compile" : raw.scope();
                    result.add(new Dependency(raw.groupId(), raw.artifactId(), resolvedVersion, scope));
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("[arshi] warning: could not resolve dependencies for "
                    + groupId + ":" + artifactId + ":" + version + " — " + e);
            return List.of();
        }
    }

    /** Adds this POM's own properties + dependencyManagement (child-first precedence), then recurses into its parent and any BOM imports. */
    private void mergeChain(RawPom pom, Map<String, String> props, Map<String, String> managed,
                             int depth, Set<String> visited) throws IOException, InterruptedException {
        if (depth > MAX_CHAIN_DEPTH) return;

        if (pom.properties() != null) {
            for (Map.Entry<String, String> e : pom.properties().entrySet()) {
                props.putIfAbsent(e.getKey(), e.getValue());
            }
        }

        if (pom.dependencyManagement() != null && pom.dependencyManagement().dependencies() != null) {
            for (RawDependency raw : pom.dependencyManagement().dependencies()) {
                if (raw.isImportBom()) {
                    String bomVersion = substituteProps(raw.version(), props);
                    if (bomVersion == null || bomVersion.contains("${")) continue;
                    String visitKey = raw.groupId() + ":" + raw.artifactId() + ":" + bomVersion;
                    if (!visited.add(visitKey)) continue;
                    try {
                        RawPom bomPom = fetchRawPom(raw.groupId(), raw.artifactId(), bomVersion);
                        mergeChain(bomPom, props, managed, depth + 1, visited);
                    } catch (Exception ignored) {
                        // BOM unreachable — dependencies it would have managed simply stay unresolved
                    }
                } else {
                    String resolved = substituteProps(raw.version(), props);
                    if (resolved != null && !resolved.contains("${")) {
                        managed.putIfAbsent(raw.key(), resolved);
                    }
                }
            }
        }

        RawPom.ParentRef parent = pom.parent();
        if (parent != null && parent.groupId() != null && parent.artifactId() != null && parent.version() != null) {
            String visitKey = parent.groupId() + ":" + parent.artifactId() + ":" + parent.version();
            if (visited.add(visitKey)) {
                try {
                    RawPom parentPom = fetchRawPom(parent.groupId(), parent.artifactId(), parent.version());
                    mergeChain(parentPom, props, managed, depth + 1, visited);
                } catch (Exception ignored) {
                    // parent unreachable — inherited properties/management from it are simply unavailable
                }
            }
        }
    }

    private String resolveDependencyVersion(RawDependency raw, Map<String, String> props, Map<String, String> managed) {
        String direct = substituteProps(raw.version(), props);
        if (direct != null && !direct.isBlank() && !direct.contains("${")) {
            return direct;
        }
        return managed.get(raw.key());
    }

    private String substituteProps(String template, Map<String, String> props) {
        if (template == null) return null;
        String result = template;
        // Bounded passes handle one level of property-referencing-property nesting without looping forever.
        for (int pass = 0; pass < 3 && result.contains("${"); pass++) {
            for (Map.Entry<String, String> e : props.entrySet()) {
                result = result.replace("${" + e.getKey() + "}", e.getValue());
            }
        }
        return result;
    }

    private RawPom fetchRawPom(String groupId, String artifactId, String version) throws IOException, InterruptedException {
        Dependency asCoordinate = new Dependency(groupId, artifactId, version, "compile");
        Path pomPath = remoteResolver.fetchPom(asCoordinate);
        return mapper.readValue(pomPath.toFile(), RawPom.class);
    }
}
