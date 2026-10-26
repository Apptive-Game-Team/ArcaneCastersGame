package com.wordonline.server.game.service;

import com.wordonline.server.auth.repository.UserRepository;
import com.wordonline.server.game.dto.result.ResultMmrDto;
import com.wordonline.server.game.dto.result.ResultType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MmrServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final MmrService service = new MmrService(userRepository);

    @Test
    void resolvesBotRatingFromNegativeUser() {
        when(userRepository.getMmr(-7)).thenReturn(Optional.of((short) 1200));

        MmrService.RatingRef result = service.resolveRatingRef(-7);

        assertThat(result.rating()).isEqualTo((short) 1200);
        assertThat(result.storageId()).isEqualTo(-7);
        assertThat(result.bot()).isTrue();
    }

    @Test
    void botMatchUpdatesOnlyBotUserRating() {
        when(userRepository.getMmr(10)).thenReturn(Optional.of((short) 1000));
        when(userRepository.getMmr(-7)).thenReturn(Optional.of((short) 1000));

        service.updateMatchResult(10, -7, ResultType.Win, false);

        verify(userRepository, never()).setMmr(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyShort());
        verify(userRepository, never()).addMmr(org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.anyShort(), org.mockito.ArgumentMatchers.anyShort());
        verify(userRepository).addMmr(-7L, (short) -10, (short) 1000);
    }

    @Test
    void computesTheSameRatingsUpdateMatchResultStores() {
        when(userRepository.getMmr(1)).thenReturn(Optional.of((short) 1200));
        when(userRepository.getMmr(2)).thenReturn(Optional.of((short) 1000));

        ResultMmrDto stored = service.updateMatchResult(1, 2, ResultType.Win, true);
        ResultMmrDto computed = MmrService.computeMatchResult(
                service.resolveRatingRef(1), service.resolveRatingRef(2), ResultType.Win, true);

        assertThat(computed).isEqualTo(stored)
                .isEqualTo(new ResultMmrDto((short) 1200, (short) 1000, (short) 1205, (short) 995));
        verify(userRepository).setMmr(1, (short) 1205);
        verify(userRepository).setMmr(2, (short) 995);
    }

    @Test
    void aDrawBetweenEqualRatingsChangesNothing() {
        ResultMmrDto result = MmrService.computeMatchResult(human(1, 1000), human(2, 1000), ResultType.Draw, true);

        assertThat(result).isEqualTo(new ResultMmrDto((short) 1000, (short) 1000, (short) 1000, (short) 1000));
    }

    @Test
    void aPracticeBotMatchKeepsTheHumanRatingAndMovesOnlyTheBot() {
        ResultMmrDto result = MmrService.computeMatchResult(human(10, 1000), bot(-7, 1000), ResultType.Lose, false);

        assertThat(result).isEqualTo(new ResultMmrDto((short) 1000, (short) 1000, (short) 1000, (short) 1010));
    }

    @Test
    void aRankedBotMatchMovesBothRatings() {
        ResultMmrDto result = MmrService.computeMatchResult(human(10, 1000), bot(-7, 1000), ResultType.Lose, true);

        assertThat(result).isEqualTo(new ResultMmrDto((short) 1000, (short) 1000, (short) 990, (short) 1010));
    }

    @Test
    void savesARankedBotMatchAsTheHumanValueAndTheBotChange() {
        ResultMmrDto result = MmrService.computeMatchResult(human(10, 1000), bot(-7, 1000), ResultType.Win, true);

        service.saveMatchResult(human(10, 1000), bot(-7, 1000), result);

        verify(userRepository).setMmr(10L, (short) 1010);
        verify(userRepository).addMmr(-7L, (short) -10, (short) 1000);
        verify(userRepository, never()).setMmr(org.mockito.ArgumentMatchers.eq(-7L),
                org.mockito.ArgumentMatchers.anyShort());
    }

    @Test
    void savesAHumanRatingAsTheComputedValue() {
        ResultMmrDto result = new ResultMmrDto((short) 1000, (short) 1000, (short) 1010, (short) 990);

        service.saveMatchResult(human(1, 1000), human(2, 1000), result);

        verify(userRepository).setMmr(1, (short) 1010);
        verify(userRepository).setMmr(2, (short) 990);
        verify(userRepository, never()).getMmr(org.mockito.ArgumentMatchers.anyLong());
    }

    // A bot plays several sessions at once, so its rating moves by the change this match made
    // rather than being overwritten with a value computed from the rating it had at the start.
    @Test
    void savesABotRatingAsTheChangeFromTheRatingItStartedWith() {
        ResultMmrDto result = MmrService.computeMatchResult(bot(-3, 1100), bot(-4, 1000), ResultType.Win, true);

        service.saveMatchResult(bot(-3, 1100), bot(-4, 1000), result);

        assertThat(result).isEqualTo(new ResultMmrDto((short) 1100, (short) 1000, (short) 1107, (short) 993));
        verify(userRepository).addMmr(-3L, (short) 7, (short) 1000);
        verify(userRepository).addMmr(-4L, (short) -7, (short) 1000);
        verify(userRepository, never()).setMmr(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyShort());
    }

    private static MmrService.RatingRef human(long id, int rating) {
        return new MmrService.RatingRef(id, id, false, (short) rating);
    }

    private static MmrService.RatingRef bot(long id, int rating) {
        return new MmrService.RatingRef(id, id, true, (short) rating);
    }

    @Test
    void rankedBotMatchUpdatesBothRatings() {
        when(userRepository.getMmr(10)).thenReturn(Optional.of((short) 1000));
        when(userRepository.getMmr(-7)).thenReturn(Optional.of((short) 1000));

        var result = service.updateMatchResult(10, -7, ResultType.Win, true);

        assertThat(result.newLeftMmr()).isEqualTo((short) 1010);
        assertThat(result.newRightMmr()).isEqualTo((short) 990);
        verify(userRepository).setMmr(10L, (short) 1010);
        verify(userRepository).addMmr(-7L, (short) -10, (short) 1000);
    }

    @Test
    void practiceBotMatchKeepsHumanRating() {
        when(userRepository.getMmr(10)).thenReturn(Optional.of((short) 1000));
        when(userRepository.getMmr(-7)).thenReturn(Optional.of((short) 1000));

        var result = service.updateMatchResult(10, -7, ResultType.Win, false);

        assertThat(result.newLeftMmr()).isEqualTo((short) 1000);
        verify(userRepository, never()).setMmr(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyShort());
        verify(userRepository).addMmr(-7L, (short) -10, (short) 1000);
    }

    @Test
    void updateMatchResultIsPublicSoClassTransactionalApplies() throws Exception {
        int modifiers = MmrService.class
                .getDeclaredMethod("updateMatchResult", long.class, long.class, ResultType.class, boolean.class)
                .getModifiers();

        assertThat(Modifier.isPublic(modifiers)).isTrue();
    }
}
