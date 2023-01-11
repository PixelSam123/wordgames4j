package io.github.pixelsam123;

import io.github.pixelsam123.server.message.*;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.subscription.Cancellable;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.github.pixelsam123.AsyncUtils.setTimeout;

@ServerEndpoint(value = "/ws/anagram", encoders = JsonEncoder.class)
@ApplicationScoped
public class WebsocketAnagram {

    private final Map<Session, PlayerInfo> players = new ConcurrentHashMap<>();
    private Cancellable roundTimeoutHandle = null;
    private Cancellable roundEndTimeoutHandle = null;
    private String currentWord = null;
    private final Set<Session> currentRoundAnswerers = new HashSet<>();

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

        if (roundTimeoutHandle == null && message.matches("/start \\d+ \\d+")) {
            String[] command = message.split(" ");
            int roundCount = Integer.parseInt(command[1]);
            int timePerRound = Integer.parseInt(command[2]);
            int revealAnswerTime = 5;

            broadcast(new ChatMessage(
                roundCount + " rounds started with time per round of " + timePerRound + " seconds!"
            ));

            for (long i = 0; i <= roundCount; i++) {
                long finalI = i;
                roundTimeoutHandle = setTimeout(() -> {
                    currentRoundAnswerers.clear();

                    if (finalI == roundCount) {
                        broadcast(new FinishedGame());
                        broadcast(new ChatMessage("GAME FINISHED! Final points:\n" + players
                            .values()
                            .stream()
                            .map(playerInfo -> playerInfo.name + ": " + playerInfo.points)
                            .collect(Collectors.joining("\n"))));

                        return;
                    }

                    currentWord = "placehold";

                    OffsetDateTime roundFinishTime = OffsetDateTime.now().plusSeconds(timePerRound);
                    OffsetDateTime toNextRoundTime = roundFinishTime
                        .plusSeconds(revealAnswerTime);

                    broadcast(new OngoingRoundInfo("palcehlod", roundFinishTime.toString()));

                    roundEndTimeoutHandle = setTimeout(
                        () -> endCurrentRound(toNextRoundTime),
                        Duration.ofSeconds(timePerRound)
                    );
                }, Duration.ofSeconds((timePerRound + revealAnswerTime) * i));
            }

            return;
        }

        if (message.equals(currentWord)) {
            if (currentRoundAnswerers.contains(session)) {
                send(session, new ChatMessage("You already answered..."));

                return;
            }

            players.get(session).points += 1;
            currentRoundAnswerers.add(session);
            broadcast(new ChatMessage(players.get(session).name + " answered successfully!"));

            if (currentRoundAnswerers.size() == players.size()) {
                roundEndTimeoutHandle.cancel();
                endCurrentRound(OffsetDateTime.now().plusSeconds(5L));
            }

            return;
        }

        broadcast(new ChatMessage(players.get(session).name + ": " + message));
    }

    private void endCurrentRound(OffsetDateTime toNextRoundTime) {
        String playerPointsDisplay = players
            .values()
            .stream()
            .map(playerInfo -> playerInfo.name + ": " + playerInfo.points)
            .collect(Collectors.joining("\n"));

        broadcast(new ChatMessage("Points:\n" + playerPointsDisplay));
        broadcast(new FinishedRoundInfo(currentWord, toNextRoundTime.toString()));

        currentWord = null;
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
