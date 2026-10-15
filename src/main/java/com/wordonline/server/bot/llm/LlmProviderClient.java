package com.wordonline.server.bot.llm;

import java.net.URI;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.bot.domain.BotBrainType;

/** How one provider's request is written and its answer read. Pure: no network, no state. */
public interface LlmProviderClient {

    ObjectMapper JSON = new ObjectMapper();

    BotBrainType type();

    /** The ruleId the bot-thought DTO shows for this provider's casts. */
    String ruleId();

    URI uri();

    String defaultModel();

    Map<String, String> headers(String apiKey);

    String requestBody(LlmCandidates candidates, String model) throws LlmCallFailure;

    LlmAnswer parseResponse(String body) throws LlmCallFailure;

    /** The provider's error message from a non-2xx body, if it has the usual shape; never the request. */
    static String errorMessage(String body) {
        try {
            JsonNode message = JSON.readTree(body).path("error").path("message");
            if (message.isTextual()) {
                String text = message.asText();
                return text.length() <= 200 ? text : text.substring(0, 200);
            }
        } catch (Exception ignored) {
            // Not JSON: the status alone has to do.
        }
        return null;
    }

    static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText().trim() : null;
    }
}
