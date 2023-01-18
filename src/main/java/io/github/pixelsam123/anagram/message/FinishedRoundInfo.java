package io.github.pixelsam123.anagram.message;

import io.github.pixelsam123.common.message.IMessage;

import java.util.Map;

public class FinishedRoundInfo implements IMessage {

    private final Map<String, String> content;

    public FinishedRoundInfo(String wordAnswer, String toNextRoundTime) {
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
    public Object getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "FinishedRoundInfo{" + "content=" + content + '}';
    }

}
