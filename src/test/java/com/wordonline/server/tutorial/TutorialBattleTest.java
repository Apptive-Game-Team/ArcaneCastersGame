package com.wordonline.server.tutorial;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.service.ParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.server.service.ServerUrlProvider;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TutorialBattleTest {
    private Parameters parameters;
    private Map<String, PrefabInitializer> prefabs;
    private Map<String, Magic> magics;
    private final AtomicLong clock = new AtomicLong();
    private TutorialBattle battle;

    @BeforeEach void setup() throws Exception {
        var root = new ObjectMapper().readTree(getClass().getResourceAsStream("/tutorial-parameters.json"));
        Map<String, Double> values = new ObjectMapper().convertValue(root.path("parameters"), new TypeReference<>() {});
        ParameterService service = mock(ParameterService.class);
        when(service.getValue(anyString(), anyString())).thenAnswer(call -> {
            String key = call.getArgument(0) + "." + call.getArgument(1);
            if (!values.containsKey(key)) throw new IllegalArgumentException("Missing fixture: " + key);
            return values.get(key);
        });
        when(service.getValueOrDefault(anyString(), anyString(), anyDouble())).thenAnswer(call ->
                values.getOrDefault(call.getArgument(0) + "." + call.getArgument(1), call.getArgument(2)));
        parameters = new Parameters(service);
        prefabs = beans(PrefabInitializer.class, "com.wordonline.server.game.domain.object.prefab.implement");
        magics = beans(Magic.class, "com.wordonline.server.game.domain.magic.implement");
        long id = 1;
        for (var entry : magics.entrySet()) { entry.getValue().name = entry.getKey(); entry.getValue().id = id++; }
    }

    private <T> Map<String, T> beans(Class<T> parent, String path) throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(parent));
        Map<String, T> result = new TreeMap<>();
        for (var candidate : scanner.findCandidateComponents(path)) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            var constructor = type.getConstructors()[0];
            Object[] args = Arrays.stream(constructor.getParameterTypes()).map(dependency -> {
                if (dependency == Parameters.class) return parameters;
                if (dependency == com.wordonline.server.game.domain.magic.implement.shoot.VineTossMagic.class)
                    return new com.wordonline.server.game.domain.magic.implement.shoot.VineTossMagic();
                throw new IllegalArgumentException(dependency.getSimpleName());
            }).toArray();
            result.put(type.getAnnotation(Component.class).value(), parent.cast(constructor.newInstance(args)));
        }
        return result;
    }

    private TutorialBattle create(String faction) {
        return new TutorialBattle("tutorial-test", 7, faction, Instant.now().plusSeconds(1200), parameters,
                prefabs, magics, null, new TutorialProperties(8, 256, null), clock::get);
    }

    private TutorialBattle.State tick() {
        clock.addAndGet(50_000_000L);
        return battle.state(Integer.MAX_VALUE);
    }

    private void cast(String name, float x, float z) {
        var state = battle.state(Integer.MAX_VALUE);
        battle.command(new TutorialBattle.Command(UUID.randomUUID().toString(), state.revision(), "CAST", name, new Vector3(x, 0, z)));
    }

    @ParameterizedTest @ValueSource(strings = {"HELL", "NATURE"})
    void bothFactionsCanClearAllStepsUsingProductionCombat(String faction) {
        battle = create(faction);
        try (var running = battle) {
            for (int step = 0; step < 7; step++) {
                var initial = battle.state(0);
                assertThat(initial.stage()).isEqualTo(TutorialBattle.STAGES.get(step));
                int lastWave = -1;
                for (int elapsed = 0; elapsed < 2200; elapsed++) {
                    var state = battle.state(Integer.MAX_VALUE);
                    if (!state.status().equals("RUNNING")) break;
                    String name = state.recommended();
                    if (step == 4) {
                        if (elapsed == 0) cast(name, 6, 5);
                        else if (name.endsWith("swarm") && elapsed % 60 == 0) cast(name, 5, 5);
                    } else if (step == 6) {
                        if (state.wave() != lastWave) {
                            lastWave = state.wave();
                            cast((faction.equals("HELL") ? TutorialBattle.HELL : TutorialBattle.NATURE).get(state.wave() == 1 ? 2 : state.wave() == 2 ? 4 : 3),
                                    state.wave() == 2 ? 8 : 6, 5);
                        }
                        if (state.wave() == 0 && elapsed % 80 == 0)
                            cast((faction.equals("HELL") ? TutorialBattle.HELL : TutorialBattle.NATURE).get(1), 5, 5);
                        if (state.wave() == 2 && elapsed % 80 == 0)
                            cast((faction.equals("HELL") ? TutorialBattle.HELL : TutorialBattle.NATURE).get(4), 8, 5);
                    } else if (elapsed % (step == 1 ? 10 : 80) == 0 && state.mana() >= parameters.getValue(name, "mana_cost")) {
                        float x = step == 1 ? 5.8f : step == 2 ? 7 : step == 3 ? 6 : 8;
                        float z = 5;
                        if (step == 1 && !name.equals("meteor_shower")) {
                            var enemy = state.snapshot().stream().filter(object -> object.master().equals("RightPlayer")
                                    && !object.prefab().equals("Player") && object.gauges().stream().anyMatch(gauge -> gauge.maxValue() > 1)).findFirst().orElse(null);
                            if (enemy != null) { x = enemy.x(); z = enemy.z(); }
                        }
                        cast(name, x, z);
                    }
                    tick();
                }
                var end = battle.state(0);
                assertThat(end.status()).as("%s %s snapshot=%s", faction, initial.stage(), end.snapshot())
                        .isEqualTo(step == 6 ? "COMPLETED" : "CLEARED");
                System.out.printf("Tutorial %s %s cleared frame=%d%n", faction, initial.stage(), end.lastFrame());
                if (step < 6) battle.command(new TutorialBattle.Command(UUID.randomUUID().toString(), end.revision(), "NEXT", null, null));
            }
        }
    }

    @Test void emptyShotsDoNotCompleteAndIllegalSpellsOrCoordinatesAreRejected() {
        battle = create("HELL");
        try (var running = battle) {
            cast("fire_shot", 8, 1);
            for (int i = 0; i < 30; i++) tick();
            assertThat(battle.state(0).status()).isEqualTo("RUNNING");
            assertThatThrownBy(() -> cast("meteor_shower", 8, 5)).hasMessageContaining("400");
            assertThatThrownBy(() -> cast("fire_shot", Float.NaN, 5)).hasMessageContaining("400");
            assertThatThrownBy(() -> cast("fire_shot", 19, 5)).hasMessageContaining("400");
        }
    }

    @Test void duplicateCommandDoesNotSpendTwiceAndCurrentStepCannotBeSkipped() {
        battle = create("HELL");
        try (var running = battle) {
            String id = UUID.randomUUID().toString();
            battle.command(new TutorialBattle.Command(id, 0, "CAST", "fire_shot", new Vector3(8, 0, 1)));
            int mana = battle.state(0).mana();
            battle.command(new TutorialBattle.Command(id, 0, "CAST", "fire_shot", new Vector3(8, 0, 1)));
            assertThat(battle.state(0).mana()).isEqualTo(mana);
            assertThatThrownBy(() -> battle.command(new TutorialBattle.Command("next", 0, "NEXT", null, null))).hasMessageContaining("409");
            assertThatThrownBy(() -> battle.command(new TutorialBattle.Command(id, 0, "CAST", "fire_shot", new Vector3(8, 0, 2)))).hasMessageContaining("409");
        }
    }

    @Test void pollingCannotAdvanceTimeAndManaUsesProductionFourPerSecond() {
        battle = create("HELL");
        try (var running = battle) {
            cast("fire_shot", 8, 1);
            var initial = battle.state(0);
            for (int i = 0; i < 100; i++) battle.state(0);
            assertThat(battle.state(0).lastFrame()).isEqualTo(initial.lastFrame());
            for (int i = 0; i < 20; i++) tick();
            assertThat(battle.state(0).mana()).isEqualTo(initial.mana() + 4);
            clock.addAndGet(60_000_000_000L);
            assertThat(battle.state(0).lastFrame() - initial.lastFrame()).isLessThanOrEqualTo(30);
        }
    }

    @Test void retryResetsOnlyCurrentStepAndDoesNotReusePreviousCastHistory() {
        battle = create("HELL");
        try (var running = battle) {
            cast("fire_shot", 8, 1);
            for (int i = 0; i < 2401 && battle.state(0).status().equals("RUNNING"); i++) tick();
            assertThat(battle.state(0).status()).isEqualTo("FAILED");
            battle.command(new TutorialBattle.Command("retry", 0, "RETRY", null, null));
            var fresh = battle.state(0);
            assertThat(fresh.stage()).isEqualTo("shot");
            assertThat(fresh.revision()).isEqualTo(1);
            assertThat(fresh.status()).isEqualTo("RUNNING");
            assertThat(fresh.mana()).isEqualTo(120);
            for (int i = 0; i < 100; i++) tick();
            assertThat(battle.state(0).status()).isEqualTo("RUNNING");
            assertThat(battle.state(0).remainingSeconds()).isEqualTo(120);
        }
    }

    @Test void manaCannotBeSpentBelowZero() {
        battle = create("HELL");
        try (var running = battle) {
            for (int i = 0; i < 12; i++) cast("fire_shot", 8, 1);
            assertThat(battle.state(0).mana()).isZero();
            assertThatThrownBy(() -> cast("fire_shot", 8, 1)).hasMessageContaining("Insufficient mana");
            assertThat(battle.state(0).mana()).isZero();
        }
    }

    @Test void creationIsIdempotentAndOwnershipCapacityAndCleanupAreEnforced() {
        var parser = mock(DatabaseMagicParser.class);
        when(parser.parseMagicForBot(anyString())).thenAnswer(call -> magics.get(call.getArgument(0)));
        var urls = mock(ServerUrlProvider.class);
        when(urls.getServerUrl()).thenReturn("http://localhost:7001");
        var status = mock(ServerStatusService.class);
        when(status.getCurrentState()).thenReturn(ServerState.ACTIVE);
        var service = new TutorialService(parameters, prefabs, parser, new TutorialProperties(1, 256, null), urls, status);
        try {
            String request = UUID.randomUUID().toString();
            var first = service.create(7, "HELL", request);
            assertThat(service.create(7, "HELL", request)).isEqualTo(first);
            assertThat(service.create(7, "HELL", UUID.randomUUID().toString())).isEqualTo(first);
            assertThatThrownBy(() -> service.owned(first.battleId(), 8)).hasMessageContaining("403");
            assertThatThrownBy(() -> service.create(8, "NATURE", UUID.randomUUID().toString())).hasMessageContaining("429");
            var owned = service.owned(first.battleId(), 7);
            service.close(first.battleId(), 7);
            assertThatThrownBy(() -> service.owned(first.battleId(), 7)).hasMessageContaining("404");
            assertThatThrownBy(() -> owned.state(0)).hasMessageContaining("410");
            assertThat(service.create(8, "NATURE", UUID.randomUUID().toString()).ownerId()).isEqualTo(8);
        } finally { service.closeAll(); }
    }
}
