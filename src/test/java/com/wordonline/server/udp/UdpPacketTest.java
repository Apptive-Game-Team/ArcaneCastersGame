package com.wordonline.server.udp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class UdpPacketTest {

    @Test
    void roundTripsEveryHeaderField() {
        byte[] body = {1, 2, 3};
        UdpPacket packet = new UdpPacket(UdpPacketType.INPUT, UdpPacket.FLAG_RELIABLE, 0xFFFFFFF0, 0x0123456789ABCDEFL, body);

        UdpPacket decoded = UdpPacket.decode(packet.encode(), packet.encode().length).orElseThrow();

        assertThat(decoded.type()).isEqualTo(UdpPacketType.INPUT);
        assertThat(decoded.isReliable()).isTrue();
        assertThat(decoded.isFragment()).isFalse();
        assertThat(decoded.seq()).isEqualTo(0xFFFFFFF0);
        assertThat(decoded.connectionId()).isEqualTo(0x0123456789ABCDEFL);
        assertThat(decoded.body()).isEqualTo(body);
    }

    @Test
    void headerIsSixteenBytesInWireOrder() {
        byte[] wire = new UdpPacket(UdpPacketType.FRAME, 0, 1, 2L, new byte[0]).encode();

        assertThat(wire).hasSize(UdpPacket.HEADER_SIZE);
        assertThat(wire).startsWith((byte) 0xAC, (byte) 6, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 1);
    }

    @Test
    void ignoresDatagramsThatAreNotOurs() {
        byte[] valid = new UdpPacket(UdpPacketType.HELLO, 0, 1, 0L, new byte[0]).encode();

        byte[] wrongMagic = Arrays.copyOf(valid, valid.length);
        wrongMagic[0] = 0;
        byte[] unknownType = Arrays.copyOf(valid, valid.length);
        unknownType[1] = 99;

        assertThat(UdpPacket.decode(wrongMagic, wrongMagic.length)).isEmpty();
        assertThat(UdpPacket.decode(unknownType, unknownType.length)).isEmpty();
        assertThat(UdpPacket.decode(valid, UdpPacket.HEADER_SIZE - 1)).isEmpty();
    }

    @Test
    void decodeUsesOnlyTheReceivedLengthOfALargerBuffer() {
        byte[] wire = new UdpPacket(UdpPacketType.INPUT, 0, 1, 2L, new byte[] {7}).encode();
        byte[] buffer = Arrays.copyOf(wire, 2048);

        assertThat(UdpPacket.decode(buffer, wire.length).orElseThrow().body()).containsExactly(7);
    }
}
