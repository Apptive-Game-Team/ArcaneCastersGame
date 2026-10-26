package com.wordonline.server.game.domain.magic.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.MagicInfoDto;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.repository.MagicRepository;
import com.wordonline.server.game.service.GameContext;

/**
 * The admin invalidate endpoint used to leave the magic map empty, so the next lookup - usually a
 * cast on a game loop thread - reloaded it from the database.
 */
class DatabaseMagicParserReloadTest {

    private final MagicRepository magicRepository = mock(MagicRepository.class);
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final DatabaseMagicParser parser = new DatabaseMagicParser(magicRepository, applicationContext);
    private final Magic fireball = new TestMagic();

    @Test
    void invalidateCacheReloadsBeforeTheNextLookup() {
        when(applicationContext.containsBean("fireball")).thenReturn(true);
        when(applicationContext.getBean("fireball", Magic.class)).thenReturn(fireball);
        when(magicRepository.getAllMagic())
                .thenReturn(List.of(new MagicInfoDto(3L, "fireball", ElementType.NONE)));

        parser.invalidateCache();
        verify(magicRepository).getAllMagic();

        assertThat(parser.getMagic(3L)).isSameAs(fireball);
        assertThat(parser.getAllMagics()).containsExactly(fireball);
        verify(magicRepository).getAllMagic();
    }

    @Test
    void aFailedReloadKeepsTheMagicsLoadedBefore() {
        when(applicationContext.containsBean("fireball")).thenReturn(true);
        when(applicationContext.getBean("fireball", Magic.class)).thenReturn(fireball);
        when(magicRepository.getAllMagic())
                .thenReturn(List.of(new MagicInfoDto(3L, "fireball", ElementType.NONE)))
                .thenThrow(new IllegalStateException("database down"));
        parser.invalidateCache();

        assertThatThrownBy(parser::invalidateCache).isInstanceOf(IllegalStateException.class);

        assertThat(parser.getMagic(3L)).isSameAs(fireball);
        verify(magicRepository, times(2)).getAllMagic();
    }

    private static final class TestMagic extends Magic {
        @Override
        public void run(GameContext gameContext, Master master, Vector3 position) {
        }
    }
}
