package com.wordonline.server.local;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The stdout half of the control protocol between the desktop client and a locally hosted server.
 *
 * <p>One JSON object per line, UTF-8, {@code \n} terminated. The client parses every stdout line as
 * a protocol message, so nothing else may ever reach this stream: {@link LocalLauncher} hands this
 * class the real stdout and points {@link System#out} at stderr before anything else runs.
 *
 * <p>Every line goes through one synchronized method, so a match result written from a loop thread
 * cannot interleave with a line written by the launcher.
 */
public class LocalProtocol {

    private final PrintStream out;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalProtocol(OutputStream out) {
        this.out = new PrintStream(out, false, StandardCharsets.UTF_8);
    }

    public void ready(int port, String serverVersion, int schemaVersion, String staticVersion) {
        Map<String, Object> message = message("ready");
        message.put("port", port);
        message.put("serverVersion", serverVersion);
        message.put("schemaVersion", schemaVersion);
        message.put("staticVersion", staticVersion);
        send(message);
    }

    public void error(String reason) {
        Map<String, Object> message = message("error");
        message.put("reason", reason);
        send(message);
    }

    public void matchStats(LocalMatchStats stats) {
        send(stats);
    }

    public void matchResult(LocalMatchResult result) {
        send(result);
    }

    public synchronized void send(Object message) {
        String line;
        try {
            line = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("protocol message is not serializable", e);
        }
        out.print(line);
        out.print('\n');
        out.flush();
    }

    private static Map<String, Object> message(String type) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        return message;
    }
}
