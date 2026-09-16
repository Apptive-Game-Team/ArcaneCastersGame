package com.wordonline.server.game.repository;

import java.util.List;
import java.util.Locale;

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

    private final static String USER_MAGIC_EXIST = """
            SELECT EXISTS (
                 SELECT 1
                 FROM user_magics
                 WHERE user_id = :userId
                   AND magic_id = :magicId
             );
            """;

    // magic_cards and cards are gone: a magic is looked up by its own id, and the element it
    // carries is a column on the row rather than the most common card in a combination.
    private final static String FIND_ALL = """
            SELECT id, name, element
            FROM magics;
            """;

    public boolean existUserMagic(long userId, long magicId) {
        return jdbcClient.sql(USER_MAGIC_EXIST)
                .param("userId", userId)
                .param("magicId", magicId)
                .query(Boolean.class)
                .optional()
                .orElse(false);
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
