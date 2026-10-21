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
    private volatile Map<Long, Set<String>> magicTagsCache = new ConcurrentHashMap<>();

    public Set<String> getMagicTags(long magicId) {
        return magicTagsCache.computeIfAbsent(magicId, tagRepository::getMagicTags);
    }

    /**
     * Reloads the memoised tag data, the repository's first, so an admin edit to the tag tables
     * takes effect without a restart and the next bot think still answers from memory. Exposed
     * like ParameterService.invalidateCache and DatabaseMagicParser.invalidateCache.
     */
    public void invalidateCache() {
        tagRepository.invalidateCache();
        Map<Long, Set<String>> reloaded = new ConcurrentHashMap<>();
        magicTagsCache.keySet().forEach(magicId -> reloaded.put(magicId, tagRepository.getMagicTags(magicId)));
        magicTagsCache = reloaded;
    }
}
