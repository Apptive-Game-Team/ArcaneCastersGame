package com.wordonline.server.game.repository;

import java.util.Optional;
import java.util.OptionalDouble;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ParameterRepository {

    private final JdbcClient jdbcClient;

    /** One query gives an internally consistent input set for an offline replay generation. */
    public java.util.Map<String, Double> snapshot() {
        var values = new java.util.TreeMap<String, Double>();
        jdbcClient.sql("""
                SELECT game_objects.name AS object_name, parameters.name AS parameter_name, value
                FROM parameter_values
                JOIN game_objects ON parameter_values.game_object_id = game_objects.id
                JOIN parameters ON parameter_values.parameter_id = parameters.id
                """).query((row, index) -> java.util.Map.entry(
                        row.getString("object_name").toLowerCase() + "." + row.getString("parameter_name"),
                        row.getDouble("value")))
                .list().forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        return java.util.Map.copyOf(values);
    }

    private static final String GET_PARAMETER_VALUE = """
            SELECT value
            FROM parameter_values
            JOIN game_objects ON parameter_values.game_object_id = game_objects.id
            JOIN parameters ON parameter_values.parameter_id = parameters.id
            WHERE game_objects.name = :gameObject AND parameters.name = :parameter;
            """;

    public Optional<Double> getParameterValue(String gameObject, String parameter) {
        // Debug, not info: this runs on the game loop thread for every parameter of every
        // spawned object. The lower-casing stays on the bind value below so a disabled log level
        // costs nothing.
        log.debug("[Database] get parameter gameobject: {} | parameter: {}", gameObject, parameter);
        return jdbcClient.sql(GET_PARAMETER_VALUE)
                .param("gameObject", gameObject.toLowerCase())
                .param("parameter", parameter)
                .query(Double.class)
                .optional();
    }
}
