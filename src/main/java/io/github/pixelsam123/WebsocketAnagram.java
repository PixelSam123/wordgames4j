package io.github.pixelsam123;

import io.github.pixelsam123.server.message.ChatMessage;
import io.github.pixelsam123.server.message.FinishedRoundInfo;
import io.github.pixelsam123.server.message.IServerMessage;
import io.github.pixelsam123.server.message.OngoingRoundInfo;
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
        send(session, new ChatMessage("Please enter name in chat."));

        Log.info("New player joined, awaiting name.");
    }

    @OnClose
    public void onClose(Session session) {
        if (players.containsKey(session)) {
            players.remove(session);
            broadcast(new ChatMessage(players.get(session).name + " left!"));
        }
    }

    @OnError
    public void onError(Throwable throwable) {
        broadcast(new ChatMessage("ERROR: " + throwable.getMessage()));
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
                send(session, new ChatMessage("Name " + message + " is already in room!"));

                return;
            }

            players.put(session, new PlayerInfo(message));
            broadcast(new ChatMessage(message + " joined!"));

            return;
        }

        if (message.matches("/start \\d+ \\d+")) {
            String[] command = message.split(" ");
            int roundCount = Integer.parseInt(command[1]);
            int timePerRound = Integer.parseInt(command[2]);
            int revealAnswerTime = 5;

            broadcast(new ChatMessage(
                roundCount + " rounds started with time per round of " + timePerRound + " seconds!"
            ));

            for (long i = 0; i < roundCount; i++) {
                setTimeout(() -> {
                    OffsetDateTime roundFinishTime = OffsetDateTime.now().plusSeconds(timePerRound);
                    OffsetDateTime toNextRoundTime = roundFinishTime
                        .plusSeconds(revealAnswerTime);

                    broadcast(new OngoingRoundInfo("palcehlod", roundFinishTime.toString()));

                    setTimeout(() -> {
                        broadcast(new FinishedRoundInfo("placehold", toNextRoundTime.toString()));
                    }, Duration.ofSeconds(timePerRound));
                }, Duration.ofSeconds((timePerRound + revealAnswerTime) * i));
            }

            return;
        }

        broadcast(new ChatMessage(players.get(session).name + ": " + message));
    }

    private void send(Session session, IServerMessage message) {
        session.getAsyncRemote().sendObject(message, result -> {
            if (result.getException() != null) {
                Log.error(result.getException().getMessage(), result.getException().getCause());
            }
        });
    }

    private void broadcast(IServerMessage message) {
        for (Session playerSession : players.keySet()) {
            send(playerSession, message);
        }
        Log.info("BROADCAST -> " + message);
    }

}
