package com.wordonline.server.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates the in-memory schema of a locally hosted server and loads the static data JSON into it.
 *
 * <p>The JSON format is the one the lobby exports (root plan, local hosting contracts, section 1):
 * {@code {"schemaVersion":1,"staticVersion":"...","tables":{"<table>":{"columns":[...],"rows":[[...]]}}}}.
 * Only tables and columns {@code local/schema-local.sql} declares are loaded; everything else is
 * skipped with a warning, so the lobby can export a column before this server reads it. A
 * {@code schemaVersion} other than {@link #SUPPORTED_SCHEMA_VERSION} is refused outright: that
 * number only moves when the format or the table list changes incompatibly.
 */
@Slf4j
public class LocalStaticDataImporter {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    static final String SCHEMA_RESOURCE = "local/schema-local.sql";

    private static final int BATCH_SIZE = 500;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @return the {@code staticVersion} of the imported file
     * @throws LocalStartupException when the file cannot be read, is not the supported format, or
     *                               a row does not fit the schema
     */
    public String importInto(DataSource dataSource, Path staticDataPath) {
        JsonNode root = read(staticDataPath);
        int schemaVersion = root.path("schemaVersion").asInt(-1);
        if (!root.path("schemaVersion").isIntegralNumber() || schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new LocalStartupException("static data schemaVersion " + root.path("schemaVersion")
                    + " is not supported; this server reads schemaVersion " + SUPPORTED_SCHEMA_VERSION);
        }
        String staticVersion = root.path("staticVersion").asText("");
        if (staticVersion.isBlank()) {
            throw new LocalStartupException("static data has no staticVersion");
        }
        JsonNode tables = root.path("tables");
        if (!tables.isObject()) {
            throw new LocalStartupException("static data has no tables object");
        }

        try (Connection connection = dataSource.getConnection()) {
            try {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(SCHEMA_RESOURCE));
            } catch (ScriptException e) {
                throw new LocalStartupException("cannot create the local schema: " + e.getMessage(), e);
            }
            connection.setAutoCommit(false);
            try {
                Map<String, Map<String, Integer>> schema = readSchema(connection);
                int rowCount = 0;
                for (Iterator<Map.Entry<String, JsonNode>> it = tables.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> table = it.next();
                    rowCount += importTable(connection, schema, table.getKey(), table.getValue());
                }
                restartIdentities(connection);
                connection.commit();
                log.info("[Local] Imported static data; staticVersion: {}, rows: {}", staticVersion, rowCount);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new LocalStartupException("static data does not fit the local schema: " + e.getMessage(), e);
        }
        return staticVersion;
    }

    private JsonNode read(Path staticDataPath) {
        try (InputStream in = Files.newInputStream(staticDataPath)) {
            JsonNode root = objectMapper.readTree(in);
            if (root == null || !root.isObject()) {
                throw new LocalStartupException("static data is not a JSON object: " + staticDataPath);
            }
            return root;
        } catch (IOException e) {
            throw new LocalStartupException("cannot read static data " + staticDataPath + ": " + e.getMessage(), e);
        }
    }

    // table -> (column -> java.sql.Types), names lower-cased as the database stores them.
    private Map<String, Map<String, Integer>> readSchema(Connection connection) throws SQLException {
        Map<String, Map<String, Integer>> schema = new LinkedHashMap<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, connection.getSchema(), "%", "%")) {
            while (columns.next()) {
                String table = columns.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                String column = columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                schema.computeIfAbsent(table, key -> new LinkedHashMap<>()).put(column, columns.getInt("DATA_TYPE"));
            }
        }
        return schema;
    }

    private int importTable(Connection connection, Map<String, Map<String, Integer>> schema,
                            String tableName, JsonNode table) throws SQLException {
        String storedName = tableName.toLowerCase(Locale.ROOT);
        Map<String, Integer> knownColumns = schema.get(storedName);
        if (knownColumns == null) {
            log.warn("[Local] Static data table {} is not in the local schema; skipped", tableName);
            return 0;
        }

        JsonNode columns = table.path("columns");
        JsonNode rows = table.path("rows");
        if (!columns.isArray() || !rows.isArray()) {
            throw new LocalStartupException("static data table " + tableName + " needs columns and rows arrays");
        }

        // Positions in the JSON row of the columns this schema has, in insert order.
        List<Integer> positions = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i).asText().toLowerCase(Locale.ROOT);
            if (knownColumns.containsKey(column) && !names.contains(column)) {
                positions.add(i);
                names.add(column);
            } else {
                ignored.add(columns.get(i).asText());
            }
        }
        if (!ignored.isEmpty()) {
            log.warn("[Local] Static data columns not in the local schema were ignored; table: {}, columns: {}",
                    tableName, ignored);
        }
        if (names.isEmpty() || rows.isEmpty()) {
            return 0;
        }

        String sql = "INSERT INTO " + quote(storedName) + " ("
                + String.join(", ", names.stream().map(LocalStaticDataImporter::quote).toList())
                + ") VALUES (" + String.join(", ", names.stream().map(name -> "?").toList()) + ")";
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() != columns.size()) {
                    throw new LocalStartupException("static data table " + tableName + " row " + count
                            + " does not have " + columns.size() + " values");
                }
                for (int i = 0; i < positions.size(); i++) {
                    bind(statement, i + 1, knownColumns.get(names.get(i)), row.get(positions.get(i)));
                }
                statement.addBatch();
                if (++count % BATCH_SIZE == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
        return count;
    }

    private void bind(PreparedStatement statement, int index, int sqlType, JsonNode value) throws SQLException {
        if (value == null || value.isNull()) {
            statement.setNull(index, sqlType);
            return;
        }
        switch (sqlType) {
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE, Types.DATE ->
                    statement.setObject(index, toLocalDateTime(value.asText()));
            case Types.BOOLEAN, Types.BIT -> statement.setBoolean(index, value.isBoolean()
                    ? value.booleanValue()
                    : value.isNumber() ? value.asInt() != 0 : Boolean.parseBoolean(value.asText()));
            default -> {
                if (value.isIntegralNumber()) {
                    statement.setLong(index, value.longValue());
                } else if (value.isNumber()) {
                    statement.setDouble(index, value.doubleValue());
                } else if (value.isBoolean()) {
                    statement.setBoolean(index, value.booleanValue());
                } else if (value.isContainerNode()) {
                    // jsonb exported as an object instead of a string: keep it as its JSON text.
                    statement.setString(index, value.toString());
                } else {
                    statement.setString(index, value.asText());
                }
            }
        }
    }

    // ISO-8601 with or without an offset; an offset is converted to UTC wall time.
    private static LocalDateTime toLocalDateTime(String text) {
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(text).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new LocalStartupException("static data timestamp is not ISO-8601: " + text);
        }
    }

    // Identity columns would otherwise hand out ids the imported rows already use.
    private void restartIdentities(Connection connection) throws SQLException {
        List<String[]> identities = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = ? AND is_identity = 'YES'
                """)) {
            statement.setString(1, connection.getSchema());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    identities.add(new String[] {resultSet.getString(1), resultSet.getString(2)});
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            for (String[] identity : identities) {
                String table = quote(identity[0]);
                String column = quote(identity[1]);
                long next;
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT COALESCE(MAX(" + column + "), 0) + 1 FROM " + table)) {
                    resultSet.next();
                    next = resultSet.getLong(1);
                }
                statement.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " RESTART WITH " + next);
            }
        }
    }

    // Names come from the database's own metadata, never from the JSON, so quoting cannot be
    // escaped; it is there because columns such as value and count are keywords.
    private static String quote(String identifier) {
        return '"' + identifier + '"';
    }
}
