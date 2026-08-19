package com.arshi.cli;

import com.arshi.api.ArshiProject;
import com.arshi.core.ProjectLoader;

import java.nio.file.Path;

/** Shared helper: every subcommand loads arshi.xml from the current working directory. */
final class CommandSupport {
    private CommandSupport() {}

    static ArshiProject loadProject() {
        return ProjectLoader.load(Path.of("arshi.xml"));
    }
}
