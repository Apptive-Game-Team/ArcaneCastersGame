package com.wordonline.server.game.domain.magic.parser;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.repository.MagicRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMagicParser implements MagicParser {

    public static final long INVALID_MAGIC_ID = 0;

    // A card is a magic, so there is no combination to key on: the id on the card is the lookup.
    // init builds a new map and swaps it in, so a loop thread reading during an admin reload sees
    // the old map or the new one, never an empty one it would have to refill from the database.
    private volatile Map<Long, Magic> magicIdMap = Map.of();

    private final MagicRepository magicRepository;
    private final ApplicationContext applicationContext;

    @PostConstruct
    private void init() {
        Map<Long, Magic> loaded = new HashMap<>();
        magicRepository.getAllMagic()
                .forEach(magicInfoDto -> {
                    if (!applicationContext.containsBean(magicInfoDto.name())) {
                        log.warn("[Magic:Loading] magic ({}) bean is not available", magicInfoDto.name());
                        return;
                    }

                    Magic magic = applicationContext.getBean(magicInfoDto.name(), Magic.class);
                    magic.id = magicInfoDto.id();
                    magic.name = magicInfoDto.name();
                    magic.element = magicInfoDto.element();
                    loaded.put(magic.id, magic);
                });
        magicIdMap = Map.copyOf(loaded);
        log.info("[Magic:Loaded]: {}", loaded.values().stream().map(Magic::getClass).map(Class::getSimpleName).toList());
    }

    // Reloads now instead of leaving the map empty: an empty map is refilled by whichever thread
    // asks next, which is usually a game loop thread. A failed reload keeps the old map.
    public void invalidateCache() {
        init();
    }

    @Override
    public Magic parseMagic(long userId, long magicId, Set<Long> ownedMagicIds) {
        Magic magic = getMagic(magicId);
        if (magic == null) {
            return null;
        }

        if (!ownedMagicIds.contains(magic.id)) {
            log.info("[Magic:NotOwned] User {} does not have Magic(id:{}) yet. Rejecting magic cast.", userId, magic.id);
            return null;
        }

        return magic;
    }

    public Magic parseMagicForBot(String magicName) {
        if (magicIdMap.isEmpty()) {
            init();
        }

        if (magicName == null || magicName.isBlank()) {
            return null;
        }

        if (!applicationContext.containsBean(magicName)) {
            log.warn("[MagicNotFound] No magic bean found for name: {}", magicName);
            return null;
        }

        Magic magic = applicationContext.getBean(magicName, Magic.class);
        // A bean with no magics row never went through init, so it carries no name to read
        // parameters under. The bean name is that name.
        if (magic.name == null) {
            magic.name = magicName;
        }
        if (magic.id <= 0) {
            log.warn("[MagicIdMissing] Magic '{}' has non-positive id ({}).", magicName, magic.id);
        }
        return magic;
    }

    public Magic parseMagicForBot(long magicId) {
        return getMagic(magicId);
    }

    /** Every magic that has both a magics row and a bean. The bot prices the summon catalogue from it. */
    public Collection<Magic> getAllMagics() {
        if (magicIdMap.isEmpty()) {
            init();
        }
        return List.copyOf(magicIdMap.values());
    }

    public Magic getMagic(long magicId) {
        if (magicIdMap.isEmpty()) {
            init();
        }

        if (magicId <= INVALID_MAGIC_ID) {
            return null;
        }

        Magic magic = magicIdMap.get(magicId);
        if (magic == null) {
            log.warn("[MagicNotFound] No magic mapped for id: {}", magicId);
        }
        return magic;
    }
}
