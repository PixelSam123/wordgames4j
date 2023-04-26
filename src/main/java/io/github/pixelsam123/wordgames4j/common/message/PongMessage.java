package io.github.pixelsam123.wordgames4j.common.message;

import org.jetbrains.annotations.Nullable;

public class PongMessage implements Message {

    @Override
    public String getType() {
        return "PongMessage";
    }

    @Override
    public @Nullable Object getContent() {
        return null;
    }

    @Override
    public String toString() {
        return "PongMessage{}";
    }

}
