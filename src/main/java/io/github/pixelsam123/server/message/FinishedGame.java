package io.github.pixelsam123.server.message;

public class FinishedGame implements IServerMessage {

    @Override
    public String getType() {
        return "FinishedGame";
    }

    @Override
    public Object getContent() {
        return null;
    }

}
