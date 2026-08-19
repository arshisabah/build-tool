package com.arshi.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "arshi",
    mixinStandardHelpOptions = true,
    version = "arshi 0.1.0-SNAPSHOT",
    description = "A Maven-style build tool.",
    subcommands = {
        CleanCommand.class,
        CompileCommand.class,
        TestCommand.class,
        PackageCommand.class,
        InstallCommand.class,
        DependencyCommand.class,
        RunCommand.class
    }
)
public class ArshiCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ArshiCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Usage: arshi <clean|compile|test|package|install|dependency:tree>");
    }
}
