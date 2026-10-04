package com.wordonline.server.game.domain.pve;

// installerId takes no damage while at least one of its source installers is installed and not
// destroyed. A scenario may list several rows for one installerId, one per source.
public record PveShield(
        String installerId,
        String sourceInstallerId
) {
}
