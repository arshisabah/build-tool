package com.arshi.api;

public record Dependency(
    String groupId,
    String artifactId,
    String version,
    String scope   // compile | test | provided | runtime
) {
    public Dependency {
        if (scope == null || scope.isBlank()) {
            scope = "compile";
        }
    }

    public String coordinateKey() {
        return groupId + ":" + artifactId;
    }
}
