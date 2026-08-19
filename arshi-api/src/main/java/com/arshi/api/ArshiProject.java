package com.arshi.api;

import java.util.List;

/** In-memory representation of a parsed arshi.xml. */
public record ArshiProject(
    String groupId,
    String artifactId,
    String version,
    String packaging,
    String mainClass,          // e.g. com.example.DemoApplication — required for "arshi run" and a runnable jar
    List<Dependency> dependencies,
    List<PluginBinding> plugins
) {
    public String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
