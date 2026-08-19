package com.arshi.core;

import com.arshi.api.ArshiProject;
import com.arshi.api.Goal;
import com.arshi.api.GoalExecutionException;
import com.arshi.api.Phase;

import java.util.List;
import java.util.Map;

/**
 * Executes the default lifecycle up to (and including) a target phase.
 * Mirrors Maven's rule: "mvn package" implicitly runs validate, compile,
 * and test first, in order.
 */
public final class LifecycleRunner {

    public void run(Phase target, ArshiProject project, Map<Phase, List<Goal>> bindings)
            throws GoalExecutionException {
        for (Phase phase : Phase.values()) {
            if (phase.ordinal() > target.ordinal()) break;

            for (Goal goal : bindings.getOrDefault(phase, List.of())) {
                System.out.printf("[arshi] %-10s :: %s%n", phase, goal.name());
                goal.execute(project);
            }
        }
        System.out.println("[arshi] BUILD SUCCESS");
    }
}
