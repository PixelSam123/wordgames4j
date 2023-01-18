package io.github.pixelsam123.anagram.message;

import io.github.pixelsam123.common.message.IMessage;

public class FinishedGame implements IMessage {

    @Override
    public String getType() {
        return "FinishedGame";
    }

    @Override
    public Object getContent() {
        return null;
    }

    @Override
    public String toString() {
        return "FinishedGame{}";
    }

}
