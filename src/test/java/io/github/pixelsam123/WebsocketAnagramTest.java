package io.github.pixelsam123;

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

    @TestHTTPResource("/ws/anagram")
    public URI uri;

    @Test
    public void happyPath() throws Exception {
        try (Session session = ContainerProvider
            .getWebSocketContainer()
            .connectToServer(Client.class, uri)) {
            assertEquals("""
                {"type":"ChatMessage","content":"Please enter name in chat."}
                """.trim(), messages.poll(10, TimeUnit.SECONDS));

            session.getAsyncRemote().sendText("PlayerOne");
            assertEquals("""
                {"type":"ChatMessage","content":"PlayerOne joined!"}
                """.trim(), messages.poll(10, TimeUnit.SECONDS));

            session.getAsyncRemote().sendText("Heyy, I'm chatting!");
            assertEquals("""
                {"type":"ChatMessage","content":"PlayerOne: Heyy, I'm chatting!"}
                """.trim(), messages.poll(10, TimeUnit.SECONDS));

            session.getAsyncRemote().sendText("/start 10");
            assertEquals("""
                {"type":"ChatMessage","content":"Round started with time per round of 10 seconds!"}
                """.trim(), messages.poll(10, TimeUnit.SECONDS));
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
