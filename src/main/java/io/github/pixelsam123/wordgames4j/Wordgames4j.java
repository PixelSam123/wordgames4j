package io.github.pixelsam123.wordgames4j;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

import jakarta.ws.rs.core.Application;

@OpenAPIDefinition(
    info = @Info(
        title = "wordgames4j",
        version = "1.0-SNAPSHOT",
        description = "A word games server for WebSockets"
    )
)
public class Wordgames4j extends Application {
}
