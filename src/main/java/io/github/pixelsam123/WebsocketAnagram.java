package io.github.pixelsam123;

import io.github.pixelsam123.server.message.ChatMessage;
import io.github.pixelsam123.server.message.IServerMessage;
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
public class WebsocketAnagram {

    private final Map<String, AnagramRoom> idToRoom = new ConcurrentHashMap<>();
    private final Map<String, Session> nameToSession = new ConcurrentHashMap<>();
    private final Map<Session, String> sessionToName = new ConcurrentHashMap<>();

    private final RandomWordService randomWordService;

    public WebsocketAnagram(@RestClient RandomWordService randomWordService) {
        this.randomWordService = randomWordService;
    }

    @OnOpen
    public void onOpen(Session session) {
        sendServerMessage(session, new ChatMessage("Please enter name in chat."));
    }

    @OnClose
    public void onClose(Session session, @PathParam("roomId") String roomId) {
        AnagramRoom room = idToRoom.get(roomId);
        String name = sessionToName.get(session);

        nameToSession.remove(name);
        sessionToName.remove(session);
        room.removePlayerOfName(name);
    }

    @OnError
    public void onError(Throwable throwable, @PathParam("roomId") String roomId) {
        Log.error(throwable.getMessage(), throwable.getCause());

        AnagramRoom room = idToRoom.get(roomId);
        room.receiveError(throwable);
    }

    @OnMessage
    public void onMessage(Session session, String message, @PathParam("roomId") String roomId) {
        AnagramRoom room = idToRoom.computeIfAbsent(roomId, key -> new AnagramRoom(
            randomWordService,
            (name, messageToSend) -> sendServerMessage(nameToSession.get(name), messageToSend),
            () -> idToRoom.remove(key)
        ));

        if (!sessionToName.containsKey(session)) { // means player hasn't joined any room
            try {
                nameToSession.put(message, session);
                sessionToName.put(session, message);
                room.addPlayerOfName(message);
            } catch (JoinRoomException err) {
                nameToSession.remove(message);
                sessionToName.remove(session);
                sendServerMessage(
                    session,
                    new ChatMessage("ERROR joining room! Reason:\n" + err.getMessage())
                );
            }

            return;
        }

        // now player is in a room
        room.receiveMessage(sessionToName.get(session), message);
    }

    private void sendServerMessage(Session session, IServerMessage message) {
        session.getAsyncRemote().sendObject(message, result -> {
            if (result.getException() != null) {
                Log.error(result.getException().getMessage(), result.getException().getCause());
            }
        });
    }

}
