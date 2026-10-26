package com.wordonline.server.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LocalStaticDataImporterTest {

    @TempDir
    Path directory;

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:importer-" + UUID.randomUUID()
                    + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE", "sa", "");
    private final JdbcClient jdbcClient = JdbcClient.create(dataSource);
    private final LocalStaticDataImporter importer = new LocalStaticDataImporter();

    @Test
    void importsTheRowsAndReturnsTheStaticVersion() throws IOException {
        Path file = write("""
                {"schemaVersion": 1, "staticVersion": "abc123", "generatedAt": "2026-10-12T03:00:00Z",
                 "tables": {
                   "magics": {"columns": ["id", "name", "element", "cast_kind", "indicator", "updated_at"],
                              "rows": [[7, "fire_shot", "Fire", "Shot", "{\\"version\\":1}", "2026-10-01T12:00:00Z"]]},
                   "bot_personas": {"columns": ["user_id", "name", "enabled", "hospitality", "counter_aggression"],
                                    "rows": [[-17, "bot", true, false, 0.25]]},
                   "parameter_values": {"columns": ["id", "parameter_id", "game_object_id", "value"],
                                        "rows": [[1, 2, 3, 0.5], [2, 2, 4, null]]},
                   "decks": {"columns": ["id", "name", "user_id"], "rows": [[41, "bot", -17]]}
                 }}
                """);

        assertThat(importer.importInto(dataSource, file)).isEqualTo("abc123");

        assertThat(jdbcClient.sql("SELECT name FROM magics WHERE id = 7").query(String.class).single())
                .isEqualTo("fire_shot");
        assertThat(jdbcClient.sql("SELECT indicator FROM magics WHERE id = 7").query(String.class).single())
                .isEqualTo("{\"version\":1}");
        assertThat(jdbcClient.sql("SELECT value FROM parameter_values WHERE id = 1").query(Double.class).single())
                .isEqualTo(0.5);
        assertThat(jdbcClient.sql("SELECT enabled FROM bot_personas WHERE user_id = -17").query(Boolean.class).single())
                .isTrue();
        // Identities continue after the imported ids, so a deck the player gets later cannot collide.
        jdbcClient.sql("INSERT INTO decks (name, user_id) VALUES ('local', 5)").update();
        assertThat(jdbcClient.sql("SELECT id FROM decks WHERE user_id = 5").query(Long.class).single()).isEqualTo(42L);
    }

    @Test
    void ignoresUnknownColumnsAndTablesWithAWarning() throws IOException {
        // Attached to the logger itself: a local profile context earlier in the same JVM points the
        // root logger at a file, so console capture would depend on test order.
        Logger logger = (Logger) LoggerFactory.getLogger(LocalStaticDataImporter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Path file = write("""
                {"schemaVersion": 1, "staticVersion": "v",
                 "tables": {
                   "tags": {"columns": ["id", "name", "colour"], "rows": [[1, "Air", "blue"]]},
                   "quests": {"columns": ["id"], "rows": [[1]]}
                 }}
                """);

        importer.importInto(dataSource, file);

        assertThat(jdbcClient.sql("SELECT name FROM tags WHERE id = 1").query(String.class).single()).isEqualTo("Air");
        logger.detachAppender(appender);
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("table: tags, columns: [colour]"))
                .anySatisfy(message -> assertThat(message).contains("Static data table quests is not in the local schema"));
    }

    @Test
    void refusesAnotherSchemaVersion() throws IOException {
        Path file = write("""
                {"schemaVersion": 2, "staticVersion": "v", "tables": {}}
                """);

        assertThatThrownBy(() -> importer.importInto(dataSource, file))
                .isInstanceOf(LocalStartupException.class)
                .hasMessageContaining("schemaVersion 2 is not supported");
    }

    @Test
    void refusesRowsThatDoNotMatchTheirColumns() throws IOException {
        Path file = write("""
                {"schemaVersion": 1, "staticVersion": "v",
                 "tables": {"tags": {"columns": ["id", "name"], "rows": [[1]]}}}
                """);

        assertThatThrownBy(() -> importer.importInto(dataSource, file))
                .isInstanceOf(LocalStartupException.class)
                .hasMessageContaining("tags row 0");
    }

    @Test
    void importsTheTestFixture() throws Exception {
        Path fixture = Path.of(getClass().getResource("/local/static.json").toURI());

        assertThat(importer.importInto(dataSource, fixture)).hasSize(64);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM users WHERE id < 0").query(Long.class).single()).isEqualTo(3);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM parameter_values").query(Long.class).single()).isPositive();
    }

    private Path write(String json) throws IOException {
        Path file = directory.resolve("static.json");
        Files.writeString(file, json);
        return file;
    }
}
