package io.github.pixelsam123;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pixelsam123.server.message.IServerMessage;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

public class JsonEncoder implements Encoder.Text<IServerMessage> {

    private final ObjectMapper jsonMapper;

    public JsonEncoder() {
        jsonMapper = new ObjectMapper();
    }

    @Override
    public String encode(IServerMessage serverMessage) throws EncodeException {
        ObjectNode message = jsonMapper.createObjectNode();
        message.put("type", serverMessage.getType());
        message.putPOJO("content", serverMessage.getContent());

        try {
            return jsonMapper.writer().writeValueAsString(serverMessage);
        } catch (JsonProcessingException e) {
            throw new EncodeException(serverMessage, e.getMessage(), e.getCause());
        }
    }

    @Override
    public void init(EndpointConfig config) {
    }

    @Override
    public void destroy() {
    }

}
