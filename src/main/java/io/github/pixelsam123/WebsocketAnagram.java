package io.github.pixelsam123;

import io.github.pixelsam123.server.message.*;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.core.impl.ConcurrentHashSet;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static io.github.pixelsam123.AsyncUtils.setTimeout;

@ServerEndpoint(value = "/ws/anagram", encoders = JsonEncoder.class)
@ApplicationScoped
public class WebsocketAnagram {

    private final Map<Session, PlayerInfo> players = new ConcurrentHashMap<>();

    private Optional<AnagramConfig> gameConfig = Optional.empty();

    private Set<String> wordsForRound = Set.of();
    private Optional<String> currentWord = Optional.empty();
    private Optional<Cancellable> roundEndTimeoutHandle = Optional.empty();
    private final Set<Session> currentRoundAnswerers = new ConcurrentHashSet<>();

    private final RandomWordService randomWordService;

    public WebsocketAnagram(@RestClient RandomWordService randomWordService) {
        this.randomWordService = randomWordService;
    }

    @OnOpen
    public void onOpen(Session session) {
        send(session, new ChatMessage("Please enter name in chat."));

        Log.info("New player joined, awaiting name.");
    }

    @OnClose
    public void onClose(Session session) {
        PlayerInfo leavingPlayer = players.remove(session);
        if (leavingPlayer != null) {
            broadcast(new ChatMessage(leavingPlayer.name + " left!"));
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
            handleNameEntry(session, message);
            return;
        }
        if (roundEndTimeoutHandle.isEmpty() && message.matches("/start \\d+ \\d+")) {
            handleGameStartCommand(message);
            return;
        }
        if (currentWord.isPresent() && message.toLowerCase().equals(currentWord.get())) {
            handleSuccessfulAnswer(session);
            return;
        }

        broadcast(new ChatMessage(players.get(session).name + ": " + message));
    }

    private void handleNameEntry(Session session, String name) {
        if (players
            .values()
            .stream()
            .map(existingPlayer -> existingPlayer.name)
            .toList()
            .contains(name)) {
            send(session, new ChatMessage("Name " + name + " is already in room!"));

            return;
        }

        players.put(session, new PlayerInfo(name));
        broadcast(new ChatMessage(name + " joined!"));
    }

    private void handleGameStartCommand(String command) {
        String[] commandParts = command.split(" ");
        int roundCount = Integer.parseInt(commandParts[1]);
        int timePerRound = Integer.parseInt(commandParts[2]);

        randomWordService.getWordsOfLength(roundCount, 5).subscribe().with(
            words -> {
                broadcast(new ChatMessage(
                    roundCount + " rounds started with time per round of " + timePerRound
                        + " seconds!"
                ));

                gameConfig = Optional.of(new AnagramConfig(timePerRound, 5));

                Set<String> wordsForRound = new ConcurrentHashSet<>();
                wordsForRound.addAll(words);
                this.wordsForRound = wordsForRound;

                startRound();
            },
            err -> broadcast(new ChatMessage("FAILED to fetch words. Cannot start game."))
        );
    }

    private void handleSuccessfulAnswer(Session session) {
        if (currentRoundAnswerers.contains(session)) {
            send(session, new ChatMessage("You already answered..."));

            return;
        }

        players.get(session).points += currentRoundAnswerers.size() == 0 ? 2 : 1;
        currentRoundAnswerers.add(session);
        broadcast(new ChatMessage(players.get(session).name + " answered successfully!"));

        if (roundEndTimeoutHandle.isPresent() && currentRoundAnswerers.size() == players.size()) {
            roundEndTimeoutHandle.get().cancel();
            endCurrentRound();
        }
    }

    private void startRound() {
        if (gameConfig.isEmpty()) {
            broadcast(new ChatMessage("CANNOT start round because configuration is empty!"));
            return;
        }
        AnagramConfig config = gameConfig.get();

        currentRoundAnswerers.clear();

        if (wordsForRound.size() == 0) {
            broadcast(new FinishedGame());
            broadcast(new ChatMessage("GAME FINISHED! Final points:\n" + playerToPointsTable()));
            roundEndTimeoutHandle = Optional.empty();

            for (PlayerInfo player : players.values()) {
                player.points = 0;
            }

            return;
        }

        int randomWordIndex = ThreadLocalRandom.current().nextInt(0, wordsForRound.size());
        currentWord = wordsForRound.stream().skip(randomWordIndex).findFirst();

        if (currentWord.isEmpty()) {
            broadcast(new ChatMessage(
                "NOT SUPPOSED TO HAPPEN: Random word is empty. Skipping round."
            ));
            endCurrentRound();

            return;
        }

        List<Character> shuffledChars = new ArrayList<>();
        for (char character : currentWord.get().toCharArray()) {
            shuffledChars.add(character);
        }
        Collections.shuffle(shuffledChars);

        String shuffledWord = shuffledChars
            .stream()
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();

        OffsetDateTime roundFinishTime = OffsetDateTime.now().plusSeconds(config.timePerRound);
        broadcast(new OngoingRoundInfo(shuffledWord, roundFinishTime.toString()));

        roundEndTimeoutHandle = Optional.of(setTimeout(
            this::endCurrentRound,
            Duration.ofSeconds(config.timePerRound)
        ));
    }

    private void endCurrentRound() {
        if (gameConfig.isEmpty()) {
            broadcast(new ChatMessage("CANNOT end round because configuration is empty!"));
            return;
        }
        AnagramConfig config = gameConfig.get();

        OffsetDateTime nextRoundStartTime = OffsetDateTime
            .now()
            .plusSeconds(config.timePerRoundEnding);

        broadcast(new ChatMessage("Points:\n" + playerToPointsTable()));
        currentWord.ifPresentOrElse(
            word -> {
                broadcast(new FinishedRoundInfo(word, nextRoundStartTime.toString()));
                wordsForRound.remove(word);
            },
            () -> broadcast(new FinishedRoundInfo("INVALID_STATE", nextRoundStartTime.toString()))
        );

        currentWord = Optional.empty();

        setTimeout(this::startRound, Duration.ofSeconds(config.timePerRoundEnding));
    }

    private String playerToPointsTable() {
        return players
            .values()
            .stream()
            .map(player -> player.name + " -> " + player.points)
            .collect(Collectors.joining("\n"));
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
