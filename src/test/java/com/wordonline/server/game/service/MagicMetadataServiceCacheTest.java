package com.wordonline.server.game.service;

import com.wordonline.server.game.repository.CountingTagRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MagicMetadataServiceCacheTest {

    private static final Set<String> MAGIC_TAGS = Set.of("fire", "projectile");

    @Test
    void magicTagsAreServedFromMemoryAfterTheFirstCall() {
        CountingTagRepository tagRepository = tagRepository();
        MagicMetadataService service = new MagicMetadataService(tagRepository);

        assertThat(service.getMagicTags(7L)).isEqualTo(MAGIC_TAGS);
        assertThat(service.getMagicTags(7L)).isEqualTo(MAGIC_TAGS);

        assertThat(tagRepository.magicTagLoads()).containsExactly(7L);
    }

    @Test
    void invalidatingClearsBothTheServiceCacheAndTheRepositoryCaches() {
        CountingTagRepository tagRepository = tagRepository();
        MagicMetadataService service = new MagicMetadataService(tagRepository);

        service.getMagicTags(7L);
        tagRepository.getGameObjectTags("rock_slime");

        service.invalidateCache();

        service.getMagicTags(7L);
        tagRepository.getGameObjectTags("rock_slime");

        assertThat(tagRepository.magicTagLoads()).containsExactly(7L, 7L);
        assertThat(tagRepository.gameObjectTagLoads()).containsExactly("rock_slime", "rock_slime");
    }

    private CountingTagRepository tagRepository() {
        return new CountingTagRepository(
                Map.of("rock_slime", Set.of("slime", "ground")),
                Map.of(7L, MAGIC_TAGS),
                2.5
        );
    }
}
