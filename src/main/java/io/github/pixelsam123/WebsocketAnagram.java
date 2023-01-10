package io.github.pixelsam123;

import io.quarkus.logging.Log;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/ws/anagram")
@ApplicationScoped
public class WebsocketAnagram {

    Map<Session, PlayerInfo> players = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        session.getAsyncRemote().sendObject(Map.ofEntries(
            Map.entry("type", "ChatMessage"),
            Map.entry("content", "Please enter name in chat.")
        ));

        Log.info("New player joined, awaiting name.");
    }

    @OnClose
    public void onClose(Session session) {
        broadcast(players.get(session).name + " left!");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        broadcast("ERROR: " + throwable.getMessage());
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        PlayerInfo player = players.get(session);

        if (player == null) {
            players.put(session, new PlayerInfo(message));
            broadcast(message + " joined!");

            return;
        }

        if (message.matches("/start \\d+")) {
            int timePerRound = Integer.parseInt(message.split(" ")[1]);
            broadcast("Round started with time per round of " + timePerRound + " seconds!");

            return;
        }

        broadcast(players.get(session).name + ": " + message);
    }

    private void broadcast(String message) {
        for (Session playerSession : players.keySet()) {
            playerSession.getAsyncRemote().sendObject(Map.ofEntries(
                Map.entry("type", "ChatMessage"),
                Map.entry("content", message)
            ));
        }
        Log.info("BROADCAST -> " + message);
    }

}
