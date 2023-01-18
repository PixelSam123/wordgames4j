package io.github.pixelsam123.common;

import io.github.pixelsam123.common.message.IMessage;
import org.jetbrains.annotations.Nullable;

public interface IRoomInterceptor {

    /**
     * @param username Username of the user that has just successfully registered
     * @return Private message to send before this user is announced as having joined the room.
     */
    IMessage interceptAfterUsernameAdded(String username);

    void interceptAfterUsernameRemoved(String username);

    /**
     * @param username      Username that sent the message
     * @param clientMessage The message
     * @return Intercepted message. If this isn't null, the normal broadcast chat message
     * will be replaced by a private message to the user that sent this message.
     */
    @Nullable IMessage interceptMessage(String username, String clientMessage);

}
