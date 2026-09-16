package com.wordonline.server.game.domain.magic.parser;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<Long, Magic> magicIdMap = new ConcurrentHashMap<>();

    private final MagicRepository magicRepository;
    private final ApplicationContext applicationContext;

    @PostConstruct
    private void init() {
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
                    magicIdMap.put(magic.id, magic);
                });
        log.info("[Magic:Loaded]: {}", magicIdMap.values().stream().map(Magic::getClass).map(Class::getSimpleName).toList());
    }

    public void invalidateCache() {
        magicIdMap.clear();
    }

    @Override
    public Magic parseMagic(long userId, long magicId) {
        Magic magic = getMagic(magicId);
        if (magic == null) {
            return null;
        }

        if (!magicRepository.existUserMagic(userId, magic.id)) {
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
