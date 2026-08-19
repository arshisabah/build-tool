package com.arshi.resolver;

import java.util.List;
import java.util.Map;

/** Raw deserialized shape of a fetched .pom file — mirrors just enough of the Maven POM schema to resolve versions. */
record RawPom(
    ParentRef parent,
    Map<String, String> properties,
    DependencyManagementSection dependencyManagement,
    List<RawDependency> dependencies,
    String packaging
) {
    record ParentRef(String groupId, String artifactId, String version) {}

    record DependencyManagementSection(List<RawDependency> dependencies) {}
}
