package com.ylh.client.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

public class SocketMessage {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

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
        return GSON.fromJson(body, clazz);
    }

    public String getCommand() {
        CommandBody cmd = getBodyAs(CommandBody.class);
        return cmd != null ? cmd.command : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getConfigMap() {
        try {
            return GSON.fromJson(body, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getCommandConfig() {
        try {
            Map<String, Object> map = GSON.fromJson(body, Map.class);
            if (map != null && map.containsKey("config")) {
                Object configObj = map.get("config");
                if (configObj instanceof Map) {
                    return (Map<String, String>) configObj;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public String getMessage() {
        ResponseBody resp = getBodyAs(ResponseBody.class);
        return resp != null ? resp.message : null;
    }

    public boolean isOk() {
        return MessageConstants.TYPE_OK.equals(type);
    }

    public boolean isError() {
        return MessageConstants.TYPE_ERROR.equals(type);
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

    public static SocketMessage processList(Object data) {
        return wrap(MessageConstants.TYPE_PROCESS_LIST, data);
    }

    public static SocketMessage processListFull(Object data) {
        return wrap(MessageConstants.TYPE_PROCESS_LIST_FULL, data);
    }

    public static SocketMessage processListDelta(Object data) {
        return wrap(MessageConstants.TYPE_PROCESS_LIST_DELTA, data);
    }

    private static SocketMessage wrap(String type, Object data) {
        return new SocketMessage(type, GSON.toJson(data));
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class CommandBody {
        String command;
    }

    private static class ResponseBody {
        String message;
    }

    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            String str = in.nextString();
            return str != null && !str.isEmpty() ? LocalDateTime.parse(str) : null;
        }
    }

    @Override
    public String toString() {
        return "SocketMessage{type='" + type + "', body='" + body + "'}";
    }
}
