package io.github.pixelsam123.wordgames4j.anagram;

import io.github.pixelsam123.wordgames4j.RandomWordService;
import io.github.pixelsam123.wordgames4j.anagram.message.FinishedGameMessage;
import io.github.pixelsam123.wordgames4j.anagram.message.FinishedRoundMessage;
import io.github.pixelsam123.wordgames4j.anagram.message.OngoingRoundMessage;
import io.github.pixelsam123.wordgames4j.common.message.ChatMessage;
import io.github.pixelsam123.wordgames4j.common.message.Message;
import io.github.pixelsam123.wordgames4j.common.room.RoomInterceptor;
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

    private @Nullable String currentShuffledWord = null;
    private @Nullable OffsetDateTime currentRoundFinishTime = null;

    private final RandomWordService randomWordService;
    private final Consumer<Message> onBroadcastRequest;

    public AnagramRoom(
        RandomWordService randomWordService, Consumer<Message> onBroadcastRequest
    ) {
        this.randomWordService = randomWordService;
        this.onBroadcastRequest = onBroadcastRequest;
    }

    @Override
    public List<Message> interceptAfterUsernameAdded(String username) {
        nameToPlayerInfo.put(username, new AnagramPlayerInfo());

        List<Message> messagesToSend = new ArrayList<>();
        messagesToSend.add(
            new ChatMessage("Welcome! Type /help for help, or for how to start the game.")
        );

        if (currentShuffledWord != null && currentRoundFinishTime != null) {
            messagesToSend.add(
                new OngoingRoundMessage(currentShuffledWord, currentRoundFinishTime.toString())
            );
        }

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
            if (clientMessage.matches("/start.*")) {
                return handleGameStartCommand(clientMessage);
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
            
            /start {dictionary}
            Start a game with default settings, but choose a dictionary.
            Available options are: id, hygames, en
                        
            /start {dictionary} {roundCount} {timePerRound}
            Start a new game with custom settings. Time per round is in seconds.
                        
            /start {dictionary} {roundCount} {timePerRound} {wordLength}
            Start a new game with custom settings. Time per round is in seconds.
            Additionally specify the word length. Ideal for big dictionaries.
            
            /skip
            Skip your turn in a round."""));
    }

    private List<Message> handleListCommand() {
        return List.of(new ChatMessage(nameToPointsTable()));
    }

    private List<Message> handleGameStartCommand(String command) {
        AnagramGameConfig gameConfig = AnagramGameConfig.parseFromGameStartCommand(command);

        if (gameConfig.wordLength == 1 || gameConfig.wordLength == 0
            || gameConfig.wordLength < -1) {
            return List.of(
                new ChatMessage("CANNOT start a game with word length of 1, 0, or under -1.")
            );
        }

        if (gameConfig.dictionary.equals("en")) {
            startGameWithRandomWordService(gameConfig);
        } else {
            startGameWithOfflineWordBank(gameConfig);
        }

        return List.of(new ChatMessage("Requested a new game. Settings:\n" + gameConfig));
    }

    private void startGameWithOfflineWordBank(AnagramGameConfig gameConfig) {
        Uni
            .createFrom()
            .item(Unchecked.supplier(() -> getWordPoolFromOfflineWordBank(
                gameConfig,
                gameConfig.dictionary + ".txt"
            )))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
            .subscribe()
            .with(
                words -> announceAndStartGame(words, gameConfig),
                err -> broadcast(new ChatMessage("FAILED to read word bank. Cannot start game."))
            );
    }

    private Set<String> getWordPoolFromOfflineWordBank(
        AnagramGameConfig gameConfig,
        String filename
    ) throws IOException {
        List<String> wordsOfRequestedLength =
            getWordsOfLengthFromWordBank(gameConfig.wordLength, filename);

        Set<String> wordPool = new HashSet<>();

        while (wordPool.size() < gameConfig.roundCount) {
            int randomIdx = ThreadLocalRandom.current().nextInt(0, wordsOfRequestedLength.size());
            wordPool.add(wordsOfRequestedLength.get(randomIdx));
        }

        return wordPool;
    }

    private List<String> getWordsOfLengthFromWordBank(int length, String filename)
        throws IOException {
        try (
            InputStream resource = Thread
                .currentThread()
                .getContextClassLoader()
                .getResourceAsStream(filename)
        ) {
            assert resource != null;

            try (Scanner wordBank = new Scanner(resource)) {
                List<String> wordsOfRequestedLength = new ArrayList<>();

                while (wordBank.hasNextLine()) {
                    String word = wordBank.nextLine();
                    if (length == -1 || word.length() == length) {
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
        Set<String> words, AnagramGameConfig gameConfig
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
            nameToPlayerInfo.get(name).points += currentRoundAnswerers.isEmpty() ? 3 : 2;
        }

        currentRoundAnswerers.add(name);
        broadcast(new ChatMessage(name + (isSkip ? " skipped!" : " answered successfully!")));

        if (roundEndTimeoutHandle != null
            && currentRoundAnswerers.size() == nameToPlayerInfo.size()) {
            roundEndTimeoutHandle.cancel();
            endCurrentRound();
        }

        return List.of(new ChatMessage(
            "You are #" + currentRoundAnswerers.size() + "/" + nameToPlayerInfo.size() + " to " +
                (isSkip ? "skip" : "answer") + " this round."
        ));
    }

    private void startRound() {
        if (gameConfig == null) {
            broadcast(new ChatMessage("CANNOT start round because configuration is empty!"));
            return;
        }

        currentRoundAnswerers.clear();

        if (wordsForRound.isEmpty()) {
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

        currentShuffledWord = getShuffledWord(currentWord);

        currentRoundFinishTime = OffsetDateTime.now().plusSeconds(gameConfig.secondsPerRound);
        broadcast(new OngoingRoundMessage(currentShuffledWord, currentRoundFinishTime.toString()));

        roundEndTimeoutHandle =
            setTimeout(this::endCurrentRound, Duration.ofSeconds(gameConfig.secondsPerRound));
    }

    private String getShuffledWord(String word) {
        List<Character> shuffledChars = new ArrayList<>();
        for (char character : word.toCharArray()) {
            shuffledChars.add(character);
        }
        Collections.shuffle(shuffledChars);

        String shuffledWord = shuffledChars
            .stream()
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();

        if (shuffledWord.equals(word)) {
            return getShuffledWord(word);
        }

        return shuffledWord;
    }

    private void endCurrentRound() {
        if (gameConfig == null) {
            broadcast(new ChatMessage("CANNOT end round because configuration is empty!"));
            return;
        }

        OffsetDateTime nextRoundStartTime =
            OffsetDateTime.now().plusSeconds(gameConfig.secondsPerRoundEnding);

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
        currentShuffledWord = null;
        currentRoundFinishTime = null;

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
