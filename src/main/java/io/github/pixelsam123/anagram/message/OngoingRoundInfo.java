package io.github.pixelsam123.anagram.message;

import io.github.pixelsam123.common.message.IMessage;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class OngoingRoundInfo implements IMessage {

    private final Map<String, String> content;

    public OngoingRoundInfo(String wordToGuess, String roundFinishTime) {
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
        return "OngoingRoundInfo{" + "content=" + content + '}';
    }

}
