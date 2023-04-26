package io.github.pixelsam123.wordgames4j.common.room;

import io.github.pixelsam123.wordgames4j.common.message.ChatMessage;
import io.github.pixelsam123.wordgames4j.common.message.Message;
import io.github.pixelsam123.wordgames4j.common.message.PongMessage;
import io.quarkus.logging.Log;

import javax.websocket.Session;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Room {

    /**
     * Empty name MEANS nonexistent username
     */
    private final Map<Session, String> sessionToUsername = new ConcurrentHashMap<>();

    private final RoomConfig config;
    private final Runnable onLastUserRemoved;
    private final RoomInterceptor interceptor;

    /**
     * @param config            Room configuration
     * @param onLastUserRemoved Please use this to destroy the room when the last user is removed
     * @param interceptor       Intercept user addition and removal plus messages with this
     */
    public Room(
        RoomConfig config, Runnable onLastUserRemoved, RoomInterceptor interceptor
    ) {
        this.config = config;
        this.onLastUserRemoved = onLastUserRemoved;
        this.interceptor = interceptor;

        Log.info("ROOM " + config.identifier + " created!");
    }

    public void addUser(Session session) {
        sessionToUsername.put(session, "");
        sendMessage(session, new ChatMessage("Please enter username in chat."));
    }

    public void removeUser(Session session) {
        String removedUser = sessionToUsername.remove(session);

        if (!removedUser.isEmpty()) {
            interceptor.interceptAfterUsernameRemoved(removedUser);
            broadcast(new ChatMessage(removedUser + " left!"));
        }

        if (sessionToUsername.isEmpty()) {
            onLastUserRemoved.run();
        }
    }

    public void receiveError(Throwable error) {
        broadcast(new ChatMessage("ERROR: " + error.getMessage()));
    }

    public void receiveMessage(Session session, String clientMessage) {
        if (clientMessage.equals("/ping")) {
            sendMessage(session, new PongMessage());
            return;
        }

        String username = sessionToUsername.get(session);

        if (username.isEmpty()) {
            handleUsernameEntry(session, clientMessage);
            return;
        }

        List<Message> messages = interceptor.interceptMessage(username, clientMessage);
        if (messages != null) {
            for (Message message : messages) {
                sendMessage(session, message);
            }
            return;
        }

        broadcast(new ChatMessage(username + ": " + clientMessage));
    }

    public void broadcast(Message message) {
        for (Session session : sessionToUsername.keySet()) {
            sendMessage(session, message);
        }
    }

    private void handleUsernameEntry(Session session, String username) {
        if (username.isBlank()) {
            sendMessage(session, new ChatMessage("Cannot use blank username!"));
            return;
        }
        if (sessionToUsername.containsValue(username)) {
            sendMessage(session, new ChatMessage("Username " + username + " is already in room!"));
            return;
        }

        sessionToUsername.put(session, username);

        List<Message> messages = interceptor.interceptAfterUsernameAdded(username);
        for (Message message : messages) {
            sendMessage(session, message);
        }

        broadcast(new ChatMessage(username + " joined!"));
    }

    private void sendMessage(Session session, Message message) {
        session.getAsyncRemote().sendObject(message, result -> {
            Throwable error = result.getException();
            if (error != null) {
                Log.error(
                    "ROOM " + config.identifier + ": " + error.getMessage(),
                    error.getCause()
                );
            }
        });
    }

}
