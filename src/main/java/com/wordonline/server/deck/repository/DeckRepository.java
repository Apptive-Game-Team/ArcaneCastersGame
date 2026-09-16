package com.wordonline.server.deck.repository;

import com.wordonline.server.deck.dto.CardsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DeckRepository {

    // deck_cards keeps its table name and points at magics instead of cards.
    private static final String GET_SELECTED_DECK = """
            SELECT
              magics.id AS id,
              magics.name AS name,
              deck_cards.count AS count
            FROM
              users
            JOIN decks ON decks.id = users.selected_deck_id
            JOIN deck_cards ON deck_cards.deck_id = decks.id
            JOIN magics ON magics.id = deck_cards.magic_id
            WHERE users.id = :userId;
            """;

    private static final String GET_DECK = """
            SELECT
              magics.id AS id,
              magics.name AS name,
              deck_cards.count AS count
            FROM
            decks
            JOIN deck_cards ON deck_cards.deck_id = decks.id
            JOIN magics ON magics.id = deck_cards.magic_id
            WHERE decks.id = :deckId;
            """;

    private static final String GET_SELECTED_DECK_ID = """
            SELECT selected_deck_id
            FROM users
            WHERE id = :userId;
            """;

    private final JdbcClient jdbcClient;

    public List<CardsDto> getSelectedDeck(long userId) {
        return jdbcClient.sql(GET_SELECTED_DECK)
                .param("userId", userId)
                .query((rs, num) ->
                    new CardsDto(rs.getLong("id"),
                            rs.getString("name"),
                            rs.getInt("count"))
                ).list();
    }

    public List<CardsDto> getDeck(long deckId) {
        return jdbcClient.sql(GET_DECK)
                .param("deckId", deckId)
                .query((rs, num) ->
                        new CardsDto(rs.getLong("id"),
                                rs.getString("name"),
                                rs.getInt("count"))
                ).list();
    }

    public Optional<Long> getSelectedDeckId(long userId) {
        return jdbcClient.sql(GET_SELECTED_DECK_ID)
                .param("userId", userId)
                .query(Long.class)
                .optional();
    }
}
