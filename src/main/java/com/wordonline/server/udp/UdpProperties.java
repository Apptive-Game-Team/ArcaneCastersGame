package com.wordonline.server.udp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the UDP game channel. Off by default: a server that does not enable it keeps serving
 * every client over STOMP exactly as before.
 *
 * @param enabled           whether the UDP listener starts
 * @param port              port to bind, 0 picks a free one (tests)
 * @param maxDatagramBytes  largest datagram sent, header included; kept under a common path MTU
 * @param idleTimeoutMillis a peer that sends nothing for this long is forgotten
 * @param retransmitMillis  how long a reliable MESSAGE waits for its ack before it is sent again
 * @param maxRetransmits    resends before a reliable MESSAGE is given up on
 */
@ConfigurationProperties(prefix = "game.udp")
public record UdpProperties(
        boolean enabled,
        Integer port,
        Integer maxDatagramBytes,
        Long idleTimeoutMillis,
        Long retransmitMillis,
        Integer maxRetransmits
) {
    public UdpProperties {
        port = port == null ? 7777 : port;
        maxDatagramBytes = maxDatagramBytes == null ? 1200 : maxDatagramBytes;
        idleTimeoutMillis = idleTimeoutMillis == null ? 15_000L : idleTimeoutMillis;
        retransmitMillis = retransmitMillis == null ? 100L : retransmitMillis;
        maxRetransmits = maxRetransmits == null ? 20 : maxRetransmits;
    }
}
