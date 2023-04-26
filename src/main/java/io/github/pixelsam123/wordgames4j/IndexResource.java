package io.github.pixelsam123.wordgames4j;

import io.smallrye.mutiny.Uni;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
