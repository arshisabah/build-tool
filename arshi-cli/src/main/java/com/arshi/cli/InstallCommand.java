package com.arshi.cli;

import com.arshi.api.Phase;
import com.arshi.core.LifecycleRunner;
import com.arshi.core.PluginRegistry;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "install", description = "Install the jar into ~/.arshi/repository (runs the full default lifecycle).")
public class InstallCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        new LifecycleRunner().run(Phase.INSTALL, CommandSupport.loadProject(), PluginRegistry.defaultBindings());
        return 0;
    }
}
