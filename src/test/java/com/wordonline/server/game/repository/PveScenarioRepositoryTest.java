package com.wordonline.server.game.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.pve.PveInstallObjectAction;
import com.wordonline.server.game.domain.pve.PveScenarioAction;
import com.wordonline.server.game.domain.pve.PveScenarioEvent;
import com.wordonline.server.game.domain.pve.PveSetSpawnerAction;
import com.wordonline.server.game.domain.pve.PveSpawnWaveAction;
import com.wordonline.server.game.domain.pve.PveTriggerType;
import com.wordonline.server.game.domain.pve.PveWinCondition;
import java.util.List;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

class PveScenarioRepositoryTest {

    private JdbcClient jdbcClient;
    private PveScenarioRepository pveScenarioRepository;

    @BeforeEach
    void setUp() {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:pve_scenario_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcClient = JdbcClient.create(dataSource);
        // Mirrors database migration V012: max_hp on installers, target_installer_id and a
        // nullable message_key on events, plus the new event-actions and rules tables.
        jdbcClient.sql("""
                CREATE TABLE pve_scenario_installers (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    scenario_id BIGINT NOT NULL,
                    installer_id VARCHAR(50) NOT NULL,
                    prefab_type VARCHAR(50) NOT NULL,
                    master VARCHAR(20) NOT NULL,
                    position_x INT NOT NULL,
                    position_y INT NOT NULL,
                    position_z INT NOT NULL,
                    max_hp INT,
                    sort_order INT NOT NULL
                )
                """).update();
        jdbcClient.sql("""
                CREATE TABLE pve_scenario_objectives (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    scenario_id BIGINT NOT NULL,
                    installer_id VARCHAR(50) NOT NULL,
                    sort_order INT NOT NULL
                )
                """).update();
        jdbcClient.sql("""
                CREATE TABLE pve_scenario_events (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    scenario_id BIGINT NOT NULL,
                    event_id VARCHAR(50) NOT NULL,
                    trigger_type VARCHAR(50) NOT NULL,
                    trigger_value INT NOT NULL,
                    target_installer_id VARCHAR(50),
                    speaker_installer_id VARCHAR(50),
                    message_key VARCHAR(100),
                    sort_order INT NOT NULL
                )
                """).update();
        jdbcClient.sql("""
                CREATE TABLE pve_scenario_event_lines (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_row_id BIGINT NOT NULL,
                    line_order INT NOT NULL,
                    line_text VARCHAR(255) NOT NULL
                )
                """).update();
        jdbcClient.sql("""
                CREATE TABLE pve_scenario_event_actions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_row_id BIGINT NOT NULL,
                    action_order INT NOT NULL,
                    action_type VARCHAR(30) NOT NULL,
                    installer_id VARCHAR(50),
                    prefab_type VARCHAR(50),
                    count INT,
                    interval_seconds REAL,
                    position_x INT,
                    position_z INT,
                    max_hp INT
                )
                """).update();
        jdbcClient.sql("""
                CREATE TABLE pve_scenario_rules (
                    scenario_id BIGINT PRIMARY KEY,
                    win_condition VARCHAR(30) NOT NULL DEFAULT 'DestroyObjectives',
                    survive_seconds INT
                )
                """).update();
        pveScenarioRepository = new PveScenarioRepository(jdbcClient);
    }

    private void insertBossInstaller(long scenarioId, String installerId, Integer maxHp) {
        jdbcClient.sql("""
                INSERT INTO pve_scenario_installers
                    (scenario_id, installer_id, prefab_type, master, position_x, position_y, position_z, max_hp, sort_order)
                VALUES (:scenarioId, :installerId, 'FireSlime', 'RightPlayer', 1, 2, 3, :maxHp, 0)
                """)
                .param("scenarioId", scenarioId)
                .param("installerId", installerId)
                .param("maxHp", maxHp)
                .update();
    }

    @Test
    void returnsEmptyForUnknownScenarioId() {
        assertThat(pveScenarioRepository.findById(9999L)).isEmpty();
    }

    @Test
    void returnsScenarioWithInstalledObjects() {
        insertBossInstaller(1L, "boss", null);
        jdbcClient.sql("""
                INSERT INTO pve_scenario_objectives (scenario_id, installer_id, sort_order)
                VALUES (1, 'boss', 0)
                """).update();

        var scenario = pveScenarioRepository.findById(1L);

        assertThat(scenario).isPresent();
        assertThat(scenario.get().installers()).hasSize(1);
        assertThat(scenario.get().installers().get(0).maxHp()).isNull();
        assertThat(scenario.get().objectiveInstallerIds()).containsExactly("boss");
    }

    @Test
    void mapsAnInstallerMaxHpOverride() {
        insertBossInstaller(1L, "boss", 5000);

        var scenario = pveScenarioRepository.findById(1L);

        assertThat(scenario).isPresent();
        assertThat(scenario.get().installers().get(0).maxHp()).isEqualTo(5000);
    }

    @Test
    void mapsAnEventWithNoDialogueAndATargetInstallerId() {
        insertBossInstaller(1L, "boss", null);
        jdbcClient.sql("""
                INSERT INTO pve_scenario_events
                    (scenario_id, event_id, trigger_type, trigger_value, target_installer_id, message_key, sort_order)
                VALUES (1, 'e1', 'InstallerHpPercentLte', 20, 'boss', NULL, 0)
                """).update();

        var scenario = pveScenarioRepository.findById(1L);

        assertThat(scenario).isPresent();
        PveScenarioEvent event = scenario.get().events().get(0);
        assertThat(event.type()).isEqualTo(PveTriggerType.InstallerHpPercentLte);
        assertThat(event.targetInstallerId()).isEqualTo("boss");
        assertThat(event.key()).isNull();
        assertThat(event.lines()).isEmpty();
        assertThat(event.actions()).isEmpty();
    }

    @Test
    void mapsEachEventActionType() {
        insertBossInstaller(1L, "boss", null);
        jdbcClient.sql("""
                INSERT INTO pve_scenario_events
                    (scenario_id, event_id, trigger_type, trigger_value, message_key, sort_order)
                VALUES (1, 'e1', 'FrameNumGte', 0, 'k', 0)
                """)
                .update();
        long eventRowId = jdbcClient.sql("SELECT id FROM pve_scenario_events WHERE event_id = 'e1'")
                .query(Long.class)
                .single();

        jdbcClient.sql("""
                INSERT INTO pve_scenario_event_actions
                    (event_row_id, action_order, action_type, prefab_type, count, position_x, position_z)
                VALUES (:eventRowId, 0, 'SpawnWave', 'FireSlime', 3, 14, 5)
                """).param("eventRowId", eventRowId).update();
        jdbcClient.sql("""
                INSERT INTO pve_scenario_event_actions
                    (event_row_id, action_order, action_type, installer_id, prefab_type, position_x, position_z, max_hp)
                VALUES (:eventRowId, 1, 'InstallObject', 'adds', 'FireSlime', 14, 7, 999)
                """).param("eventRowId", eventRowId).update();
        jdbcClient.sql("""
                INSERT INTO pve_scenario_event_actions
                    (event_row_id, action_order, action_type, installer_id, prefab_type, count, interval_seconds)
                VALUES (:eventRowId, 2, 'SetSpawner', 'boss', 'FireSlime', 5, 1.5)
                """).param("eventRowId", eventRowId).update();

        var scenario = pveScenarioRepository.findById(1L);

        assertThat(scenario).isPresent();
        List<PveScenarioAction> actions = scenario.get().events().get(0).actions();
        assertThat(actions).hasSize(3);

        assertThat(actions.get(0)).isInstanceOfSatisfying(PveSpawnWaveAction.class, action -> {
            assertThat(action.prefabType()).isEqualTo(PrefabType.FireSlime);
            assertThat(action.count()).isEqualTo(3);
            assertThat(action.positionX()).isEqualTo(14);
            assertThat(action.positionZ()).isEqualTo(5);
        });
        assertThat(actions.get(1)).isInstanceOfSatisfying(PveInstallObjectAction.class, action -> {
            assertThat(action.installerId()).isEqualTo("adds");
            assertThat(action.maxHp()).isEqualTo(999);
        });
        assertThat(actions.get(2)).isInstanceOfSatisfying(PveSetSpawnerAction.class, action -> {
            assertThat(action.installerId()).isEqualTo("boss");
            assertThat(action.count()).isEqualTo(5);
            assertThat(action.intervalSeconds()).isEqualTo(1.5f);
        });
    }

    @Test
    void missingRulesRowDefaultsToDestroyObjectives() {
        insertBossInstaller(1L, "boss", null);

        var scenario = pveScenarioRepository.findById(1L);

        assertThat(scenario).isPresent();
        assertThat(scenario.get().rules().winCondition()).isEqualTo(PveWinCondition.DestroyObjectives);
        assertThat(scenario.get().rules().surviveSeconds()).isNull();
    }

    @Test
    void mapsASurviveRulesRow() {
        insertBossInstaller(1L, "boss", null);
        jdbcClient.sql("""
                INSERT INTO pve_scenario_rules (scenario_id, win_condition, survive_seconds)
                VALUES (1, 'Survive', 90)
                """).update();

        var scenario = pveScenarioRepository.findById(1L);

        assertThat(scenario).isPresent();
        assertThat(scenario.get().rules().winCondition()).isEqualTo(PveWinCondition.Survive);
        assertThat(scenario.get().rules().surviveSeconds()).isEqualTo(90);
    }
}
