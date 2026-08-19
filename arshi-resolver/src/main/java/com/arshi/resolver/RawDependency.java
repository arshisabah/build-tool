package com.arshi.resolver;

/** Raw <dependency> shape as it appears inside a fetched remote .pom (before version resolution). */
record RawDependency(
    String groupId,
    String artifactId,
    String version,   // may be null (managed elsewhere) or a ${property} placeholder
    String scope,
    String type,       // "pom" marks a BOM import when scope=import
    String optional
) {
    boolean isImportBom() {
        return "import".equals(scope) && "pom".equals(type);
    }

    boolean propagatesTransitively() {
        boolean isOptional = "true".equalsIgnoreCase(optional);
        boolean excludedScope = "test".equals(scope) || "provided".equals(scope) || isImportBom();
        return !isOptional && !excludedScope;
    }

    String key() {
        return groupId + ":" + artifactId;
    }
}
