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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.github.pixelsam123.AsyncUtils.setTimeout;

@ServerEndpoint(value = "/ws/anagram", encoders = JsonEncoder.class)
@ApplicationScoped
public class WebsocketAnagram {

    private final Map<Session, PlayerInfo> players = new ConcurrentHashMap<>();

    private Optional<AnagramConfig> gameConfig = Optional.empty();

    private int roundsLeft = 0;
    private Optional<String> currentWord = Optional.empty();
    private Optional<Cancellable> roundEndTimeoutHandle = Optional.empty();
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

        if (roundEndTimeoutHandle.isEmpty() && message.matches("/start \\d+ \\d+")) {
            String[] command = message.split(" ");
            int roundCount = Integer.parseInt(command[1]);
            int timePerRound = Integer.parseInt(command[2]);

            broadcast(new ChatMessage(
                roundCount + " rounds started with time per round of " + timePerRound + " seconds!"
            ));

            gameConfig = Optional.of(new AnagramConfig(timePerRound, 5));
            roundsLeft = roundCount;
            startRound();

            return;
        }

        if (currentWord.isPresent() && message.equals(currentWord.get())) {
            if (currentRoundAnswerers.contains(session)) {
                send(session, new ChatMessage("You already answered..."));

                return;
            }

            players.get(session).points += currentRoundAnswerers.size() == 0 ? 2 : 1;
            currentRoundAnswerers.add(session);
            broadcast(new ChatMessage(players.get(session).name + " answered successfully!"));

            if (roundEndTimeoutHandle.isPresent()
                && currentRoundAnswerers.size() == players.size()) {
                roundEndTimeoutHandle.get().cancel();
                endCurrentRound();
            }

            return;
        }

        broadcast(new ChatMessage(players.get(session).name + ": " + message));
    }

    private void startRound() {
        if (gameConfig.isEmpty()) {
            broadcast(new ChatMessage("CANNOT start round becuse configuration is empty!"));
            return;
        }
        AnagramConfig config = gameConfig.get();

        currentRoundAnswerers.clear();

        if (roundsLeft == 0) {
            broadcast(new FinishedGame());
            broadcast(new ChatMessage("GAME FINISHED! Final points:\n" + playerToPointsTable()));

            return;
        }

        currentWord = Optional.of("placehold");

        OffsetDateTime roundFinishTime = OffsetDateTime.now().plusSeconds(config.timePerRound);
        broadcast(new OngoingRoundInfo("palcehlod", roundFinishTime.toString()));

        roundEndTimeoutHandle = Optional.of(setTimeout(
            this::endCurrentRound,
            Duration.ofSeconds(config.timePerRound)
        ));
    }

    private void endCurrentRound() {
        if (gameConfig.isEmpty()) {
            broadcast(new ChatMessage("CANNOT start round because configuration is empty!"));
            return;
        }
        if (currentWord.isEmpty()) {
            broadcast(new ChatMessage("CANNOT end round because current word is empty!"));
            return;
        }
        AnagramConfig config = gameConfig.get();
        String word = currentWord.get();

        OffsetDateTime nextRoundStartTime = OffsetDateTime
            .now()
            .plusSeconds(config.timePerRoundEnding);

        broadcast(new ChatMessage("Points:\n" + playerToPointsTable()));
        broadcast(new FinishedRoundInfo(word, nextRoundStartTime.toString()));

        currentWord = Optional.empty();
        roundsLeft--;

        startRound();
    }

    private String playerToPointsTable() {
        return players
            .values()
            .stream()
            .map(player -> player.name + ": " + player.points)
            .collect(Collectors.joining());
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
