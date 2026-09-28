package com.wordonline.server.game.domain.pve;

// One row of pve_scenario_event_actions. Every object an action creates belongs to RightPlayer.
public sealed interface PveScenarioAction
        permits PveSpawnWaveAction, PveInstallObjectAction, PveSetSpawnerAction {
}
