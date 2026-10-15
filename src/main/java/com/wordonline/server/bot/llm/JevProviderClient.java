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
            + "none, 1 several enemies at the door), every unit with its distance to the base it walks toward and "
            + "the seconds until it starts hitting that base, and a unitGuide that says what each unit type does "
            + "(hp, damage per hit, attack interval, reach, speed, flying). "
            + "Each option is one complete play: a magic and where it lands, with its damage per mana. Damage that "
            + "lands on the enemy base counts directly toward winning; damage on units only matters when those "
            + "units would otherwise reach our base. Pick the play that wins the race: hit the enemy base whenever "
            + "no enemy unit is close to our base, defend with the most damage per mana when one is, and summon "
            + "units that will push to the enemy base. Never pick a play that hits nothing. The STANDARD TACTIC "
            + "play is what a cautious rule bot would do; it often spends mana on units instead of the base. "
            + "Waiting saves mana; never wait while enemy units are close to our base.";

    static final String STANCE_QUESTION = "stance";
    static final String STANCE_INSTRUCTIONS = "You command a wizard in a real-time lane battle. Each side has a "
            + "base at one end of the lane with 1000 hp; the first base to reach zero loses, so the game is a race "
            + "between the two base hp values. The state lists our hp, the enemy hp, our mana, the pressure on our "
            + "base (0 none, 1 several enemies at the door), every unit with its distance to the base it walks "
            + "toward and the seconds until it starts hitting that base, and a unitGuide that says what each unit "
            + "type does. Code aims every cast; you only choose the plan for the next second, and you are asked "
            + "again every second. Each option says what the plan would do right now. Attack when the enemy base "
            + "is in reach and no enemy unit will hit our base soon, defend when enemy units will reach our base "
            + "before our attack wins the race, and save when a stronger card is close to affordable and nothing "
            + "is coming.";

    static final String CARD_INSTRUCTIONS = "You are a wizard in a real-time lane battle. Each side has a base at "
            + "one end of the lane with 1000 hp; the first base to reach zero loses. Mana refills over time up to "
            + "120. The state lists both base hp values, our mana, every unit on the field with its distance to "
            + "the base it walks toward and the seconds until it starts hitting that base, and a unitGuide. "
            + "Each option is one card in our hand: what the card does, what the unit it leaves behind does, and "
            + "what it would do on this board right now. Code aims the card for you; you decide which card to "
            + "play, or to keep the mana. The hand holds at most 6 cards (handSlotsUsed, and copies per card); a card "
            + "stays in the hand until it is played, and every second a free slot draws the next card of the deck, "
            + "so a card we never play keeps blocking a slot, and playing a weak card frees its slot for a better "
            + "one. Think about what each card is for. A unit that never moves only helps "
            + "where enemies will come; a unit that walks or flies to the enemy base can hit it; a spell is wasted "
            + "on a target that does not matter. Pick the card that does the most for winning on this board now.";

    private final URI uri;
    private final String defaultModel;
    private final String playInstructions;

    public JevProviderClient(String url, String defaultModel) {
        this(url, defaultModel, PLAY_INSTRUCTIONS);
    }

    /** @param playInstructions what the {@value #PLAY_QUESTION} question tells the model about its options */
    public JevProviderClient(String url, String defaultModel, String playInstructions) {
        this.uri = URI.create(url);
        this.defaultModel = defaultModel;
        this.playInstructions = playInstructions;
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
        return choiceBody(PLAY_QUESTION, playInstructions, candidates.criteria(), candidates.state(), model);
    }

    /** One {@value #STANCE_QUESTION} question whose options are the plans in {@code criteria}. */
    public String stanceBody(Map<String, String> criteria, Map<String, Object> state, String model)
            throws LlmCallFailure {
        return choiceBody(STANCE_QUESTION, STANCE_INSTRUCTIONS, criteria, state, model);
    }

    private static String choiceBody(String questionId, String instructions, Map<String, String> criteria,
                                     Map<String, Object> state, String model) throws LlmCallFailure {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "choice");
        question.put("instructions", instructions);
        question.put("criteria", criteria);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("state", state);
        body.put("questions", Map.of(questionId, question));
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new LlmCallFailure("jev request could not be written: " + e.getOriginalMessage());
        }
    }

    @Override
    public LlmAnswer parseResponse(String body) throws LlmCallFailure {
        return parseChoice(PLAY_QUESTION, body);
    }

    /** The key jev chose for the {@value #STANCE_QUESTION} question. */
    public LlmAnswer parseStance(String body) throws LlmCallFailure {
        return parseChoice(STANCE_QUESTION, body);
    }

    private static LlmAnswer parseChoice(String questionId, String body) throws LlmCallFailure {
        JsonNode answers;
        try {
            answers = JSON.readTree(body).path("answers");
        } catch (Exception e) {
            throw new LlmCallFailure("jev answer is not JSON");
        }
        JsonNode play = answers.path(questionId);
        String playKey = LlmProviderClient.text(play.get("choice"));
        if (playKey == null) {
            throw new LlmCallFailure("jev answer has no " + questionId + " choice");
        }
        JsonNode confidence = play.get("confidence");
        return new LlmAnswer(playKey, null, confidence != null && confidence.isNumber() ? confidence.asDouble() : null);
    }
}
