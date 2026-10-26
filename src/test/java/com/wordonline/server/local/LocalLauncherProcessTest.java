package com.wordonline.server.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The control protocol against the real boot jar in its own process, as the desktop client runs it:
 * {@code java -jar <jar> --spring.profiles.active=local} with stdin and stdout as pipes.
 *
 * <p>Gradle builds the jar before the tests and passes its path as {@code localServer.jar}; run from
 * an IDE without it, these tests are skipped.
 */
class LocalLauncherProcessTest {

    private static final Duration STARTUP_LIMIT = Duration.ofSeconds(90);
    private static final Set<String> PROTOCOL_TYPES = Set.of("ready", "error", "matchStats", "matchResult");
    private static final String TOKEN = "5a".repeat(32);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final BlockingQueue<String> stdoutLines = new LinkedBlockingQueue<>();
    private final List<String> allStdoutLines = new CopyOnWriteArrayList<>();

    @TempDir
    Path directory;

    private Process process;

    @BeforeEach
    void requireJar() {
        String jar = System.getProperty("localServer.jar");
        assumeTrue(jar != null && Files.isRegularFile(Path.of(jar)), "boot jar not built");
    }

    @AfterEach
    void stop() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Test
    void readyServesRequestsAndEndOfInputExits() throws Exception {
        start(fixture());

        JsonNode ready = nextLine();
        assertThat(ready.path("type").asText()).isEqualTo("ready");
        int port = ready.path("port").asInt();
        assertThat(port).isPositive();
        assertThat(ready.path("serverVersion").asText()).isNotBlank();
        assertThat(ready.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(ready.path("staticVersion").asText())
                .isEqualTo(objectMapper.readTree(fixture().toFile()).path("staticVersion").asText());

        HttpResponse<String> health = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/healthcheck")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        // On Windows the process may write only under dataDir (low integrity, see LocalLauncher),
        // so Tomcat's folders have to be there rather than in the system temp folder.
        assertThat(directory.resolve("data/tmp/docbase")).isDirectory();
        assertThat(directory.resolve("data/tmp/tomcat")).isDirectory();

        assertThat(postSession(port, "cd".repeat(32))).isBetween(400, 499);
        assertThat(postSession(port, null)).isBetween(400, 499);

        URI webSocket = URI.create("ws://127.0.0.1:" + port + "/ws?token=");
        WebSocket accepted = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(webSocket + TOKEN), new WebSocket.Listener() { })
                .join();
        accepted.abort();
        assertThatThrownBy(() -> httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(webSocket + "cd".repeat(32)), new WebSocket.Listener() { })
                .join())
                .isInstanceOf(CompletionException.class);

        long closedAt = System.nanoTime();
        process.getOutputStream().close();
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).as("exits within 5 s of end of input").isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - closedAt)).isLessThan(Duration.ofSeconds(5));
        assertThat(process.exitValue()).isZero();
        assertOnlyProtocolLines();
    }

    @Test
    void shutdownCommandExits() throws Exception {
        start(fixture());
        assertThat(nextLine().path("type").asText()).isEqualTo("ready");

        OutputStream stdin = process.getOutputStream();
        stdin.write("{\"type\":\"shutdown\"}\n".getBytes(StandardCharsets.UTF_8));
        stdin.flush();

        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertOnlyProtocolLines();
    }

    @Test
    void startupFailureWritesOneErrorLine() throws Exception {
        Path unsupported = Files.writeString(directory.resolve("static-v2.json"),
                "{\"schemaVersion\":2,\"staticVersion\":\"v\",\"tables\":{}}");
        start(unsupported);

        assertThat(process.waitFor(STARTUP_LIMIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isEqualTo(1);
        drain();
        assertThat(allStdoutLines).hasSize(1);
        JsonNode error = objectMapper.readTree(allStdoutLines.get(0));
        assertThat(error.path("type").asText()).isEqualTo("error");
        assertThat(error.path("reason").asText()).contains("schemaVersion 2");
    }

    @Test
    void endOfInputBeforeInitIsAnError() throws Exception {
        process = new ProcessBuilder(command())
                .redirectError(directory.resolve("stderr.log").toFile())
                .start();
        readStdout();
        process.getOutputStream().close();

        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isEqualTo(1);
        drain();
        assertThat(allStdoutLines).hasSize(1);
        assertThat(objectMapper.readTree(allStdoutLines.get(0)).path("type").asText()).isEqualTo("error");
    }

    private void start(Path staticData) throws IOException {
        process = new ProcessBuilder(command())
                .redirectError(directory.resolve("stderr.log").toFile())
                .start();
        readStdout();
        String init = objectMapper.writeValueAsString(java.util.Map.of(
                "type", "init",
                "token", TOKEN,
                "dataDir", directory.resolve("data").toString(),
                "staticDataPath", staticData.toString()));
        OutputStream stdin = process.getOutputStream();
        stdin.write((init + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    private List<String> command() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return List.of(java, "-Xmx512m", "-jar", System.getProperty("localServer.jar"),
                "--spring.profiles.active=local");
    }

    private void readStdout() {
        Thread reader = new Thread(() -> {
            try (BufferedReader stdout = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stdout.readLine()) != null) {
                    allStdoutLines.add(line);
                    stdoutLines.add(line);
                }
            } catch (IOException ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();
    }

    private JsonNode nextLine() throws Exception {
        String line = stdoutLines.poll(STARTUP_LIMIT.toSeconds(), TimeUnit.SECONDS);
        assertThat(line).as("a stdout line within " + STARTUP_LIMIT).isNotNull();
        return objectMapper.readTree(line);
    }

    // The reader thread may still be copying the last line after the process exited.
    private void drain() throws InterruptedException {
        Thread.sleep(200);
    }

    private void assertOnlyProtocolLines() throws Exception {
        drain();
        for (String line : allStdoutLines) {
            JsonNode node = objectMapper.readTree(line);
            assertThat(node.isObject()).as(line).isTrue();
            assertThat(PROTOCOL_TYPES).as(line).contains(node.path("type").asText());
        }
    }

    private int postSession(int port, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/local/sessions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"sessionId\":\"local-x\",\"type\":\"PRACTICE\"}"));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static Path fixture() throws Exception {
        return Path.of(LocalLauncherProcessTest.class.getResource("/local/static.json").toURI());
    }
}
