package com.wordonline.server.game.domain.pve;

// Switches the match's background music to the track bgmKey names. A null bgmKey returns to the
// battle scene's default track. Sets the "bgm" PVE state channel, so a client that reconnects
// later still gets the current track.
public record PveSetBgmAction(String bgmKey) implements PveScenarioAction {
}
