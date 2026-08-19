package com.arshi.cli;

import com.arshi.api.Phase;
import com.arshi.core.LifecycleRunner;
import com.arshi.core.PluginRegistry;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "compile", description = "Compile sources (runs validate, compile).")
public class CompileCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        new LifecycleRunner().run(Phase.COMPILE, CommandSupport.loadProject(), PluginRegistry.defaultBindings());
        return 0;
    }
}
