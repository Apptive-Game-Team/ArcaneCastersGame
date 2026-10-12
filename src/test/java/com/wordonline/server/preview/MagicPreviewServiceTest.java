package com.wordonline.server.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.domain.magic.implement.shoot.FireShotMagic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.repository.ParameterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

class MagicPreviewServiceTest {
    @Test void invalidationDiscardsInFlightCatalogAndPublishesOnlyCurrentGeneration() throws Exception {
        ParameterRepository repository = mock(ParameterRepository.class);
        DatabaseMagicParser parser = mock(DatabaseMagicParser.class);
        PreviewRecorder recorder = mock(PreviewRecorder.class);
        var magic = new FireShotMagic(); magic.name = "fire_shot";
        when(parser.getAllMagics()).thenReturn(List.of(magic));
        when(repository.snapshot()).thenReturn(Map.of("fire_shot.damage", 100d), Map.of("fire_shot.damage", 200d));
        CountDownLatch entered = new CountDownLatch(1), proceed = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ObjectMapper json = new ObjectMapper();
        when(recorder.record(anyString(), anyMap(), anyMap())).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) { entered.countDown(); proceed.await(3, TimeUnit.SECONDS); }
            Map<String, Double> inputs = call.getArgument(1);
            return json.createObjectNode().put("damage", inputs.get("fire_shot.damage"));
        });
        try (var service = new CloseableService(repository, parser, recorder, json)) {
            service.value.invalidate();
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            service.value.invalidate();
            assertThat(service.value.catalog().status()).isEqualTo("generating");
            proceed.countDown();
            await().atMost(Duration.ofSeconds(5)).until(() -> service.value.catalog().status().equals("ready"));
            String revision = service.value.catalog().revision();
            assertThat(json.readTree(service.value.recording("fire_shot", revision)).path("damage").asInt()).isEqualTo(200);
            assertThat(calls).hasValue(2);
            for (int i = 0; i < 10; i++) service.value.catalog();
            assertThat(calls).hasValue(2);
            assertThatThrownBy(() -> service.value.recording("fire_shot", "old")).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test void oneUnavailableMagicDoesNotDiscardSuccessfulRecordings() throws Exception {
        ParameterRepository repository = mock(ParameterRepository.class);
        DatabaseMagicParser parser = mock(DatabaseMagicParser.class);
        PreviewRecorder recorder = mock(PreviewRecorder.class);
        var first = new FireShotMagic(); first.name = "fire_shot";
        var second = new FireShotMagic(); second.name = "new_magic";
        when(parser.getAllMagics()).thenReturn(List.of(first, second));
        when(repository.snapshot()).thenReturn(Map.of());
        when(recorder.record(eq("fire_shot"), anyMap(), anyMap())).thenReturn(new ObjectMapper().createObjectNode().put("version", 2));
        when(recorder.record(eq("new_magic"), anyMap(), anyMap())).thenThrow(new IllegalArgumentException("No scenario"));
        try (var service = new CloseableService(repository, parser, recorder, new ObjectMapper())) {
            service.value.invalidate();
            await().atMost(Duration.ofSeconds(5)).until(() -> service.value.catalog().status().equals("ready"));
            assertThat(service.value.catalog().magics()).extracting(PreviewCatalog.Entry::available).containsExactly(true, false);
            byte[] bytes = service.value.recording("fire_shot", service.value.catalog().revision());
            bytes[0] = 0;
            assertThat(service.value.recording("fire_shot", service.value.catalog().revision())[0]).isEqualTo((byte)'{');
        }
    }

    private static class CloseableService implements AutoCloseable {
        final MagicPreviewService value;
        CloseableService(ParameterRepository repository, DatabaseMagicParser parser, PreviewRecorder recorder, ObjectMapper json) {
            value = new MagicPreviewService(repository, parser, recorder, new PreviewProperties(0, 0, 0), json);
        }
        @Override public void close() { value.close(); }
    }
}
