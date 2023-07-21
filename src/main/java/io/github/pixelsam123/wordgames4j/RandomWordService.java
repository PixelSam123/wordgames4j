package io.github.pixelsam123.wordgames4j;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Set;

@Path("/word")
@RegisterRestClient(baseUri = "https://random-word-api.herokuapp.com")
public interface RandomWordService {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Set<String>> getWordsOfLength(
        @QueryParam("number") int count,
        @QueryParam("length") int length
    );

}
