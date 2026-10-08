package com.wordonline.server.game.preview;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.parameter.*;
import com.wordonline.server.game.domain.object.prefab.*;
import com.wordonline.server.game.service.ParameterService;
import com.wordonline.server.preview.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionPreviewRecorderTest {
    private final Map<String, Double> inputs = new HashMap<>();
    private final Map<String, Magic> magics = new TreeMap<>();
    private final Map<String, PrefabInitializer> prefabs = new HashMap<>();

    private PreviewRecorder recorder() throws Exception {
        for (GameObjectKey owner : GameObjectKey.values()) for (ParameterKey parameter : ParameterKey.values()) {
            try { inputs.put(owner.dbName() + "." + parameter.dbName(), RemainingMagicPreviewTest.fixtureValue(owner.dbName(), parameter.dbName())); }
            catch (AssertionError ignored) { /* Optional values remain absent, like real missing rows. */ }
        }
        // PVE initializer owner names intentionally need not be in the player-magic enum.
        for (String owner : List.of("pve_nature_slime_nest", "pve_water_slime_nest"))
            for (ParameterKey parameter : ParameterKey.values()) {
                try { inputs.put(owner + "." + parameter.dbName(), RemainingMagicPreviewTest.fixtureValue(owner, parameter.dbName())); }
                catch (AssertionError ignored) { }
            }
        ParameterService service = mock(ParameterService.class);
        when(service.getValue(anyString(), anyString())).thenAnswer(call -> inputs.getOrDefault(call.getArgument(0) + "." + call.getArgument(1), 1d));
        when(service.getValueOrDefault(anyString(), anyString(), anyDouble())).thenAnswer(call ->
                inputs.getOrDefault(call.getArgument(0) + "." + call.getArgument(1), call.getArgument(2)));
        Parameters parameters = new Parameters(service);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Magic.class));
        long id = 1;
        for (var bean : scanner.findCandidateComponents("com.wordonline.server.game.domain.magic.implement")) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            var constructor = type.getConstructors()[0];
            Object[] args = Arrays.stream(constructor.getParameterTypes()).map(dependency -> {
                if (dependency == Parameters.class) return parameters;
                if (dependency == com.wordonline.server.game.domain.magic.implement.shoot.VineTossMagic.class)
                    return new com.wordonline.server.game.domain.magic.implement.shoot.VineTossMagic();
                throw new IllegalArgumentException(dependency.getSimpleName());
            }).toArray();
            Magic magic = (Magic)constructor.newInstance(args);
            magic.id = id++;
            magic.name = type.getAnnotation(Component.class).value();
            magics.put(magic.name, magic);
        }
        for (PrefabType type : PrefabType.values()) {
            try { prefabs.put(type.getBeanName(), RemainingMagicPreviewTest.initializer(type, parameters)); }
            catch (AssertionError ignored) { }
        }
        return new PreviewRecorder(prefabs, new PreviewProperties(512, 600, 4 * 1024 * 1024));
    }

    @Test void currentSnapshotChangesDamageAndSwarmQuantity() throws Exception {
        PreviewRecorder recorder = recorder();
        ObjectNode before = recorder.record("fire_shot", inputs, magics);
        inputs.put("fire_shot.damage", 250d);
        ObjectNode after = recorder.record("fire_shot", inputs, magics);
        assertThat(after).isNotEqualTo(before);
        assertThat(after.path("scenarios").get(0).path("parameters").path("fire_shot.damage").asDouble()).isEqualTo(250);
        inputs.put("mini_rock.quantity", 4d);
        ObjectNode swarm = recorder.record("mini_rock_swarm", inputs, magics);
        long count = java.util.stream.StreamSupport.stream(swarm.path("scenarios").get(0).path("frames").spliterator(), false)
                .flatMap(frame -> java.util.stream.StreamSupport.stream(frame.path("objects").path("create").spliterator(), false))
                .filter(object -> object.path("type").asText().equals("MiniRock")).count();
        assertThat(count).isEqualTo(4);
    }

    @Test void allProductionMagicScenariosRunWithoutStaticMocks() throws Exception {
        PreviewRecorder recorder = recorder();
        List<String> failures = new ArrayList<>();
        int scenarios = 0;
        long bytes = 0, compressed = 0;
        for (String name : magics.keySet()) {
            try {
                ObjectNode recording = recorder.record(name, inputs, magics);
                scenarios += recording.path("scenarios").size();
                byte[] content = recording.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                bytes += content.length;
                try (var buffer = new java.io.ByteArrayOutputStream()) {
                    try (var gzip = new java.util.zip.GZIPOutputStream(buffer)) { gzip.write(content); }
                    compressed += buffer.size();
                }
                assertThat(recording.path("version").asInt()).isEqualTo(2);
                assertThat(recording.path("magic").asText()).isEqualTo(name);
                for (var scenario : recording.path("scenarios")) {
                    assertThat(scenario.path("frames").size()).isPositive();
                    assertThat(scenario.has("fixtureTargetIds")).isTrue();
                    assertThat(scenario.has("parameters")).isTrue();
                }
            } catch (Exception | AssertionError e) { failures.add(name + ": " + e.getMessage()); }
        }
        assertThat(failures).isEmpty();
        assertThat(magics).hasSize(85);
        assertThat(scenarios).isEqualTo(129);
        System.out.printf("Production previews: %d magics, %d scenarios, %d compact bytes, %d gzip bytes%n", magics.size(), scenarios, bytes, compressed);
    }

    @Test void invalidIntervalsFailInsteadOfEnteringUnboundedComponentLoop() throws Exception {
        PreviewRecorder recorder = recorder();
        inputs.put("vine_colony.attack_interval", 0d);
        assertThatThrownBy(() -> recorder.record("vine_colony", inputs, magics)).isInstanceOf(RuntimeException.class);
    }
}
