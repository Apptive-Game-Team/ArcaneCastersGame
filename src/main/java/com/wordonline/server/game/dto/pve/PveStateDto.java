package com.wordonline.server.game.dto.pve;

/**
 * The current value of one PVE state channel, sent to both users when it changes and
 * again to the asking user on every {@code pveSync}. {@code seq} is one counter for the whole match,
 * raised by 1 on every change of any channel, so a client applies a value only when its seq is
 * greater than the last one it applied for that channel. A null {@code value} means the channel's
 * default (for {@code bgm}, the battle scene's default track).
 */
public record PveStateDto(
        String type,
        String channel,
        String value,
        int seq
) {
    public static final String TYPE = "pveState";

    public PveStateDto(String channel, String value, int seq) {
        this(TYPE, channel, value, seq);
    }
}
