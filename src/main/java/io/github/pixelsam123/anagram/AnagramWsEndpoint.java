package io.github.pixelsam123.anagram;

import io.github.pixelsam123.common.*;
import io.github.pixelsam123.RandomWordService;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(value = "/ws/anagram/{roomId}", encoders = JsonEncoder.class)
@ApplicationScoped
public class AnagramWsEndpoint {

    private final Map<String, Room> idToRoom = new ConcurrentHashMap<>();

    private final RandomWordService randomWordService;

    public AnagramWsEndpoint(@RestClient RandomWordService randomWordService) {
        this.randomWordService = randomWordService;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId) {
        Room room = idToRoom.computeIfAbsent(roomId, key -> new Room(
            new RoomConfig(roomId, 20),
            config -> {
                Log.info("ROOM " + config.identifier + " destroyed!");
                idToRoom.remove(roomId);
            },
            new AnagramRoom(randomWordService, message -> idToRoom.get(roomId).broadcast(message))
        ));

        room.addUser(session);
    }

    @OnClose
    public void onClose(Session session, @PathParam("roomId") String roomId) {
        idToRoom.get(roomId).removeUser(session);
    }

    @OnError
    public void onError(Throwable error, @PathParam("roomId") String roomId) {
        idToRoom.get(roomId).receiveError(error);
    }

    @OnMessage
    public void onMessage(
        Session session,
        String clientMessage,
        @PathParam("roomId") String roomId
    ) {
        idToRoom.get(roomId).receiveMessage(session, clientMessage);
    }

}
