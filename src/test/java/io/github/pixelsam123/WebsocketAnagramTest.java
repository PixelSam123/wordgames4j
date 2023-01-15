package io.github.pixelsam123;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class WebsocketAnagramTest {

    private static final LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @TestHTTPResource("/ws/anagram")
    public URI uri;

    @InjectMock
    @RestClient
    public RandomWordService randomWordService;

    @BeforeEach
    public void setup() {
        Mockito
            .when(randomWordService.getWordsOfLength(1, 5))
            .thenReturn(Uni.createFrom().item(Set.of("place")));
    }

    @Test
    public void happyPath() throws Exception {
        try (Session session = ContainerProvider
            .getWebSocketContainer()
            .connectToServer(Client.class, uri)) {
            ObjectNode asksForNameEntry = constructJson(
                new SimpleEntry<>("type", "ChatMessage"),
                new SimpleEntry<>("content", "Type /help for help. Please enter name in chat.")
            );

            assertEquals(
                asksForNameEntry,
                jsonMapper.readTree(messages.poll(10, TimeUnit.SECONDS))
            );

            ObjectNode announcesJoiningPlayer = constructJson(
                new SimpleEntry<>("type", "ChatMessage"),
                new SimpleEntry<>("content", "PlayerOne joined!")
            );
            session.getAsyncRemote().sendText("PlayerOne");

            assertEquals(
                announcesJoiningPlayer,
                jsonMapper.readTree(messages.poll(10, TimeUnit.SECONDS))
            );

            ObjectNode announcesChat = constructJson(
                new SimpleEntry<>("type", "ChatMessage"),
                new SimpleEntry<>("content", "PlayerOne: Heyy, I'm chatting!")
            );
            session.getAsyncRemote().sendText("Heyy, I'm chatting!");

            assertEquals(
                announcesChat,
                jsonMapper.readTree(messages.poll(10, TimeUnit.SECONDS))
            );

            ObjectNode announcesRoundStart = constructJson(
                new SimpleEntry<>("type", "ChatMessage"),
                new SimpleEntry<>(
                    "content",
                    "1 rounds started with time per round of 5 seconds! Word length: 4"
                )
            );
            session.getAsyncRemote().sendText("/start 4 1 5");

            assertEquals(
                announcesRoundStart,
                jsonMapper.readTree(messages.poll(10, TimeUnit.SECONDS))
            );
        }
    }

    @SafeVarargs
    private ObjectNode constructJson(SimpleEntry<String, String>... entries) {
        ObjectNode json = jsonMapper.createObjectNode();
        for (SimpleEntry<String, String> entry : entries) {
            json.put(entry.getKey(), entry.getValue());
        }

        return json;
    }

    @ClientEndpoint
    public static class Client {

        @OnMessage
        public void message(String message) {
            messages.add(message);
        }

    }

}
