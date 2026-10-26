package com.wordonline.server.game.repository;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.dto.MagicInfoDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MagicRepository {

    private final JdbcClient jdbcClient;

    private final static String USER_MAGIC_IDS = """
            SELECT magic_id
            FROM user_magics
            WHERE user_id = :userId;
            """;

    // magic_cards and cards are gone: a magic is looked up by its own id, and the element it
    // carries is a column on the row rather than the most common card in a combination.
    private final static String FIND_ALL = """
            SELECT id, name, element
            FROM magics;
            """;

    /** Every magic id the user owns. Read once when a session is created, never during a cast. */
    public Set<Long> findUserMagicIds(long userId) {
        return Set.copyOf(jdbcClient.sql(USER_MAGIC_IDS)
                .param("userId", userId)
                .query(Long.class)
                .list());
    }

    public List<MagicInfoDto> getAllMagic() {
        return jdbcClient.sql(FIND_ALL)
                .query((rs, num) -> new MagicInfoDto(
                            rs.getLong("id"),
                            rs.getString("name"),
                            toElement(rs.getString("name"), rs.getString("element"))
                )).list();
    }

    // magics.element is a varchar with a check constraint, not an enum, so a value the server does
    // not know is a data problem rather than a reason to refuse to load the whole catalogue.
    private static ElementType toElement(String magicName, String element) {
        if (element == null || element.isBlank()) {
            return ElementType.NONE;
        }
        try {
            return ElementType.valueOf(element.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("[Magic:UnknownElement] magic ({}) has element '{}'; treating it as NONE", magicName, element);
            return ElementType.NONE;
        }
    }
}
