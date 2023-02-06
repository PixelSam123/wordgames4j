package io.github.pixelsam123.common.message;

import org.jetbrains.annotations.Nullable;

public class ChatMessage implements Message {

    private final String content;

    public ChatMessage(String content) {
        this.content = content;
    }

    @Override
    public String getType() {
        return "ChatMessage";
    }

    @Override
    public @Nullable Object getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "ChatMessage{" + "content='" + content + '\'' + '}';
    }

}
