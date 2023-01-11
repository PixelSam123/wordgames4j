package io.github.pixelsam123;

import io.quarkus.logging.Log;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.pixelsam123.AsyncUtils.setTimeout;

@ServerEndpoint(value = "/ws/anagram", encoders = JsonEncoder.class)
@ApplicationScoped
public class WebsocketAnagram {

    private final Map<Session, PlayerInfo> players = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        send(session, "ChatMessage", "Please enter name in chat.");

        Log.info("New player joined, awaiting name.");
    }

    @OnClose
    public void onClose(Session session) {
        if (players.containsKey(session)) {
            players.remove(session);
            broadcast("ChatMessage", players.get(session).name + " left!");
        }
    }

    @OnError
    public void onError(Throwable throwable) {
        broadcast("ChatMessage", "ERROR: " + throwable.getMessage());
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        PlayerInfo player = players.get(session);

        if (player == null) {
            if (players
                .values()
                .stream()
                .map(existingPlayer -> existingPlayer.name)
                .toList()
                .contains(message)) {
                send(session, "ChatMessage", "Name " + message + " is already in room!");

                return;
            }

            players.put(session, new PlayerInfo(message));
            broadcast("ChatMessage", message + " joined!");

            return;
        }

        if (message.matches("/start \\d+ \\d+")) {
            String[] command = message.split(" ");
            int roundCount = Integer.parseInt(command[1]);
            int timePerRound = Integer.parseInt(command[2]);
            int revealAnswerTime = 5;

            broadcast(
                "ChatMessage",
                roundCount + " rounds started with time per round of " + timePerRound + " seconds!"
            );

            for (long i = 0; i < roundCount; i++) {
                setTimeout(() -> {
                    broadcast(
                        "OngoingRoundInfo",
                        "{word_to_guess:\"plchold\",round_finish_time:\""
                            + OffsetDateTime.now().plusSeconds(timePerRound) + "\"}"
                    );

                    setTimeout(() -> {
                        broadcast(
                            "FinishedRoundInfo",
                            "placeholder"
                        );
                    }, Duration.ofSeconds(timePerRound));
                }, Duration.ofSeconds((timePerRound + revealAnswerTime) * i));
            }

            return;
        }

        broadcast("ChatMessage", players.get(session).name + ": " + message);
    }

    private void send(Session session, String type, String message) {
        session.getAsyncRemote().sendObject(Map.ofEntries(
            Map.entry("type", type),
            Map.entry("content", message)
        ), result -> {
            if (result.getException() != null) {
                Log.error(result.getException().getMessage(), result.getException().getCause());
            }
        });
    }

    private void broadcast(String type, String message) {
        for (Session playerSession : players.keySet()) {
            send(playerSession, type, message);
        }
        Log.info("BROADCAST -> " + message);
    }

}
