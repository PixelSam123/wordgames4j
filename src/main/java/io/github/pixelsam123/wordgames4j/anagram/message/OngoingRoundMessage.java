package io.github.pixelsam123.wordgames4j.anagram.message;

import io.github.pixelsam123.wordgames4j.common.message.Message;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class OngoingRoundMessage implements Message {

    private final Map<String, String> content;

    public OngoingRoundMessage(String wordToGuess, String roundFinishTime) {
        content = Map.ofEntries(
            Map.entry("word_to_guess", wordToGuess),
            Map.entry("round_finish_time", roundFinishTime)
        );
    }

    @Override
    public String getType() {
        return "OngoingRoundInfo";
    }

    @Override
    public @Nullable Object getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "OngoingRoundMessage{" + "content=" + content + '}';
    }

}
