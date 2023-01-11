package io.github.pixelsam123;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.net.URI;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class WebsocketAnagramTest {

    private static final LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @TestHTTPResource("/ws/anagram")
    public URI uri;

    @Test
    public void happyPath() throws Exception {
        try (Session session = ContainerProvider
            .getWebSocketContainer()
            .connectToServer(Client.class, uri)) {
            ObjectNode asksForNameEntry = jsonMapper.createObjectNode();
            asksForNameEntry.put("type", "ChatMessage");
            asksForNameEntry.put("content", "Please enter name in chat.");

            assertEquals(
                asksForNameEntry,
                jsonMapper.readTree(messages.poll(10, TimeUnit.SECONDS))
            );

            ObjectNode announcesJoiningPlayer = jsonMapper.createObjectNode();
            announcesJoiningPlayer.put("type", "ChatMessage");
            announcesJoiningPlayer.put("content", "PlayerOne joined!");

            session.getAsyncRemote().sendText("PlayerOne");
            assertEquals(
                announcesJoiningPlayer,
                jsonMapper.readTree(messages.poll(10, TimeUnit.SECONDS))
            );

            ObjectNode announcesChat = jsonMapper.createObjectNode();
            announcesChat.put("type", "ChatMessage");
            announcesChat.put("content", "PlayerOne: Heyy, I'm chatting!");

            session.getAsyncRemote().sendText("Heyy, I'm chatting!");
            assertEquals(
                announcesChat,
                jsonMapper.readTree(messages.poll(10, TimeUnit.SECONDS))
            );

            ObjectNode announcesRoundStart = jsonMapper.createObjectNode();
            announcesRoundStart.put("type", "ChatMessage");
            announcesRoundStart.put(
                "content",
                "2 rounds started with time per round of 5 seconds!"
            );

            session.getAsyncRemote().sendText("/start 2 5");
            assertEquals(
                announcesRoundStart,
                jsonMapper.readTree(messages.poll(10, TimeUnit.SECONDS))
            );
        }
    }

    @ClientEndpoint
    public static class Client {

        @OnMessage
        public void message(String message) {
            messages.add(message);
        }

    }

}
