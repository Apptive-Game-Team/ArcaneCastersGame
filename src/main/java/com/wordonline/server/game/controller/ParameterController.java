package com.wordonline.server.game.controller;

import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.service.MagicMetadataService;
import com.wordonline.server.game.service.ParameterService;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ParameterController {

    private final ParameterService parameterService;
    private final DatabaseMagicParser databaseMagicParser;
    private final MagicMetadataService magicMetadataService;
    private final com.wordonline.server.preview.MagicPreviewService magicPreviewService;

    @PreAuthorize("hasAuthority('WORDONLINE_SERVER')")
    @PostMapping("/invalidate")
    public void invalidateParameterCache() {
        parameterService.invalidateCache();
        databaseMagicParser.invalidateCache();
        // The bot's tag lookups are memoised too; an admin edit to the tag tables only takes
        // effect on a running server if this endpoint clears them as well.
        magicMetadataService.invalidateCache();
        magicPreviewService.invalidate();
    }
}
