package com.wordonline.server.udp;

/**
 * Drops repeats of reliable packets. Keeps the highest seq seen and a 64 bit window of the ones
 * just below it; anything older than the window counts as already seen, because the sender gave up
 * on it long ago.
 */
public class ReliableWindow {

    private static final int WINDOW = 64;

    private long highest = 0;
    private long seen = 0;

    /** @return {@code true} the first time this seq shows up, {@code false} for a repeat. */
    public synchronized boolean accept(int rawSeq) {
        long seq = Integer.toUnsignedLong(rawSeq);
        if (seq > highest) {
            long shift = seq - highest;
            seen = shift >= WINDOW ? 0 : seen << shift;
            seen |= 1L;
            highest = seq;
            return true;
        }
        long diff = highest - seq;
        if (diff >= WINDOW) {
            return false;
        }
        long bit = 1L << diff;
        if ((seen & bit) != 0) {
            return false;
        }
        seen |= bit;
        return true;
    }
}
