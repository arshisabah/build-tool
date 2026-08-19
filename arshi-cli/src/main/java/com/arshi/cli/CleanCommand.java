package com.arshi.cli;

import com.arshi.plugins.CleanGoal;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "clean", description = "Delete the target/ output directory.")
public class CleanCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        new CleanGoal().execute(CommandSupport.loadProject());
        System.out.println("[arshi] BUILD SUCCESS");
        return 0;
    }
}
