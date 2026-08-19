package com.arshi.plugins;

import com.arshi.api.ArshiProject;
import com.arshi.api.Goal;
import com.arshi.api.GoalExecutionException;

/**
 * "surefire:test" — discovers and runs tests under target/classes.
 *
 * v1 stub: wire this up to the JUnit Platform Launcher API
 * (org.junit.platform:junit-platform-launcher) to discover @Test-annotated
 * classes on the target/classes classpath and execute them, the same way
 * Maven Surefire does. Left as a documented extension point — see
 * Milestone 7 in the build guide.
 */
public class SurefireGoal implements Goal {

    @Override
    public String name() {
        return "surefire:test";
    }

    @Override
    public void execute(ArshiProject project) throws GoalExecutionException {
        System.out.println("[arshi] surefire:test — no test runner wired up yet (see Milestone 7)");
    }
}
