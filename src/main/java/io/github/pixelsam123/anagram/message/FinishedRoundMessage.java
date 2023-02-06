package io.github.pixelsam123.anagram.message;

import io.github.pixelsam123.common.message.Message;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class FinishedRoundMessage implements Message {

    private final Map<String, String> content;

    public FinishedRoundMessage(String wordAnswer, String toNextRoundTime) {
        content = Map.ofEntries(
            Map.entry("word_answer", wordAnswer),
            Map.entry("to_next_round_time", toNextRoundTime)
        );
    }

    @Override
    public String getType() {
        return "FinishedRoundInfo";
    }

    @Override
    public @Nullable Object getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "FinishedRoundMessage{" + "content=" + content + '}';
    }

}
