package io.github.pixelsam123.wordgames4j.common.room;

import io.github.pixelsam123.wordgames4j.common.message.Message;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface RoomInterceptor {

    /**
     * @param username Username of the user that has just successfully registered
     * @return Private messages to send before this user is announced as having joined the room.
     */
    List<Message> interceptAfterUsernameAdded(String username);

    void interceptAfterUsernameRemoved(String username);

    /**
     * @param username      Username that sent the message
     * @param clientMessage The message
     * @return Intercepted message. If this isn't null, the normal broadcast chat message
     * will be replaced by private messages to the user that sent this message.
     */
    @Nullable List<Message> interceptMessage(String username, String clientMessage);

}
