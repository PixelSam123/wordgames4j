package io.github.pixelsam123.wordgames4j.anagram;

import io.github.pixelsam123.wordgames4j.RandomWordService;
import io.github.pixelsam123.wordgames4j.anagram.message.FinishedRoundMessage;
import io.github.pixelsam123.wordgames4j.anagram.message.OngoingRoundMessage;
import io.github.pixelsam123.wordgames4j.common.room.RoomInterceptor;
import io.github.pixelsam123.wordgames4j.anagram.message.FinishedGameMessage;
import io.github.pixelsam123.wordgames4j.common.message.ChatMessage;
import io.github.pixelsam123.wordgames4j.common.message.Message;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.impl.ConcurrentHashSet;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.github.pixelsam123.wordgames4j.common.utils.UtilsAsync.setTimeout;

public class AnagramRoom implements RoomInterceptor {

    private final Map<String, AnagramPlayerInfo> nameToPlayerInfo = new ConcurrentHashMap<>();

    private @Nullable AnagramGameConfig gameConfig = null;

    private @Nullable String currentWord = null;
    private @Nullable Cancellable roundEndTimeoutHandle = null;
    private final Set<String> currentRoundAnswerers = new ConcurrentHashSet<>();
    private final Set<String> wordsForRound = new ConcurrentHashSet<>();

    private final RandomWordService randomWordService;
    private final Consumer<Message> onBroadcastRequest;

    public AnagramRoom(
        RandomWordService randomWordService,
        Consumer<Message> onBroadcastRequest
    ) {
        this.randomWordService = randomWordService;
        this.onBroadcastRequest = onBroadcastRequest;
    }

    @Override
    public List<Message> interceptAfterUsernameAdded(String username) {
        nameToPlayerInfo.put(username, new AnagramPlayerInfo());

        List<Message> messagesToSend = new ArrayList<>();
        messagesToSend.add(new ChatMessage("Welcome! Type /help for help"));

        return messagesToSend;
    }

    @Override
    public void interceptAfterUsernameRemoved(String username) {
        nameToPlayerInfo.remove(username);

        if (nameToPlayerInfo.isEmpty() && roundEndTimeoutHandle != null) {
            roundEndTimeoutHandle.cancel();
        }
    }

    @Override
    public @Nullable List<Message> interceptMessage(String username, String clientMessage) {
        if (clientMessage.matches("/help")) {
            return handleHelpCommand();
        }
        if (clientMessage.matches("/list")) {
            return handleListCommand();
        }
        if (roundEndTimeoutHandle == null) {
            if (clientMessage.matches("/start \\d+ \\d+ \\d+ (true|false)")) {
                return handleGameStartCommand(clientMessage);
            }
            if (clientMessage.matches("/start")) {
                return handleGameStartCommandDefault();
            }
        }
        if (clientMessage.toLowerCase().equals(currentWord)) {
            return handleAnswer(username, false);
        }
        if (currentWord != null && clientMessage.matches("/skip")) {
            return handleAnswer(username, true);
        }

        return null;
    }

    private List<Message> handleHelpCommand() {
        return List.of(new ChatMessage("""
            Commands:
            /help
            Show this message
            
            /list
            List the players in a room
            
            /start
            Start a new game with default settings.
            
            /start {wordLength} {roundCount} {timePerRound} {isIndonesian:boolean}
            Start a new game with custom settings. Time per round is in seconds.
            
            /skip
            Skip your turn in a round."""));
    }

    private List<Message> handleListCommand() {
        return List.of(new ChatMessage(nameToPointsTable()));
    }

    private List<Message> handleGameStartCommand(String command) {
        AnagramGameConfig gameConfig = AnagramGameConfig.parseFromGameStartCommand(command);

        if (gameConfig.isIndonesian) {
            startGameWithOfflineWordBank(gameConfig);
        } else {
            startGameWithRandomWordService(gameConfig);
        }

        return List.of(new ChatMessage("Requested a new game."));
    }

    private List<Message> handleGameStartCommandDefault() {
        handleGameStartCommand("/start 5 10 30 true");

        return List.of(new ChatMessage("Requested a new game with default settings!"));
    }

    private void startGameWithOfflineWordBank(AnagramGameConfig gameConfig) {
        Uni
            .createFrom()
            .item(Unchecked.supplier(() -> getWordPoolFromOfflineWordBank(gameConfig)))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
            .subscribe()
            .with(
                words -> announceAndStartGame(words, gameConfig),
                err -> broadcast(new ChatMessage("FAILED to read word bank. Cannot start game."))
            );
    }

    private Set<String> getWordPoolFromOfflineWordBank(
        AnagramGameConfig gameConfig
    ) throws IOException {
        List<String> wordsOfRequestedLength = getWordsOfLengthFromWordBank(gameConfig.wordLength);

        Set<String> wordPool = new HashSet<>();

        while (wordPool.size() < gameConfig.roundCount) {
            int randomIdx = ThreadLocalRandom
                .current()
                .nextInt(0, wordsOfRequestedLength.size());
            wordPool.add(wordsOfRequestedLength.get(randomIdx));
        }

        return wordPool;
    }

    private List<String> getWordsOfLengthFromWordBank(int length) throws IOException {
        try (InputStream resource = Thread
            .currentThread()
            .getContextClassLoader()
            .getResourceAsStream("wordbank_id.txt")) {
            assert resource != null;

            try (Scanner wordBank = new Scanner(resource)) {
                List<String> wordsOfRequestedLength = new ArrayList<>();

                while (wordBank.hasNextLine()) {
                    String word = wordBank.nextLine();
                    if (word.length() == length) {
                        wordsOfRequestedLength.add(word);
                    }
                }

                return wordsOfRequestedLength;
            }
        }
    }

    private void startGameWithRandomWordService(AnagramGameConfig gameConfig) {
        randomWordService
            .getWordsOfLength(gameConfig.roundCount, gameConfig.wordLength)
            .subscribe()
            .with(
                words -> announceAndStartGame(words, gameConfig),
                err -> broadcast(new ChatMessage("FAILED to fetch words. Cannot start game."))
            );
    }

    private void announceAndStartGame(
        Set<String> words,
        AnagramGameConfig gameConfig
    ) {
        broadcast(new ChatMessage(gameConfig.generateAnnouncementMessage()));

        this.gameConfig = gameConfig;

        wordsForRound.addAll(words);

        startRound();
    }

    private List<Message> handleAnswer(String name, boolean isSkip) {
        if (currentRoundAnswerers.contains(name)) {
            return List.of(new ChatMessage("You already answered..."));
        }

        if (!isSkip) {
            nameToPlayerInfo.get(name).points += currentRoundAnswerers.size() == 0 ? 2 : 1;
        }

        currentRoundAnswerers.add(name);
        broadcast(new ChatMessage(name + (isSkip ? " skipped!" : " answered successfully!")));

        if (roundEndTimeoutHandle != null
            && currentRoundAnswerers.size() == nameToPlayerInfo.size()) {
            roundEndTimeoutHandle.cancel();
            endCurrentRound();
        }

        return List.of(new ChatMessage(
            "You are #" + currentRoundAnswerers.size() + "/" + nameToPlayerInfo.size()
                + " to " + (isSkip ? "skip" : "answer") + " this round."
        ));
    }

    private void startRound() {
        if (gameConfig == null) {
            broadcast(new ChatMessage("CANNOT start round because configuration is empty!"));
            return;
        }

        currentRoundAnswerers.clear();

        if (wordsForRound.size() == 0) {
            broadcast(new FinishedGameMessage());
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

        OffsetDateTime roundFinishTime = OffsetDateTime
            .now()
            .plusSeconds(gameConfig.secondsPerRound);
        broadcast(new OngoingRoundMessage(shuffledWord, roundFinishTime.toString()));

        roundEndTimeoutHandle = setTimeout(
            this::endCurrentRound,
            Duration.ofSeconds(gameConfig.secondsPerRound)
        );
    }

    private void endCurrentRound() {
        if (gameConfig == null) {
            broadcast(new ChatMessage("CANNOT end round because configuration is empty!"));
            return;
        }

        OffsetDateTime nextRoundStartTime = OffsetDateTime
            .now()
            .plusSeconds(gameConfig.secondsPerRoundEnding);

        broadcast(new ChatMessage(
            "Points:\n" + nameToPointsTable() + "\n" + (wordsForRound.size() - 1)
                + " round(s) left."));
        if (currentWord != null) {
            broadcast(new FinishedRoundMessage(currentWord, nextRoundStartTime.toString()));
            wordsForRound.remove(currentWord);
        } else {
            broadcast(new FinishedRoundMessage("INVALID_STATE", nextRoundStartTime.toString()));
        }

        currentWord = null;

        setTimeout(this::startRound, Duration.ofSeconds(gameConfig.secondsPerRoundEnding));
    }

    private String nameToPointsTable() {
        return nameToPlayerInfo
            .entrySet()
            .stream()
            .sorted((a, b) -> b.getValue().points - a.getValue().points)
            .map(entry -> entry.getKey() + " -> " + entry.getValue().points)
            .collect(Collectors.joining("\n"));
    }

    private void broadcast(Message message) {
        onBroadcastRequest.accept(message);
    }

}
