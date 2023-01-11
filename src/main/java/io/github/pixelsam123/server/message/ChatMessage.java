package io.github.pixelsam123.server.message;

public class ChatMessage implements IServerMessage {

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
