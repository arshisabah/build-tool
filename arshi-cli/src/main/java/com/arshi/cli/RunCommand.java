package com.arshi.cli;

import com.arshi.plugins.RunGoal;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
    name = "run",
    description = "Compile and run the app (like 'mvn spring-boot:run'). Requires <mainClass> in arshi.xml."
)
public class RunCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        new RunGoal().execute(CommandSupport.loadProject());
        return 0;
    }
}
