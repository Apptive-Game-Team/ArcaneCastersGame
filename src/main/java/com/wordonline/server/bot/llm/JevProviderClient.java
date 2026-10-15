package com.wordonline.server.bot.llm;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.wordonline.server.bot.domain.BotBrainType;

/**
 * TypeSafe Jev decision API (api.typesafe.ai; jev-ai.pro relays the same schema with its own
 * keys): one call with one {@code choice} question, {@code play}, whose options are complete plays
 * (magic and target together). Jev answers separate questions in parallel, so a magic and its
 * target asked apart would be picked without knowing each other. The body carries only
 * {@code model}, {@code state} and {@code questions}; the API rejects unknown fields.
 */
public final class JevProviderClient implements LlmProviderClient {

    static final String PLAY_QUESTION = "play";
    static final String PLAY_INSTRUCTIONS = "You are a wizard in a real-time lane battle. Each side has a base at "
            + "one end of the lane with 1000 hp; the first base to reach zero loses, so the game is a race between "
            + "the two base hp values. The state lists our hp, the enemy hp, our mana, the pressure on our base (0 "
            + "none, 1 several enemies at the door), and every unit with its distance to the base it walks toward. "
            + "Each option is one complete play: a magic and where it lands, with its damage per mana. Damage that "
            + "lands on the enemy base counts directly toward winning; damage on units only matters when those "
            + "units would otherwise reach our base. Pick the play that wins the race: hit the enemy base whenever "
            + "no enemy unit is close to our base, defend with the most damage per mana when one is, and summon "
            + "units that will push to the enemy base. Never pick a play that hits nothing. The STANDARD TACTIC "
            + "play is what a cautious rule bot would do; it often spends mana on units instead of the base. "
            + "Waiting saves mana; never wait while enemy units are close to our base.";

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
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "choice");
        question.put("instructions", PLAY_INSTRUCTIONS);
        question.put("criteria", candidates.criteria());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("state", candidates.state());
        body.put("questions", Map.of(PLAY_QUESTION, question));
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new LlmCallFailure("jev request could not be written: " + e.getOriginalMessage());
        }
    }

    @Override
    public LlmAnswer parseResponse(String body) throws LlmCallFailure {
        JsonNode answers;
        try {
            answers = JSON.readTree(body).path("answers");
        } catch (Exception e) {
            throw new LlmCallFailure("jev answer is not JSON");
        }
        JsonNode play = answers.path(PLAY_QUESTION);
        String playKey = LlmProviderClient.text(play.get("choice"));
        if (playKey == null) {
            throw new LlmCallFailure("jev answer has no play choice");
        }
        JsonNode confidence = play.get("confidence");
        return new LlmAnswer(playKey, null, confidence != null && confidence.isNumber() ? confidence.asDouble() : null);
    }
}
