package io.github.pixelsam123;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(value = "/ws/anagram/{roomId}", encoders = JsonEncoder.class)
@ApplicationScoped
public class WebsocketAnagram {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    private final RandomWordService randomWordService;

    public WebsocketAnagram(@RestClient RandomWordService randomWordService) {
        this.randomWordService = randomWordService;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId) {
        Room room = rooms.computeIfAbsent(roomId, key -> new Room(randomWordService));

        room.players.put(session, new PlayerInfo(null));
        room.onOpen(session);
    }

    @OnClose
    public void onClose(Session session, @PathParam("roomId") String roomId) {
        Room room = rooms.get(roomId);
        room.onClose(session);

        if (room.players.isEmpty()) {
            rooms.remove(roomId);
        }
    }

    @OnError
    public void onError(Throwable throwable, @PathParam("roomId") String roomId) {
        Room room = rooms.get(roomId);
        room.onError(throwable);
    }

    @OnMessage
    public void onMessage(Session session, String message, @PathParam("roomId") String roomId) {
        Room room = rooms.get(roomId);
        room.onMessage(session, message);
    }

}
