package io.github.pixelsam123;

import io.github.pixelsam123.server.message.*;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.core.impl.ConcurrentHashSet;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static io.github.pixelsam123.UtilsAsync.setTimeout;

public class AnagramRoom {

    private final Map<String, AnagramPlayerInfo> nameToPlayerInfo = new ConcurrentHashMap<>();

    private @Nullable AnagramConfig gameConfig = null;

    private @Nullable String currentWord = null;
    private @Nullable Cancellable roundEndTimeoutHandle = null;
    private final Set<String> currentRoundAnswerers = new ConcurrentHashSet<>();
    private final Set<String> wordsForRound = new ConcurrentHashSet<>();

    private final RandomWordService randomWordService;
    private final BiConsumer<String, IServerMessage> sendToName;
    private final Runnable destroyRoom;

    public AnagramRoom(
        RandomWordService randomWordService,
        BiConsumer<String, IServerMessage> sendToName,
        Runnable destroyRoom
    ) {
        this.randomWordService = randomWordService;
        this.sendToName = sendToName;
        this.destroyRoom = destroyRoom;
    }

    public void addPlayerOfName(String name) throws AnagramRoomJoinException {
        int maxRoomSize = 20;

        if (nameToPlayerInfo.size() >= maxRoomSize) {
            throw new AnagramRoomJoinException("Room is full! Max size: " + maxRoomSize);
        }
        if (nameToPlayerInfo.containsKey(name)) {
            throw new AnagramRoomJoinException("Name " + name + " is already in room!");
        }

        nameToPlayerInfo.put(name, new AnagramPlayerInfo());
        sendToName.accept(name, new ChatMessage("Welcome! Type /help for help"));
        broadcast(new ChatMessage(name + " joined!"));
    }

    public void removePlayerOfName(String name) {
        nameToPlayerInfo.remove(name);
        broadcast(new ChatMessage(name + " left!"));

        // Ask for room destruction if room becomes empty
        if (nameToPlayerInfo.isEmpty() && roundEndTimeoutHandle != null) {
            roundEndTimeoutHandle.cancel();
            destroyRoom.run();
        }
    }

    public void receiveError(Throwable throwable) {
        broadcast(new ChatMessage("ERROR: " + throwable.getMessage()));
    }

    public void receiveMessage(String name, String message) {
        if (message.matches("/help")) {
            handleHelpCommand(name);
            return;
        }
        if (roundEndTimeoutHandle == null && message.matches("/start \\d+ \\d+ \\d+")) {
            handleGameStartCommand(message);
            return;
        }
        if (message.toLowerCase().equals(currentWord)) {
            handleSuccessfulAnswer(name);
            return;
        }
        if (currentWord != null && message.matches("/skip")) {
            handleSkippingAnswer(name);
            return;
        }

        broadcast(new ChatMessage(name + ": " + message));
    }

    private void handleHelpCommand(String name) {
        sendToName.accept(name, new ChatMessage("""
            Commands:
                        
            /start {wordLength} {roundCount} {timePerRound}
            Start a new round. Time per round is in seconds.
                        
            /skip
            Skip your turn in a round.
            """.trim()));
    }

    private void handleGameStartCommand(String command) {
        String[] commandParts = command.split(" ");
        int wordLength = Integer.parseInt(commandParts[1]);
        int roundCount = Integer.parseInt(commandParts[2]);
        int timePerRound = Integer.parseInt(commandParts[3]);

        randomWordService.getWordsOfLength(roundCount, wordLength).subscribe().with(
            words -> {
                broadcast(new ChatMessage(
                    roundCount + " rounds started with time per round of " + timePerRound
                        + " seconds! Word length: " + wordLength
                ));

                gameConfig = new AnagramConfig(timePerRound, 5);

                wordsForRound.addAll(words);

                startRound();
            },
            err -> broadcast(new ChatMessage("FAILED to fetch words. Cannot start game."))
        );
    }

    private void handleSuccessfulAnswer(String name) {
        if (currentRoundAnswerers.contains(name)) {
            sendToName.accept(name, new ChatMessage("You already answered..."));

            return;
        }

        nameToPlayerInfo.get(name).points += currentRoundAnswerers.size() == 0 ? 2 : 1;
        currentRoundAnswerers.add(name);
        broadcast(new ChatMessage(name + " answered successfully!"));

        if (roundEndTimeoutHandle != null
            && currentRoundAnswerers.size() == nameToPlayerInfo.size()) {
            roundEndTimeoutHandle.cancel();
            endCurrentRound();
        }
    }

    private void handleSkippingAnswer(String name) {
        if (currentRoundAnswerers.contains(name)) {
            sendToName.accept(name, new ChatMessage("Can't skip! You already answered."));

            return;
        }

        currentRoundAnswerers.add(name);
        broadcast(new ChatMessage(name + " skipped!"));

        if (roundEndTimeoutHandle != null
            && currentRoundAnswerers.size() == nameToPlayerInfo.size()) {
            roundEndTimeoutHandle.cancel();
            endCurrentRound();
        }
    }

    private void startRound() {
        if (gameConfig == null) {
            broadcast(new ChatMessage("CANNOT start round because configuration is empty!"));
            return;
        }

        currentRoundAnswerers.clear();

        if (wordsForRound.size() == 0) {
            broadcast(new FinishedGame());
            broadcast(new ChatMessage("GAME FINISHED! Final points:\n" + nameToPointsTable()));
            roundEndTimeoutHandle = null;

            for (AnagramPlayerInfo playerInfo : nameToPlayerInfo.values()) {
                playerInfo.points = 0;
            }

            return;
        }

        int randomWordIndex = ThreadLocalRandom.current().nextInt(0, wordsForRound.size());
        currentWord = wordsForRound.stream().skip(randomWordIndex).findFirst().orElse(null);

        if (currentWord == null) {
            broadcast(new ChatMessage(
                "NOT SUPPOSED TO HAPPEN: Random word is empty. Skipping round."
            ));
            endCurrentRound();

            return;
        }

        List<Character> shuffledChars = new ArrayList<>();
        for (char character : currentWord.toCharArray()) {
            shuffledChars.add(character);
        }
        Collections.shuffle(shuffledChars);

        String shuffledWord = shuffledChars
            .stream()
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();

        OffsetDateTime roundFinishTime = OffsetDateTime.now().plusSeconds(gameConfig.timePerRound);
        broadcast(new OngoingRoundInfo(shuffledWord, roundFinishTime.toString()));

        roundEndTimeoutHandle = setTimeout(
            this::endCurrentRound,
            Duration.ofSeconds(gameConfig.timePerRound)
        );
    }

    private void endCurrentRound() {
        if (gameConfig == null) {
            broadcast(new ChatMessage("CANNOT end round because configuration is empty!"));
            return;
        }

        OffsetDateTime nextRoundStartTime = OffsetDateTime
            .now()
            .plusSeconds(gameConfig.timePerRoundEnding);

        broadcast(new ChatMessage("Points:\n" + nameToPointsTable()));
        if (currentWord != null) {
            broadcast(new FinishedRoundInfo(currentWord, nextRoundStartTime.toString()));
            wordsForRound.remove(currentWord);
        } else {
            broadcast(new FinishedRoundInfo("INVALID_STATE", nextRoundStartTime.toString()));
        }

        currentWord = null;

        setTimeout(this::startRound, Duration.ofSeconds(gameConfig.timePerRoundEnding));
    }

    private String nameToPointsTable() {
        return nameToPlayerInfo
            .entrySet()
            .stream()
            .map(entry -> entry.getKey() + " -> " + entry.getValue().points)
            .collect(Collectors.joining("\n"));
    }

    private void broadcast(IServerMessage message) {
        for (String name : nameToPlayerInfo.keySet()) {
            sendToName.accept(name, message);
        }
    }

}
