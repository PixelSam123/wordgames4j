package io.github.pixelsam123.wordgames4j;

import io.smallrye.mutiny.Uni;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class IndexResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> get() {
        return Uni.createFrom().item(() -> """
            WebSocket endpoints:
                        
            /ws/anagram/{roomId:string}
            Room ID can be anything you want. Max 20 players in a room.
            Example client: https://pixelsam123.github.io/minigames""");
    }

}
