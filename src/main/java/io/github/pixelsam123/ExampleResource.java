package io.github.pixelsam123;

import io.smallrye.mutiny.Uni;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@Path("/hello")
public class ExampleResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Map<String, String>> hello() {
        return Uni.createFrom().item(Map.ofEntries(
            Map.entry("hello", "from RESTEasy Reactive")
        ));
    }

}
