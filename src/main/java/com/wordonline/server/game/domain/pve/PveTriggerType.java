package com.wordonline.server.game.domain.pve;

public enum PveTriggerType {
    // frame number >= trigger_value (20 frames = 1 second); target_installer_id unused
    FrameNumGte,
    // elapsed seconds >= trigger_value; target_installer_id unused
    SecondsGte,
    // target installer's hp <= trigger_value percent of its max hp, and it is still alive
    InstallerHpPercentLte,
    // target installer's object is terminal (destroyed or hp <= 0); trigger_value ignored
    InstallerDestroyed
}
