package io.github.pixelsam123.common.message;

public class ChatMessage implements IMessage {

    private final String content;

    public ChatMessage(String content) {
        this.content = content;
    }

    @Override
    public String getType() {
        return "ChatMessage";
    }

    @Override
    public Object getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "ChatMessage{" + "content='" + content + '\'' + '}';
    }

}
