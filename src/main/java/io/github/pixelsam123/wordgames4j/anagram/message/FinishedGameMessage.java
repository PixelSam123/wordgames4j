package io.github.pixelsam123.wordgames4j.anagram.message;

import io.github.pixelsam123.wordgames4j.common.message.Message;
import org.jetbrains.annotations.Nullable;

public class FinishedGameMessage implements Message {

    @Override
    public String getType() {
        return "FinishedGame";
    }

    @Override
    public @Nullable Object getContent() {
        return null;
    }

    @Override
    public String toString() {
        return "FinishedGameMessage{}";
    }

}
