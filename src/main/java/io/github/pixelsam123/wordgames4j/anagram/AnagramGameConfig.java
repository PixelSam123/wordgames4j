package io.github.pixelsam123.wordgames4j.anagram;

public class AnagramGameConfig {

    public final int wordLength;
    public final int roundCount;
    public final int secondsPerRound;
    public final int secondsPerRoundEnding;
    public final boolean isIndonesian;

    private AnagramGameConfig(
        int wordLength,
        int roundCount,
        int secondsPerRound,
        int secondsPerRoundEnding,
        boolean isIndonesian
    ) {
        this.wordLength = wordLength;
        this.roundCount = roundCount;
        this.secondsPerRound = secondsPerRound;
        this.secondsPerRoundEnding = secondsPerRoundEnding;
        this.isIndonesian = isIndonesian;
    }

    public static AnagramGameConfig parseFromGameStartCommand(String gameStartCommand) {
        String[] commandParts = gameStartCommand.split(" ");

        int wordLength = Integer.parseInt(commandParts[1]);
        int roundCount = Integer.parseInt(commandParts[2]);
        int secondsPerRound = Integer.parseInt(commandParts[3]);
        int secondsPerRoundEnding = 5;
        boolean isIndonesian = Boolean.parseBoolean(commandParts[4]);

        return new AnagramGameConfig(
            wordLength,
            roundCount,
            secondsPerRound,
            secondsPerRoundEnding,
            isIndonesian
        );
    }

    public String generateAnnouncementMessage() {
        return roundCount + " rounds started with time per round of " + secondsPerRound
            + " seconds!\nWord length: " + wordLength + "\nIs Indonesian: " + isIndonesian;
    }

    @Override
    public String toString() {
        return "AnagramGameConfig{"
            + "wordLength=" + wordLength
            + ", roundCount=" + roundCount
            + ", secondsPerRound=" + secondsPerRound
            + ", secondsPerRoundEnding=" + secondsPerRoundEnding
            + ", isIndonesian=" + isIndonesian
            + '}';
    }

}
