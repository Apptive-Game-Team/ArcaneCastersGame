package com.wordonline.server.game.repository;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * An in-memory H2 holding only the adventure, stage and scenario tables, built from the shipped
 * {@code schema-h2.sql} and {@code data-h2.sql}. The whole scripts cannot run on H2 (they still
 * contain Postgres-only statements), so only the statements that name these three tables are
 * applied; that way the fixtures under test are the ones the rest of the suite shares.
 */
public final class MapFixtureDatabase {

    private static final Pattern MAP_TABLE_STATEMENT = Pattern.compile(
            "^\\s*(CREATE TABLE|INSERT INTO)\\s+(adventures|stages|scenarios)\\b", Pattern.CASE_INSENSITIVE);

    private MapFixtureDatabase() {
    }

    public static JdbcClient create() {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(
                new org.h2.Driver(), "jdbc:h2:mem:map_fixture_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        List<String> ddl = statements("schema-h2.sql");
        List<String> data = statements("data-h2.sql");
        // Fail loudly rather than silently testing an empty fixture.
        if (ddl.size() != 3 || data.size() != 4) {
            throw new IllegalStateException("expected 3 table and 4 insert statements, got "
                    + ddl.size() + " and " + data.size());
        }
        ddl.forEach(statement -> jdbcClient.sql(statement).update());
        data.forEach(statement -> jdbcClient.sql(statement).update());
        return jdbcClient;
    }

    private static List<String> statements(String resource) {
        try {
            String script = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
            // Comment lines may contain semicolons, so they go before the split.
            String withoutComments = script.lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            return Arrays.stream(withoutComments.split(";"))
                    .map(String::strip)
                    .filter(statement -> MAP_TABLE_STATEMENT.matcher(statement).find())
                    .toList();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
