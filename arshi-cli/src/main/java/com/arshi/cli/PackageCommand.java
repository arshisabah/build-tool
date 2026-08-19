package com.arshi.cli;

import com.arshi.api.Phase;
import com.arshi.core.LifecycleRunner;
import com.arshi.core.PluginRegistry;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "package", description = "Build the jar (runs validate, compile, test, package).")
public class PackageCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        new LifecycleRunner().run(Phase.PACKAGE, CommandSupport.loadProject(), PluginRegistry.defaultBindings());
        return 0;
    }
}
