package io.github.pixelsam123.anagram;

import io.github.pixelsam123.RandomWordService;
import io.github.pixelsam123.anagram.message.FinishedGame;
import io.github.pixelsam123.anagram.message.FinishedRoundInfo;
import io.github.pixelsam123.anagram.message.OngoingRoundInfo;
import io.github.pixelsam123.common.IRoomInterceptor;
import io.github.pixelsam123.common.message.ChatMessage;
import io.github.pixelsam123.common.message.IMessage;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.impl.ConcurrentHashSet;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.github.pixelsam123.common.utils.UtilsAsync.setTimeout;

public class AnagramRoom implements IRoomInterceptor {

    private final Map<String, AnagramPlayerInfo> nameToPlayerInfo = new ConcurrentHashMap<>();

    private @Nullable AnagramConfig gameConfig = null;

    private @Nullable String currentWord = null;
    private @Nullable Cancellable roundEndTimeoutHandle = null;
    private final Set<String> currentRoundAnswerers = new ConcurrentHashSet<>();
    private final Set<String> wordsForRound = new ConcurrentHashSet<>();

    private final RandomWordService randomWordService;
    private final Consumer<IMessage> onMessageToBeBroadcast;

    public AnagramRoom(
        RandomWordService randomWordService,
        Consumer<IMessage> onMessageToBeBroadcast
    ) {
        this.randomWordService = randomWordService;
        this.onMessageToBeBroadcast = onMessageToBeBroadcast;
    }

    @Override
    public List<IMessage> interceptAfterUsernameAdded(String username) {
        nameToPlayerInfo.put(username, new AnagramPlayerInfo());

        List<IMessage> messagesToSend = new ArrayList<>();
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
    public @Nullable List<IMessage> interceptMessage(String username, String clientMessage) {
        if (clientMessage.matches("/help")) {
            return handleHelpCommand();
        }
        if (clientMessage.matches("/list")) {
            return handleListCommand();
        }
        if (roundEndTimeoutHandle == null && clientMessage.matches(
            "/start \\d+ \\d+ \\d+ (true|false)")) {
            return handleGameStartCommand(clientMessage);
        }
        if (clientMessage.toLowerCase().equals(currentWord)) {
            return handleCorrectAnswer(username);
        }
        if (currentWord != null && clientMessage.matches("/skip")) {
            return handleSkippingAnswer(username);
        }

        return null;
    }

    private List<IMessage> handleHelpCommand() {
        return List.of(new ChatMessage("""
            Commands:
            /help
            Show this message
                        
            /list
            List the players in a room
                        
            /start {wordLength} {roundCount} {timePerRound} {isIndonesian:boolean}
            Start a new round. Time per round is in seconds.
                        
            /skip
            Skip your turn in a round.
            """.trim()));
    }

    private List<IMessage> handleListCommand() {
        return List.of(new ChatMessage(nameToPointsTable()));
    }

    private List<IMessage> handleGameStartCommand(String command) {
        String[] commandParts = command.split(" ");
        int wordLength = Integer.parseInt(commandParts[1]);
        int roundCount = Integer.parseInt(commandParts[2]);
        int timePerRound = Integer.parseInt(commandParts[3]);
        boolean isIndonesian = Boolean.parseBoolean(commandParts[4]);

        String roundAnnouncementMessage =
            roundCount + " rounds started with time per round of " + timePerRound
                + " seconds!\nWord length: " + wordLength + "\nIs Indonesian: " + isIndonesian;

        if (isIndonesian) {
            Uni.createFrom().item(Unchecked.supplier(() -> {
                try (Scanner wordBank = new Scanner(new File("wordbank_id.txt"))) {
                    List<String> wordsOfRequestedLength = new ArrayList<>();
                    while (wordBank.hasNextLine()) {
                        String word = wordBank.nextLine();
                        if (word.length() == wordLength) {
                            wordsOfRequestedLength.add(word);
                        }
                    }

                    List<String> wordPool = new ArrayList<>();
                    while (wordPool.size() < roundCount) {
                        int randomIdx = ThreadLocalRandom
                            .current()
                            .nextInt(0, wordsOfRequestedLength.size());
                        wordPool.add(wordsOfRequestedLength.get(randomIdx));
                    }

                    return Set.copyOf(wordPool);
                }
            })).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).subscribe().with(
                words -> announceAndStartGame(words, roundAnnouncementMessage, timePerRound),
                err -> broadcast(new ChatMessage("FAILED to read word bank. Cannot start game."))
            );
        } else {
            randomWordService.getWordsOfLength(roundCount, wordLength).subscribe().with(
                words -> announceAndStartGame(words, roundAnnouncementMessage, timePerRound),
                err -> broadcast(new ChatMessage("FAILED to fetch words. Cannot start game."))
            );
        }


        return List.of(new ChatMessage("Requested a new game."));
    }

    private void announceAndStartGame(
        Set<String> words,
        String roundAnnouncementMessage,
        int timePerRound
    ) {
        broadcast(new ChatMessage(roundAnnouncementMessage));

        gameConfig = new AnagramConfig(timePerRound, 5);

        wordsForRound.addAll(words);

        startRound();
    }

    private List<IMessage> handleCorrectAnswer(String name) {
        if (currentRoundAnswerers.contains(name)) {
            return List.of(new ChatMessage("You already answered..."));
        }

        nameToPlayerInfo.get(name).points += currentRoundAnswerers.size() == 0 ? 2 : 1;
        currentRoundAnswerers.add(name);
        broadcast(new ChatMessage(name + " answered successfully!"));

        if (roundEndTimeoutHandle != null
            && currentRoundAnswerers.size() == nameToPlayerInfo.size()) {
            roundEndTimeoutHandle.cancel();
            endCurrentRound();
        }

        return List.of(new ChatMessage(
            "You are #" + currentRoundAnswerers.size() + "/" + nameToPlayerInfo.size()
                + " to answer this round."
        ));
    }

    private List<IMessage> handleSkippingAnswer(String name) {
        if (currentRoundAnswerers.contains(name)) {
            return List.of(new ChatMessage("You already answered..."));
        }

        currentRoundAnswerers.add(name);
        broadcast(new ChatMessage(name + " skipped!"));

        if (roundEndTimeoutHandle != null
            && currentRoundAnswerers.size() == nameToPlayerInfo.size()) {
            roundEndTimeoutHandle.cancel();
            endCurrentRound();
        }

        return List.of(new ChatMessage(
            "You are #" + currentRoundAnswerers.size() + "/" + nameToPlayerInfo.size()
                + " to skip this round."
        ));
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

        broadcast(new ChatMessage(
            "Points:\n" + nameToPointsTable() + "\n" + (wordsForRound.size() - 1)
                + " round(s) left."));
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

    private void broadcast(IMessage message) {
        onMessageToBeBroadcast.accept(message);
    }

}
