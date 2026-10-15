package com.wordonline.server.bot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import com.wordonline.server.bot.domain.BotBrainType;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.bot.dto.BotPersonaRequestDto;
import com.wordonline.server.bot.llm.LlmApiKeyRepository;
import com.wordonline.server.bot.service.BotPersonaService;

/**
 * Runs the persona and API key queries against the bot_personas and llm_api_keys definitions in
 * schema-h2.sql. The whole script does not run on H2, so only those two tables are read out of it.
 */
class BotPersonaRepositoryTest {

    private JdbcClient jdbcClient;
    private BotPersonaRepository repository;
    private BotPersonaService service;

    @BeforeEach
    void setUp() throws Exception {
        jdbcClient = JdbcClient.create(new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:bot_persona_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        // The production queries cast to the Postgres enum types; H2 gets same-named domains.
        jdbcClient.sql("CREATE DOMAIN bot_tier AS VARCHAR(20)").update();
        jdbcClient.sql("CREATE DOMAIN bot_temperament AS VARCHAR(20)").update();
        jdbcClient.sql("CREATE TABLE users (id BIGINT PRIMARY KEY)").update();
        jdbcClient.sql(tableDdl("bot_personas")).update();
        jdbcClient.sql(tableDdl("llm_api_keys")).update();
        jdbcClient.sql("INSERT INTO users(id) VALUES (-7)").update();
        repository = new BotPersonaRepository(jdbcClient);
        service = new BotPersonaService(repository);
    }

    @Test
    void roundTripsBrainTypeAndModel() {
        BotPersona created = service.create(request(BotBrainType.OPENROUTER, "  anthropic/claude-sonnet  "));

        assertThat(created.brainType()).isEqualTo(BotBrainType.OPENROUTER);
        assertThat(created.llmModel()).isEqualTo("anthropic/claude-sonnet");

        BotPersona updated = service.update(-7, request(BotBrainType.JEV, "   "));
        assertThat(updated.brainType()).isEqualTo(BotBrainType.JEV);
        assertThat(updated.llmModel()).isNull();
        assertThat(repository.findAll()).containsExactly(updated);
    }

    @Test
    void aRequestWithoutABrainKeepsTheRuleBrain() {
        BotPersona created = service.create(new BotPersonaRequestDto(-7, "Bot", BotTier.BEGINNER, 250, 8, 0.25,
                true, false, BotTemperament.WARM));

        assertThat(created.brainType()).isEqualTo(BotBrainType.RULE);
        assertThat(created.llmModel()).isNull();
    }

    @Test
    void anExistingRowReadsTheColumnDefaultAsRule() {
        jdbcClient.sql("INSERT INTO bot_personas(user_id, name) VALUES (-7, 'Old')").update();

        assertThat(repository.findByUserId(-7)).get()
                .extracting(BotPersona::brainType)
                .isEqualTo(BotBrainType.RULE);
    }

    @Test
    void readsTheEncryptedKeyForOneProvider() {
        jdbcClient.sql("INSERT INTO llm_api_keys(provider, encrypted_key, key_hint) VALUES ('JEV', 'v1:abc', '…1234')")
                .update();
        LlmApiKeyRepository keys = new LlmApiKeyRepository(jdbcClient);

        assertThat(keys.findEncryptedKey("JEV")).contains("v1:abc");
        assertThat(keys.findEncryptedKey("OPENROUTER")).isEmpty();
    }

    private static BotPersonaRequestDto request(BotBrainType brainType, String llmModel) {
        return new BotPersonaRequestDto(-7, "Bot", BotTier.BEGINNER, 250, 8, 0.25, true, false,
                BotTemperament.WARM, brainType, llmModel);
    }

    private static String tableDdl(String table) throws Exception {
        String schema = new ClassPathResource("schema-h2.sql").getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("CREATE TABLE " + table + " \\(.*?\\n\\);", Pattern.DOTALL).matcher(schema);
        if (!matcher.find()) {
            throw new IllegalStateException(table + " is missing from schema-h2.sql");
        }
        // Drop the SQL comments; H2 accepts them, but the regex above already keeps the DDL whole.
        return matcher.group().replaceAll("--[^\\n]*", "");
    }
}
