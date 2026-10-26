package com.wordonline.server.local;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@code init} line, the first line the client writes to stdin:
 * {@code {"type":"init","token":"<hex>","dataDir":"<absolute path>","staticDataPath":"<absolute path>"}}.
 *
 * <p>The token travels over stdin rather than as an argument so other users of the machine cannot
 * read it from the process list. It is the only credential this server accepts.
 *
 * @param token          at least 32 random bytes as hex; the bearer token for HTTP, the WebSocket
 *                       handshake and STOMP CONNECT
 * @param dataDir        directory the server may write to; logs go under {@code logs/}
 * @param staticDataPath the static data JSON imported into the in-memory database
 */
public record LocalInit(String token, Path dataDir, Path staticDataPath) {

    static final int MINIMUM_TOKEN_HEX_LENGTH = 64;
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Path logDirectory() {
        return dataDir.resolve("logs");
    }

    /** Where the JVM and the embedded web server put temporary files instead of the user's temp folder. */
    public Path temporaryDirectory() {
        return dataDir.resolve("tmp");
    }

    public static LocalInit parse(String line) {
        if (line == null) {
            throw new LocalStartupException("stdin closed before the init line arrived");
        }
        JsonNode node;
        try {
            node = OBJECT_MAPPER.readTree(line);
        } catch (JsonProcessingException e) {
            throw new LocalStartupException("the first stdin line is not JSON");
        }
        if (node == null || !node.isObject() || !"init".equals(node.path("type").asText())) {
            throw new LocalStartupException("the first stdin line must be {\"type\":\"init\",...}");
        }

        String token = node.path("token").asText("");
        if (token.length() < MINIMUM_TOKEN_HEX_LENGTH || !HEX.matcher(token).matches()) {
            throw new LocalStartupException("init token must be at least 32 bytes of hex ("
                    + MINIMUM_TOKEN_HEX_LENGTH + " hex characters)");
        }

        Path dataDir = absolutePath(node, "dataDir");
        Path staticDataPath = absolutePath(node, "staticDataPath");
        if (!Files.isRegularFile(staticDataPath)) {
            throw new LocalStartupException("staticDataPath does not exist: " + staticDataPath);
        }
        return new LocalInit(token, dataDir, staticDataPath);
    }

    private static Path absolutePath(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new LocalStartupException("init is missing " + field);
        }
        Path path;
        try {
            path = Path.of(value);
        } catch (InvalidPathException e) {
            throw new LocalStartupException(field + " is not a valid path: " + value);
        }
        if (!path.isAbsolute()) {
            throw new LocalStartupException(field + " must be an absolute path: " + value);
        }
        return path.normalize();
    }

    // The token must never reach a log line through a record's generated toString.
    @Override
    public String toString() {
        return "LocalInit[dataDir=" + dataDir + ", staticDataPath=" + staticDataPath + "]";
    }
}
