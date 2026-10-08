package com.wordonline.server.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.repository.ParameterRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class MagicPreviewService {
    private final ParameterRepository parameters;
    private final DatabaseMagicParser parser;
    private final PreviewRecorder recorder;
    private final PreviewProperties limits;
    private final ObjectMapper json;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("magic-preview").daemon(true).factory());
    private long generation;
    private boolean running;
    private boolean closed;
    private volatile Published published = new Published(new PreviewCatalog("generating", null, List.of()), Map.of());

    public MagicPreviewService(ParameterRepository parameters, DatabaseMagicParser parser, PreviewRecorder recorder,
                               PreviewProperties limits, ObjectMapper json) {
        this.parameters = parameters;
        this.parser = parser;
        this.recorder = recorder;
        this.limits = limits;
        this.json = json;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() { invalidate(); }

    /** Coalesces invalidations; at most one catalog is being simulated at a time. */
    public synchronized void invalidate() {
        if (closed) return;
        generation++;
        published = new Published(new PreviewCatalog("generating", null, List.of()), Map.of());
        if (!running) {
            running = true;
            worker.execute(this::generate);
        }
    }

    public PreviewCatalog catalog() { return published.catalog(); }

    public byte[] recording(String name, String revision) {
        Published snapshot = published;
        if (!"ready".equals(snapshot.catalog().status()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Previews are being generated");
        if (!Objects.equals(revision, snapshot.catalog().revision()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Preview revision changed");
        byte[] result = snapshot.recordings().get(name);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Preview unavailable");
        return result.clone();
    }

    private void generate() {
        while (!Thread.currentThread().isInterrupted()) {
            long requested;
            synchronized (this) { requested = generation; }
            Published result;
            try {
                Map<String, Double> inputs = parameters.snapshot();
                Map<String, Magic> magics = new TreeMap<>();
                parser.getAllMagics().forEach(magic -> magics.put(magic.name, magic));
                List<PreviewCatalog.Entry> entries = new ArrayList<>();
                Map<String, byte[]> recordings = new HashMap<>();
                for (String name : magics.keySet()) {
                    synchronized (this) { if (requested != generation || closed) break; }
                    try {
                        byte[] bytes = json.writeValueAsBytes(recorder.record(name, inputs, magics));
                        if (bytes.length > limits.maxClipBytes()) throw new IllegalStateException("Preview byte limit exceeded");
                        recordings.put(name, bytes);
                        entries.add(new PreviewCatalog.Entry(name, hash(bytes), bytes.length, true));
                    } catch (CancellationException e) { throw e; }
                    catch (Exception e) {
                        // Only the magic name is public; exception details remain in server diagnostics.
                        log.warn("[Preview] {} unavailable: {}", name, e.toString());
                        entries.add(new PreviewCatalog.Entry(name, null, 0, false));
                    }
                }
                String revision = hash(json.writeValueAsBytes(entries));
                result = new Published(new PreviewCatalog("ready", revision, List.copyOf(entries)), Map.copyOf(recordings));
            } catch (CancellationException e) { return; }
            catch (Exception e) {
                log.error("[Preview] Catalog generation failed", e);
                result = new Published(new PreviewCatalog("failed", null, List.of()), Map.of());
            }
            synchronized (this) {
                if (closed) return;
                if (requested != generation) continue;
                published = result;
                running = false;
                return;
            }
        }
    }

    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @PreDestroy public synchronized void close() {
        closed = true;
        worker.shutdownNow();
    }

    private record Published(PreviewCatalog catalog, Map<String, byte[]> recordings) { }
}
