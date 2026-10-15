package com.wordonline.server.bot.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

/**
 * The card text a player reads for each magic, keyed by magic name: what the magic does, in words.
 *
 * <p>The database carries no description, so {@code bot/magic-descriptions.properties} holds a copy
 * of the client's English magic book (MagicBook_en). A magic with no line reads as null.
 */
@Slf4j
final class MagicDescriptions {

    static final String RESOURCE = "bot/magic-descriptions.properties";

    private static final MagicDescriptions BUNDLED = load();

    private final Map<String, String> byName;

    MagicDescriptions(Map<String, String> byName) {
        this.byName = Map.copyOf(byName);
    }

    static MagicDescriptions bundled() {
        return BUNDLED;
    }

    String describe(String magicName) {
        return magicName == null ? null : byName.get(magicName);
    }

    private static MagicDescriptions load() {
        Properties properties = new Properties();
        try (InputStream in = MagicDescriptions.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("[LlmBot] {} is missing; magics are offered without their card text", RESOURCE);
                return new MagicDescriptions(Map.of());
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            log.warn("[LlmBot] {} could not be read; magics are offered without their card text", RESOURCE, e);
            return new MagicDescriptions(Map.of());
        }
        Map<String, String> byName = new java.util.HashMap<>();
        properties.forEach((key, value) -> byName.put(key.toString(), value.toString()));
        return new MagicDescriptions(byName);
    }
}
