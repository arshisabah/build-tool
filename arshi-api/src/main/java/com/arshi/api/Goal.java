package com.arshi.api;

/**
 * Contract every unit of build work implements: clean, compile, test, jar, install...
 * Custom plugins implement this interface and register themselves in a
 * plugin descriptor so the CLI/core can discover and bind them to a phase.
 */
public interface Goal {

    /** e.g. "compiler:compile", "clean:clean", "jar:jar" */
    String name();

    void execute(ArshiProject project) throws GoalExecutionException;
}
