package com.arshi.plugins;

import com.arshi.api.ArshiProject;
import com.arshi.api.Goal;
import com.arshi.api.GoalExecutionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** "clean:clean" — deletes the target/ output directory. Bound to the standalone CLEAN lifecycle. */
public class CleanGoal implements Goal {

    @Override
    public String name() {
        return "clean:clean";
    }

    @Override
    public void execute(ArshiProject project) throws GoalExecutionException {
        Path target = Path.of("target");
        if (!Files.exists(target)) return;
        try (var stream = Files.walk(target)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) { }
            });
        } catch (IOException e) {
            throw new GoalExecutionException("Failed to clean target/", e);
        }
    }
}
