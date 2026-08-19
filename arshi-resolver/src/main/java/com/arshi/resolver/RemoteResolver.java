package com.arshi.resolver;

import com.arshi.api.Dependency;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/** Fetches jars/poms from a remote Maven-layout repository (e.g. Maven Central). */
public final class RemoteResolver {

    private final String baseUrl;
    private final LocalRepository localRepository;
    private final HttpClient client = HttpClient.newHttpClient();

    public RemoteResolver(LocalRepository localRepository) {
        this("https://repo1.maven.org/maven2/", localRepository);
    }

    public RemoteResolver(String baseUrl, LocalRepository localRepository) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.localRepository = localRepository;
    }

    public Path fetchJar(Dependency dep) throws IOException, InterruptedException {
        return download(dep, ".jar", localRepository.artifactPath(dep));
    }

    public Path fetchPom(Dependency dep) throws IOException, InterruptedException {
        return download(dep, ".pom", localRepository.pomPath(dep));
    }

    private Path download(Dependency dep, String extension, Path destination)
            throws IOException, InterruptedException {
        if (Files.exists(destination)) {
            return destination;
        }
        String remotePath = dep.groupId().replace('.', '/') + "/" + dep.artifactId() + "/"
                + dep.version() + "/" + dep.artifactId() + "-" + dep.version() + extension;

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + remotePath)).build();
        Files.createDirectories(destination.getParent());

        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(destination);
            throw new IOException("Failed to fetch " + dep + " (HTTP " + response.statusCode() + ")");
        }
        return destination;
    }
}
