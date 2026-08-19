package com.arshi.plugins;

import com.arshi.api.ArshiProject;
import com.arshi.api.Goal;
import com.arshi.api.GoalExecutionException;

import java.io.IOException;
import java.nio.file.*;

/** "resources:copy" — copies src/main/resources into target/classes. */
public class ResourcesGoal implements Goal {

    @Override
    public String name() {
        return "resources:copy";
    }

    @Override
    public void execute(ArshiProject project) throws GoalExecutionException {
        Path src = Path.of("src", "main", "resources");
        Path dest = Path.of("target", "classes");
        if (!Files.exists(src)) return;

        try (var stream = Files.walk(src)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                Path target = dest.resolve(src.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new GoalExecutionException("Failed to copy resources", e);
        }
    }
}
