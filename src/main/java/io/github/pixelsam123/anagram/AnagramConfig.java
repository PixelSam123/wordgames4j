package io.github.pixelsam123.anagram;

public class AnagramConfig {

    public final int timePerRound;
    public final int timePerRoundEnding;

    public AnagramConfig(int timePerRound, int timePerRoundEnding) {
        this.timePerRound = timePerRound;
        this.timePerRoundEnding = timePerRoundEnding;
    }

    @Override
    public String toString() {
        return "AnagramConfig{"
            + "timePerRound=" + timePerRound
            + ", timePerRoundEnding=" + timePerRoundEnding
            + '}';
    }

}
