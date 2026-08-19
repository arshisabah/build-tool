package com.arshi.resolver;

import com.arshi.api.ArshiProject;
import com.arshi.api.Dependency;

/** Implements "arshi dependency:tree" — mirrors mvn's diagnostic output. */
public final class DependencyTreePrinter {

    public void print(ArshiProject project, DependencyGraph graph) {
        System.out.println(project.gav());
        for (Dependency dep : project.dependencies()) {
            printNode(dep, graph, 1);
        }
    }

    private void printNode(Dependency dep, DependencyGraph graph, int depth) {
        System.out.println("  ".repeat(depth) + "+- " + dep.groupId() + ":" + dep.artifactId()
                + ":" + dep.version() + " (" + dep.scope() + ")");
        for (Dependency child : graph.childrenOf(dep)) {
            printNode(child, graph, depth + 1);
        }
    }
}
