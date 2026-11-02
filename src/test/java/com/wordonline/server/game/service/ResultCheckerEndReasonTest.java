package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.dto.Master;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResultCheckerEndReasonTest {
    @Test
    void surrenderKeepsTheFirstResultAndReason() {
        var session = mock(SessionObject.class);
        var result = new ResultChecker(session);
        result.surrender(Master.LeftPlayer);
        result.surrender(Master.RightPlayer);
        assertThat(result.getLoser()).isEqualTo(Master.LeftPlayer);
        assertThat(result.getEndReason()).isEqualTo("SURRENDER");
    }

    @Test
    void lateSurrenderCannotChangeNormalLossOrDraw() {
        var session = mock(SessionObject.class);
        for (boolean draw : new boolean[]{true, false}) {
            var result = new ResultChecker(session);
            if (draw) result.setEnd();
            else result.setLoser(Master.RightPlayer);
            result.surrender(Master.LeftPlayer);
            assertThat(result.getLoser()).isEqualTo(draw ? null : Master.RightPlayer);
            assertThat(result.getEndReason()).isNull();
        }
    }

    @Test
    void disconnectReasonIsCapturedWhenTheLoserIsDecided() {
        var session = mock(SessionObject.class);
        when(session.getLeftUserId()).thenReturn(7L);
        when(session.isDisconnected(7L)).thenReturn(true);
        var result = new ResultChecker(session);
        result.setLoser(Master.LeftPlayer);
        when(session.isDisconnected(7L)).thenReturn(false);
        assertThat(result.getEndReason()).isEqualTo("DISCONNECT");
        var recovered = new ResultChecker(session);
        recovered.setLoser(Master.LeftPlayer);
        assertThat(recovered.getEndReason()).isNull();
    }
}
