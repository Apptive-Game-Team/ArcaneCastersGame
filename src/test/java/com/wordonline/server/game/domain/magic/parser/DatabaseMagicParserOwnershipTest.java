package com.wordonline.server.game.domain.magic.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.dto.MagicInfoDto;
import com.wordonline.server.game.repository.MagicRepository;

/** The cast-time ownership check reads the set loaded at session creation and never queries. */
class DatabaseMagicParserOwnershipTest {

    private static final long OWNED_ID = 5L;
    private static final long UNOWNED_ID = 6L;

    private final MagicRepository magicRepository = mock(MagicRepository.class);
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private DatabaseMagicParser parser;

    @BeforeEach
    void setUp() {
        when(magicRepository.getAllMagic()).thenReturn(List.of(
                new MagicInfoDto(OWNED_ID, "owned_magic", ElementType.NONE),
                new MagicInfoDto(UNOWNED_ID, "unowned_magic", ElementType.NONE)));
        for (String name : List.of("owned_magic", "unowned_magic")) {
            when(applicationContext.containsBean(name)).thenReturn(true);
            when(applicationContext.getBean(name, Magic.class)).thenReturn(mock(Magic.class));
        }
        parser = new DatabaseMagicParser(magicRepository, applicationContext);
    }

    @Test
    void ownedMagicResolvesWithoutAnyRepositoryCall() {
        Magic magic = parser.parseMagic(1L, OWNED_ID, Set.of(OWNED_ID));

        assertThat(magic).isNotNull();
        assertThat(magic.id).isEqualTo(OWNED_ID);
        verify(magicRepository).getAllMagic();
        verifyNoMoreInteractions(magicRepository);
    }

    @Test
    void unownedMagicIsRejectedWithoutAnyRepositoryCall() {
        assertThat(parser.parseMagic(1L, UNOWNED_ID, Set.of(OWNED_ID))).isNull();
        assertThat(parser.parseMagic(1L, UNOWNED_ID, Set.of())).isNull();
        verify(magicRepository).getAllMagic();
        verifyNoMoreInteractions(magicRepository);
    }
}
