package io.github.pixelsam123.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pixelsam123.common.message.Message;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

public class JsonEncoder implements Encoder.Text<Message> {

    private final ObjectMapper jsonMapper;

    public JsonEncoder() {
        jsonMapper = new ObjectMapper();
    }

    @Override
    public String encode(Message serverMessage) throws EncodeException {
        ObjectNode jsonServerMessage = jsonMapper.createObjectNode();
        jsonServerMessage.put("type", serverMessage.getType());
        jsonServerMessage.putPOJO("content", serverMessage.getContent());

        try {
            return jsonMapper.writer().writeValueAsString(jsonServerMessage);
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
