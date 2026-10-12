package com.wordonline.server.game.service.pve;

import static org.assertj.core.api.Assertions.assertThat;

import com.wordonline.server.game.dto.pve.PveStateDto;
import org.junit.jupiter.api.Test;

class PveStateStoreTest {

    @Test
    void oneSeqCountsChangesAcrossChannelsAndSkipsUnchangedValues() {
        PveStateStore store = new PveStateStore();

        assertThat(store.set("bgm", "boss")).contains(new PveStateDto("bgm", "boss", 1));
        assertThat(store.set("weather", "rain")).contains(new PveStateDto("weather", "rain", 2));
        assertThat(store.set("bgm", "boss")).isEmpty();
        assertThat(store.set("bgm", null)).contains(new PveStateDto("bgm", null, 3));

        assertThat(store.snapshot()).containsExactly(
                new PveStateDto("weather", "rain", 2),
                new PveStateDto("bgm", null, 3));
    }
}
