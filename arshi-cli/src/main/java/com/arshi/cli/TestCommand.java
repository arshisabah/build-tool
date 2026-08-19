package com.arshi.cli;

import com.arshi.api.Phase;
import com.arshi.core.LifecycleRunner;
import com.arshi.core.PluginRegistry;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "test", description = "Run tests (runs validate, compile, test).")
public class TestCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        new LifecycleRunner().run(Phase.TEST, CommandSupport.loadProject(), PluginRegistry.defaultBindings());
        return 0;
    }
}
