package com.arshi.resolver;

import com.arshi.api.Dependency;

/** A dependency plus the depth at which it was discovered (used for nearest-wins conflict resolution). */
public record ResolvedDependency(Dependency dependency, int depth) {}
