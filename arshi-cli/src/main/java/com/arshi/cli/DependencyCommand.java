package com.arshi.cli;

import com.arshi.api.ArshiProject;
import com.arshi.resolver.DependencyGraph;
import com.arshi.resolver.DependencyTreePrinter;
import com.arshi.resolver.LocalRepository;
import com.arshi.resolver.RemoteResolver;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
    name = "dependency:tree",
    description = "Print the resolved (transitive) dependency tree, like 'mvn dependency:tree'."
)
public class DependencyCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        ArshiProject project = CommandSupport.loadProject();
        RemoteResolver remote = new RemoteResolver(new LocalRepository());
        DependencyGraph graph = new DependencyGraph(remote);
        graph.resolve(project.dependencies());
        new DependencyTreePrinter().print(project, graph);
        return 0;
    }
}
