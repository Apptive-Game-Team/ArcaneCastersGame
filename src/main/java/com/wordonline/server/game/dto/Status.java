package com.wordonline.server.game.dto;

public enum Status {
    Idle,
    Initializing, Move, Attack, Destroyed, Damaged, Hindered,
    // lethally damaged but not removed yet (aerial mobs falling to the ground)
    Dying,
    Deploying, Deployed, Recovering
}
