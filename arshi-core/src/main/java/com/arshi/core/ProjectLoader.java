package com.arshi.core;

import com.arshi.api.ArshiProject;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.nio.file.Path;

/** Reads arshi.xml from disk and deserializes it into an ArshiProject. */
public final class ProjectLoader {

    private static final XmlMapper MAPPER = new XmlMapper();

    private ProjectLoader() {}

    public static ArshiProject load(Path descriptorPath) {
        try {
            return MAPPER.readValue(descriptorPath.toFile(), ArshiProject.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse " + descriptorPath, e);
        }
    }
}
