package com.wordonline.server.game.repository;

import com.wordonline.server.game.domain.map.GameMap;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.pve.PveInstallObject;
import com.wordonline.server.game.domain.pve.PveInstallObjectAction;
import com.wordonline.server.game.domain.pve.PveScenario;
import com.wordonline.server.game.domain.pve.PveScenarioAction;
import com.wordonline.server.game.domain.pve.PveScenarioEvent;
import com.wordonline.server.game.domain.pve.PveScenarioRules;
import com.wordonline.server.game.domain.pve.PveSetSpawnerAction;
import com.wordonline.server.game.domain.pve.PveShield;
import com.wordonline.server.game.domain.pve.PveSpawnWaveAction;
import com.wordonline.server.game.domain.pve.PveTriggerType;
import com.wordonline.server.game.domain.pve.PveWinCondition;
import com.wordonline.server.game.dto.Master;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PveScenarioRepository {

    private static final String FIND_OBJECTIVES = """
            SELECT installer_id
            FROM pve_scenario_objectives
            WHERE scenario_id = :scenarioId
            ORDER BY sort_order
            """;

    private static final String FIND_INSTALLERS = """
            SELECT installer_id, prefab_type, master, position_x, position_y, position_z, max_hp
            FROM pve_scenario_installers
            WHERE scenario_id = :scenarioId
            ORDER BY sort_order
            """;

    private static final String FIND_EVENTS = """
            SELECT id, event_id, trigger_type, trigger_value, target_installer_id,
                   speaker_installer_id, message_key
            FROM pve_scenario_events
            WHERE scenario_id = :scenarioId
            ORDER BY sort_order
            """;

    private static final String FIND_EVENT_LINES = """
            SELECT line_text
            FROM pve_scenario_event_lines
            WHERE event_row_id = :eventRowId
            ORDER BY line_order
            """;

    private static final String FIND_EVENT_ACTIONS = """
            SELECT action_type, installer_id, prefab_type, count, interval_seconds,
                   position_x, position_z, max_hp
            FROM pve_scenario_event_actions
            WHERE event_row_id = :eventRowId
            ORDER BY action_order
            """;

    private static final String FIND_RULES = """
            SELECT win_condition, survive_seconds
            FROM pve_scenario_rules
            WHERE scenario_id = :scenarioId
            """;

    private static final String FIND_SHIELDS = """
            SELECT installer_id, source_installer_id
            FROM pve_scenario_shields
            WHERE scenario_id = :scenarioId
            ORDER BY installer_id, source_installer_id
            """;

    // scenarios -> stages -> adventures. Both links are nullable in the schema, so an inner join
    // returns no row for a scenario that belongs to no adventure.
    private static final String FIND_MAP_TYPE = """
            SELECT a.map_type
            FROM scenarios s
            JOIN stages st ON st.id = s.stage_id
            JOIN adventures a ON a.id = st.adventure_id
            WHERE s.id = :scenarioId
            """;

    private final JdbcClient jdbcClient;

    /** The map of the adventure the scenario belongs to; empty when it belongs to none. */
    public Optional<GameMap> findMapType(Long scenarioId) {
        return jdbcClient.sql(FIND_MAP_TYPE)
                .param("scenarioId", scenarioId)
                .query(String.class)
                .optional()
                .map(GameMap::valueOf);
    }

    public Optional<PveScenario> findById(Long scenarioId) {
        List<PveInstallObject> installers = findInstallers(scenarioId);
        if (installers.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PveScenario(
                        findObjectives(scenarioId),
                        installers,
                        findEvents(scenarioId),
                        findRules(scenarioId),
                        findShields(scenarioId)
                ));
    }

    private List<String> findObjectives(Long scenarioId) {
        return jdbcClient.sql(FIND_OBJECTIVES)
                .param("scenarioId", scenarioId)
                .query(String.class)
                .list();
    }

    private List<PveInstallObject> findInstallers(Long scenarioId) {
        return jdbcClient.sql(FIND_INSTALLERS)
                .param("scenarioId", scenarioId)
                .query((rs, rowNum) -> new PveInstallObject(
                        rs.getString("installer_id"),
                        PrefabType.valueOf(rs.getString("prefab_type")),
                        Master.valueOf(rs.getString("master")),
                        new Vector3(
                                rs.getInt("position_x"),
                                rs.getInt("position_y"),
                                rs.getInt("position_z")
                        ),
                        getNullableInt(rs, "max_hp")
                ))
                .list();
    }

    private List<PveScenarioEvent> findEvents(Long scenarioId) {
        List<EventRow> eventRows = jdbcClient.sql(FIND_EVENTS)
                .param("scenarioId", scenarioId)
                .query((rs, rowNum) -> new EventRow(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getString("trigger_type"),
                        rs.getInt("trigger_value"),
                        rs.getString("target_installer_id"),
                        rs.getString("speaker_installer_id"),
                        rs.getString("message_key")
                ))
                .list();

        return eventRows.stream()
                .map(row -> new PveScenarioEvent(
                        row.eventId(),
                        PveTriggerType.valueOf(row.triggerType()),
                        row.triggerValue(),
                        row.targetInstallerId(),
                        row.speakerInstallerId(),
                        row.messageKey(),
                        findEventLines(row.id()),
                        findEventActions(row.id())
                ))
                .toList();
    }

    private List<String> findEventLines(long eventRowId) {
        return jdbcClient.sql(FIND_EVENT_LINES)
                .param("eventRowId", eventRowId)
                .query(String.class)
                .list();
    }

    private List<PveScenarioAction> findEventActions(long eventRowId) {
        List<ActionRow> actionRows = jdbcClient.sql(FIND_EVENT_ACTIONS)
                .param("eventRowId", eventRowId)
                .query((rs, rowNum) -> new ActionRow(
                        rs.getString("action_type"),
                        rs.getString("installer_id"),
                        rs.getString("prefab_type"),
                        getNullableInt(rs, "count"),
                        getNullableFloat(rs, "interval_seconds"),
                        getNullableInt(rs, "position_x"),
                        getNullableInt(rs, "position_z"),
                        getNullableInt(rs, "max_hp")
                ))
                .list();

        return actionRows.stream()
                .map(this::toAction)
                .toList();
    }

    private PveScenarioAction toAction(ActionRow row) {
        return switch (row.actionType()) {
            case "SpawnWave" -> new PveSpawnWaveAction(
                    PrefabType.valueOf(row.prefabType()),
                    row.count(),
                    row.positionX(),
                    row.positionZ()
            );
            case "InstallObject" -> new PveInstallObjectAction(
                    row.installerId(),
                    PrefabType.valueOf(row.prefabType()),
                    row.positionX(),
                    row.positionZ(),
                    row.maxHp()
            );
            case "SetSpawner" -> new PveSetSpawnerAction(
                    row.installerId(),
                    row.count(),
                    row.prefabType() == null ? null : PrefabType.valueOf(row.prefabType()),
                    row.intervalSeconds()
            );
            default -> throw new IllegalArgumentException("Unknown action_type: " + row.actionType());
        };
    }

    private List<PveShield> findShields(Long scenarioId) {
        return jdbcClient.sql(FIND_SHIELDS)
                .param("scenarioId", scenarioId)
                .query((rs, rowNum) -> new PveShield(
                        rs.getString("installer_id"),
                        rs.getString("source_installer_id")
                ))
                .list();
    }

    private PveScenarioRules findRules(Long scenarioId) {
        return jdbcClient.sql(FIND_RULES)
                .param("scenarioId", scenarioId)
                .query((rs, rowNum) -> new PveScenarioRules(
                        PveWinCondition.valueOf(rs.getString("win_condition")),
                        getNullableInt(rs, "survive_seconds")
                ))
                .optional()
                .orElseGet(PveScenarioRules::defaultRules);
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, Integer.class);
    }

    private static Float getNullableFloat(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, Float.class);
    }

    private record EventRow(
            long id,
            String eventId,
            String triggerType,
            int triggerValue,
            String targetInstallerId,
            String speakerInstallerId,
            String messageKey
    ) {
    }

    private record ActionRow(
            String actionType,
            String installerId,
            String prefabType,
            Integer count,
            Float intervalSeconds,
            Integer positionX,
            Integer positionZ,
            Integer maxHp
    ) {
    }
}
