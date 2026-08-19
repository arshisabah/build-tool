package com.arshi.core;

import com.arshi.api.Goal;
import com.arshi.api.Phase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Maps each Phase to the ordered list of Goals bound to it.
 * Built-in bindings are the defaults; a project's arshi.xml <build><plugins>
 * section can add to or override them.
 */
public final class PluginRegistry {

    public static Map<Phase, List<Goal>> defaultBindings() {
        Map<String, Goal> byName = discoverGoals();
        Map<Phase, List<Goal>> bindings = new LinkedHashMap<>();

        bind(bindings, Phase.COMPILE, byName, "resources:copy", "compiler:compile");
        bind(bindings, Phase.TEST,    byName, "surefire:test");
        bind(bindings, Phase.PACKAGE, byName, "jar:jar");
        bind(bindings, Phase.INSTALL, byName, "install:install");

        return bindings;
    }

    /** Discovers Goal implementations via java.util.ServiceLoader (see META-INF/services). */
    private static Map<String, Goal> discoverGoals() {
        Map<String, Goal> goals = new LinkedHashMap<>();
        for (Goal goal : ServiceLoader.load(Goal.class)) {
            goals.put(goal.name(), goal);
        }
        return goals;
    }

    private static void bind(Map<Phase, List<Goal>> bindings, Phase phase,
                              Map<String, Goal> byName, String... goalNames) {
        List<Goal> resolved = new java.util.ArrayList<>();
        for (String name : goalNames) {
            Goal goal = byName.get(name);
            if (goal != null) resolved.add(goal);
        }
        bindings.put(phase, resolved);
    }

    private PluginRegistry() {}
}
