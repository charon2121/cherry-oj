package com.cherryoj.problemservice.config;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.problem.authoring")
public record AuthoringProperties(
        Path binary, URI judgeUrl, Path toolchainRoot, Path workRoot, String openaiModel, String openaiApiKey) {
    public boolean buildEnabled() {
        return binary != null
                && binary.isAbsolute()
                && judgeUrl != null
                && toolchainRoot != null
                && toolchainRoot.isAbsolute()
                && workRoot != null
                && workRoot.isAbsolute();
    }

    public boolean modelEnabled() {
        return openaiModel != null && !openaiModel.isBlank() && openaiApiKey != null && !openaiApiKey.isBlank();
    }

    @Override
    public String toString() {
        return "AuthoringProperties[buildEnabled=" + buildEnabled() + ", modelEnabled=" + modelEnabled() + "]";
    }
}
