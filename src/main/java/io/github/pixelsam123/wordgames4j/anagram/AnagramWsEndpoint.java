package io.github.pixelsam123.wordgames4j.anagram;

import io.github.pixelsam123.wordgames4j.RandomWordService;
import io.github.pixelsam123.wordgames4j.common.json.JsonEncoder;
import io.github.pixelsam123.wordgames4j.common.message.Message;
import io.github.pixelsam123.wordgames4j.common.room.Room;
import io.github.pixelsam123.wordgames4j.common.room.RoomConfig;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
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
        Room room = idToRoom.computeIfAbsent(
            roomId,
            key -> new Room(
                new RoomConfig(roomId, 20),
                () -> destroyRoomOnLastUserRemoved(roomId),
                new AnagramRoom(
                    randomWordService,
                    message -> broadcastMessageToRoomOnRequest(roomId, message)
                )
            )
        );

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
        Session session, String clientMessage, @PathParam("roomId") String roomId
    ) {
        idToRoom.get(roomId).receiveMessage(session, clientMessage);
    }

    private void destroyRoomOnLastUserRemoved(String roomId) {
        Log.info("ROOM " + roomId + " destroyed!");
        idToRoom.remove(roomId);
    }

    private void broadcastMessageToRoomOnRequest(String roomId, Message message) {
        idToRoom.get(roomId).broadcast(message);
    }

}
