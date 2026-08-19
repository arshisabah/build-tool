package com.arshi.plugins;

import com.arshi.api.ArshiProject;
import com.arshi.api.Goal;
import com.arshi.api.GoalExecutionException;
import com.arshi.resolver.LocalRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** "install:install" — copies the built jar into ~/.arshi/repository so other local projects can depend on it. */
public class InstallGoal implements Goal {

    @Override
    public String name() {
        return "install:install";
    }

    @Override
    public void execute(ArshiProject project) throws GoalExecutionException {
        Path jarPath = Path.of("target", project.artifactId() + "-" + project.version() + ".jar");
        if (!Files.exists(jarPath)) {
            throw new GoalExecutionException("Nothing to install — run 'arshi package' first");
        }

        LocalRepository repo = new LocalRepository();
        Path destination = repo.root()
                .resolve(project.groupId().replace('.', '/'))
                .resolve(project.artifactId())
                .resolve(project.version())
                .resolve(jarPath.getFileName());
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(jarPath, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new GoalExecutionException("Failed to install artifact", e);
        }
        System.out.println("[arshi] installed to " + destination);
    }
}
