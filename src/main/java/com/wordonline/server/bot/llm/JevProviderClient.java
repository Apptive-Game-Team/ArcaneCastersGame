package com.wordonline.server.bot.llm;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.wordonline.server.bot.domain.BotBrainType;

/**
 * jev-ai.pro decision API: one call with two {@code choice} questions, {@code magic} and
 * {@code target}. The body carries only {@code model}, {@code state} and {@code questions}; the
 * API rejects unknown fields.
 */
public final class JevProviderClient implements LlmProviderClient {

    static final String MAGIC_QUESTION = "magic";
    static final String TARGET_QUESTION = "target";
    static final String MAGIC_INSTRUCTIONS = "You are a wizard in a real-time lane battle, defending your base on the "
            + "left or right end of the board while destroying the enemy base. Pick the magic to cast now, "
            + "or wait to save mana for a stronger magic. Answer threats near your base first.";
    static final String TARGET_INSTRUCTIONS = "Pick where to cast the chosen magic. Damage spells belong on enemy "
            + "units, ideally where several stand together; summons belong in front of your own base or on the "
            + "lane toward the enemy base.";

    private final URI uri;
    private final String defaultModel;

    public JevProviderClient(String url, String defaultModel) {
        this.uri = URI.create(url);
        this.defaultModel = defaultModel;
    }

    @Override
    public BotBrainType type() {
        return BotBrainType.JEV;
    }

    @Override
    public String ruleId() {
        return "llm.jev";
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
        return Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json");
    }

    @Override
    public String requestBody(LlmCandidates candidates, String model) throws LlmCallFailure {
        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put(MAGIC_QUESTION, choice(MAGIC_INSTRUCTIONS, candidates.magicCriteria()));
        questions.put(TARGET_QUESTION, choice(TARGET_INSTRUCTIONS, candidates.targetCriteria()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("state", candidates.state());
        body.put("questions", questions);
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new LlmCallFailure("jev request could not be written: " + e.getOriginalMessage());
        }
    }

    private static Map<String, Object> choice(String instructions, Map<String, String> criteria) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "choice");
        question.put("instructions", instructions);
        question.put("criteria", criteria);
        return question;
    }

    @Override
    public LlmAnswer parseResponse(String body) throws LlmCallFailure {
        JsonNode answers;
        try {
            answers = JSON.readTree(body).path("answers");
        } catch (Exception e) {
            throw new LlmCallFailure("jev answer is not JSON");
        }
        JsonNode magic = answers.path(MAGIC_QUESTION);
        String magicKey = LlmProviderClient.text(magic.get("choice"));
        if (magicKey == null) {
            throw new LlmCallFailure("jev answer has no magic choice");
        }
        String targetKey = LlmProviderClient.text(answers.path(TARGET_QUESTION).get("choice"));
        JsonNode confidence = magic.get("confidence");
        return new LlmAnswer(magicKey, targetKey, null,
                confidence != null && confidence.isNumber() ? confidence.asDouble() : null);
    }
}
