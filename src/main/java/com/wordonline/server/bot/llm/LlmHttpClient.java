package com.wordonline.server.bot.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * One blocking JSON POST. Only ever called on the LLM request pool; tests replace it with a fake so
 * no test touches the network.
 */
public interface LlmHttpClient {

    Response post(URI uri, Map<String, String> headers, String body) throws IOException, InterruptedException;

    record Response(int status, String body, String retryAfter) {
    }

    /** {@link HttpClient} with the configured connect and request timeouts. */
    static LlmHttpClient jdk(Duration timeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return (uri, headers, body) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(request::header);
            HttpResponse<String> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body(),
                    response.headers().firstValue("Retry-After").orElse(null));
        };
    }
}
