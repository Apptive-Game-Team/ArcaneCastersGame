package com.wordonline.server.udp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReliableWindowTest {

    @Test
    void acceptsEachSeqOnce() {
        ReliableWindow window = new ReliableWindow();

        assertThat(window.accept(1)).isTrue();
        assertThat(window.accept(1)).isFalse();
        assertThat(window.accept(2)).isTrue();
    }

    @Test
    void acceptsAnOlderSeqThatArrivesLateOnce() {
        ReliableWindow window = new ReliableWindow();
        window.accept(5);

        assertThat(window.accept(3)).isTrue();
        assertThat(window.accept(3)).isFalse();
    }

    @Test
    void treatsSeqsBelowTheWindowAsSeen() {
        ReliableWindow window = new ReliableWindow();
        window.accept(200);

        assertThat(window.accept(100)).isFalse();
    }

    @Test
    void aBigJumpForgetsTheOldWindow() {
        ReliableWindow window = new ReliableWindow();
        window.accept(1);
        window.accept(1000);

        assertThat(window.accept(1000)).isFalse();
        assertThat(window.accept(999)).isTrue();
    }
}
