package io.github.pixelsam123.wordgames4j.common.room;

public class RoomConfig {

    public final String identifier;
    public final int maxUsers;

    public RoomConfig(String identifier, int maxUsers) {
        this.identifier = identifier;
        this.maxUsers = maxUsers;
    }

}
