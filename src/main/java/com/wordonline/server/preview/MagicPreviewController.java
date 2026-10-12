package com.wordonline.server.preview;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/server/magic-previews")
@PreAuthorize("hasAuthority('WORDONLINE_SERVER')")
@RequiredArgsConstructor
public class MagicPreviewController {
    private final MagicPreviewService service;

    @GetMapping public ResponseEntity<PreviewCatalog> catalog() {
        PreviewCatalog catalog = service.catalog();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Retry-After", "5").body(catalog);
    }

    @GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> recording(@PathVariable String name, @RequestParam String revision) {
        byte[] bytes = service.recording(name, revision);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .eTag(MagicPreviewService.hash(bytes)).body(bytes);
    }
}
