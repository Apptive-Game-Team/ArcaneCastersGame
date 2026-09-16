package com.wordonline.server.game.controller;

import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.repository.CountingTagRepository;
import com.wordonline.server.game.service.MagicMetadataService;
import com.wordonline.server.game.service.ParameterService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Every cache that serves database-backed reference data has to be cleared by the one admin
 * endpoint. TagRepositoryCacheTest and MagicMetadataServiceCacheTest cover each cache on its own;
 * this test covers the wiring, so a cache added later without a call in
 * {@link ParameterController#invalidateParameterCache()} fails here.
 */
class ParameterControllerTest {

    private static final Set<String> MAGIC_TAGS = Set.of("fire", "projectile");
    private static final Set<String> SLIME_TAGS = Set.of("slime", "ground");

    private final ParameterService parameterService = mock(ParameterService.class);
    private final DatabaseMagicParser databaseMagicParser = mock(DatabaseMagicParser.class);
    private final CountingTagRepository tagRepository = new CountingTagRepository(
            Map.of("rock_slime", SLIME_TAGS),
            Map.of(7L, MAGIC_TAGS),
            2.5
    );
    private final MagicMetadataService magicMetadataService =
            new MagicMetadataService(tagRepository);
    private final ParameterController controller =
            new ParameterController(parameterService, databaseMagicParser, magicMetadataService);

    @Test
    void theEndpointClearsTheParameterAndMagicRecipeCaches() {
        controller.invalidateParameterCache();

        verify(parameterService).invalidateCache();
        verify(databaseMagicParser).invalidateCache();
    }

    @Test
    void theEndpointClearsTheTagAndCounterWeightCachesTheBotScoresWith() {
        warmEveryTagCache();

        controller.invalidateParameterCache();

        warmEveryTagCache();

        assertThat(tagRepository.magicTagLoads()).containsExactly(7L, 7L);
        assertThat(tagRepository.gameObjectTagLoads()).containsExactly("rock_slime", "rock_slime");
        assertThat(tagRepository.counterWeightLoads()).hasSize(2);
    }

    private void warmEveryTagCache() {
        // Through the service, so its own memoised copy of the magic tags is covered as well as
        // the repository's.
        magicMetadataService.getMagicTags(7L);
        tagRepository.getGameObjectTags(PrefabType.RockSlime);
        tagRepository.getCounterWeight(MAGIC_TAGS, SLIME_TAGS);
    }
}
