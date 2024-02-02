package io.github.pixelsam123.wordgames4j.anagram;

public class AnagramGameConfig {

    public final String dictionary;
    public final int roundCount;
    public final int secondsPerRound;
    public final int secondsPerRoundEnding;
    public final int wordLength;

    private AnagramGameConfig(
        String dictionary,
        int roundCount,
        int secondsPerRound,
        int secondsPerRoundEnding,
        int wordLength
    ) {
        this.dictionary = dictionary;
        this.roundCount = roundCount;
        this.secondsPerRound = secondsPerRound;
        this.secondsPerRoundEnding = secondsPerRoundEnding;
        this.wordLength = wordLength;
    }

    public static AnagramGameConfig parseFromGameStartCommand(String gameStartCommand) {
        String[] commandParts = gameStartCommand.split(" +");

        String dictionary = commandParts.length > 1 ? commandParts[1] : "id";
        int roundCount = commandParts.length > 2 ? Integer.parseInt(commandParts[2]) : 10;
        int secondsPerRound = commandParts.length > 3 ? Integer.parseInt(commandParts[3]) : 20;
        int secondsPerRoundEnding = 5;
        int wordLength = commandParts.length > 4
            ? Integer.parseInt(commandParts[4])
            : dictionary.equals("gi") || dictionary.equals("hoyo") || dictionary.equals("hsr")
                || dictionary.equals("js-topic")
                ? -1
                : 5;

        return new AnagramGameConfig(
            dictionary,
            roundCount,
            secondsPerRound,
            secondsPerRoundEnding,
            wordLength
        );
    }

    public String generateAnnouncementMessage() {
        return roundCount + " rounds started with time per round of " + secondsPerRound
            + " seconds!\nWord length: " + wordLength + "\nDictionary: " + dictionary;
    }

    @Override
    public String toString() {
        return "AnagramGameConfig{" +
            "dictionary='" + dictionary + '\'' +
            ", roundCount=" + roundCount +
            ", secondsPerRound=" + secondsPerRound +
            ", secondsPerRoundEnding=" + secondsPerRoundEnding +
            ", wordLength=" + wordLength +
            '}';
    }

}
