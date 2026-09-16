package com.wordonline.server.game.service;

import com.wordonline.server.game.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MagicMetadataService {

    private final TagRepository tagRepository;
    private final Map<Long, Set<String>> magicTagsCache = new ConcurrentHashMap<>();

    public Set<String> getMagicTags(long magicId) {
        return magicTagsCache.computeIfAbsent(magicId, tagRepository::getMagicTags);
    }

    /**
     * Clears the memoised tag data, including the repository's, so an admin edit to the tag tables
     * takes effect without a restart. Exposed like ParameterService.invalidateCache and
     * DatabaseMagicParser.invalidateCache.
     */
    public void invalidateCache() {
        magicTagsCache.clear();
        tagRepository.invalidateCache();
    }
}
