package com.ylh.backend.Socket;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SocketMessage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String type;
    private final String body;

    public SocketMessage(String type, String body) {
        this.type = type;
        this.body = body != null ? body : "{}";
    }

    public String getType() {
        return type;
    }

    public String getBody() {
        return body;
    }

    public static SocketMessage parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int sepIdx = raw.indexOf('|');
        if (sepIdx == -1) {
            return new SocketMessage(raw.trim(), "{}");
        }
        String type = raw.substring(0, sepIdx).trim();
        String body = raw.substring(sepIdx + 1).trim();
        return new SocketMessage(type, body);
    }

    public String serialize() {
        return type + "|" + body + "\n";
    }

    public <T> T getBodyAs(Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(body, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse message body as " + clazz.getSimpleName() + ": " + body, e);
        }
    }

    public static SocketMessage auth(String agentKey) {
        return new SocketMessage(MessageConstants.TYPE_AUTH,
                "{\"agentKey\":\"" + escapeJson(agentKey) + "\"}");
    }

    public static SocketMessage hostInfo(Object data) {
        return wrap(MessageConstants.TYPE_HOST_INFO, data);
    }

    public static SocketMessage hostMetric(Object data) {
        return wrap(MessageConstants.TYPE_HOST_METRIC, data);
    }

    public static SocketMessage connected() {
        return new SocketMessage(MessageConstants.TYPE_CONNECTED, "{\"version\":\"1.0\"}");
    }

    public static SocketMessage ok(String message) {
        return new SocketMessage(MessageConstants.TYPE_OK,
                "{\"message\":\"" + escapeJson(message) + "\"}");
    }

    public static SocketMessage okWithId(String message, Long id) {
        return new SocketMessage(MessageConstants.TYPE_OK,
                "{\"message\":\"" + escapeJson(message) + "\",\"id\":" + id + "}");
    }

    public static SocketMessage error(String message) {
        return new SocketMessage(MessageConstants.TYPE_ERROR,
                "{\"message\":\"" + escapeJson(message) + "\"}");
    }

    public static SocketMessage command(String command) {
        return new SocketMessage(MessageConstants.TYPE_CMD,
                "{\"command\":\"" + escapeJson(command) + "\"}");
    }

    public static SocketMessage processResync() {
        return command(MessageConstants.CMD_PROCESS_RESYNC);
    }

    private static SocketMessage wrap(String type, Object data) {
        try {
            return new SocketMessage(type, OBJECT_MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize " + type + " message", e);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "SocketMessage{type='" + type + "', body='" + body + "'}";
    }
}
