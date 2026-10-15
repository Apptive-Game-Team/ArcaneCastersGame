package com.wordonline.server.bot.llm;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.wordonline.server.bot.domain.BotBrainType;

/**
 * OpenRouter chat completions: a system prompt that explains the game and the answer format, and a
 * user message with the state and both option lists. The answer is read from
 * {@code choices[0].message.content}, code fences tolerated.
 */
public final class OpenRouterProviderClient implements LlmProviderClient {

    static final String SYSTEM_PROMPT = """
            You control a wizard in ArcaneCasters, a real-time lane battle on an 18 by 10 board. Each side \
            has a base at one end; the match is won by bringing the enemy base hp to zero. Mana refills over \
            time and every magic costs mana. Summons put units on the board that walk toward the enemy base; \
            damage spells hit enemy units around the target point.
            Every turn you get the board state, the magic options you can afford right now and the target \
            options. Pick exactly one magic option key (or "wait" to save mana) and exactly one target option key.
            Reply with a single JSON object and nothing else:
            {"magic": "<magic option key>", "target": "<target option key>", "reason": "<at most 15 words>"}""";

    private final URI uri;
    private final String defaultModel;

    public OpenRouterProviderClient(String url, String defaultModel) {
        this.uri = URI.create(url);
        this.defaultModel = defaultModel;
    }

    @Override
    public BotBrainType type() {
        return BotBrainType.OPENROUTER;
    }

    @Override
    public String ruleId() {
        return "llm.openrouter";
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public String defaultModel() {
        return defaultModel;
    }

    @Override
    public Map<String, String> headers(String apiKey) {
        return Map.of(
                "Authorization", "Bearer " + apiKey,
                "Content-Type", "application/json",
                "X-Title", "ArcaneCasters");
    }

    @Override
    public String requestBody(LlmCandidates candidates, String model) throws LlmCallFailure {
        try {
            String user = "State:\n" + JSON.writeValueAsString(candidates.state())
                    + "\n\nMagic options:\n" + JSON.writeValueAsString(candidates.magicCriteria())
                    + "\n\nTarget options:\n" + JSON.writeValueAsString(candidates.targetCriteria());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    message("system", SYSTEM_PROMPT),
                    message("user", user)));
            body.put("response_format", Map.of("type", "json_object"));
            body.put("temperature", 0.3);
            body.put("max_tokens", 200);
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new LlmCallFailure("OpenRouter request could not be written: " + e.getOriginalMessage());
        }
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    @Override
    public LlmAnswer parseResponse(String body) throws LlmCallFailure {
        String content;
        try {
            content = LlmProviderClient.text(JSON.readTree(body).path("choices").path(0).path("message").get("content"));
        } catch (Exception e) {
            throw new LlmCallFailure("OpenRouter answer is not JSON");
        }
        if (content == null || content.isEmpty()) {
            throw new LlmCallFailure("OpenRouter answer has no message content");
        }
        JsonNode answer = parseContent(content);
        String magicKey = LlmProviderClient.text(answer.get("magic"));
        if (magicKey == null) {
            throw new LlmCallFailure("OpenRouter answer has no magic key");
        }
        return new LlmAnswer(magicKey, LlmProviderClient.text(answer.get("target")),
                LlmProviderClient.text(answer.get("reason")), null);
    }

    /** The JSON object in the content, with any code fence or text around it ignored. */
    static JsonNode parseContent(String content) throws LlmCallFailure {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmCallFailure("OpenRouter content holds no JSON object");
        }
        try {
            JsonNode node = JSON.readTree(content.substring(start, end + 1));
            if (!node.isObject()) {
                throw new LlmCallFailure("OpenRouter content is not a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new LlmCallFailure("OpenRouter content is not valid JSON");
        }
    }
}
