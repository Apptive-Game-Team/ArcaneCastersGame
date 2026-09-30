package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.object.prefab.PrefabType;

import java.util.Optional;

public enum RockRemnantSize {
    SMALL(PrefabType.RockRemnant, PrefabType.MiniRock),
    MEDIUM(PrefabType.MediumRockRemnant, PrefabType.RockGolem);

    private final PrefabType remnantPrefab;
    private final PrefabType summonedPrefab;

    RockRemnantSize(PrefabType remnantPrefab, PrefabType summonedPrefab) {
        this.remnantPrefab = remnantPrefab;
        this.summonedPrefab = summonedPrefab;
    }

    public PrefabType remnantPrefab() {
        return remnantPrefab;
    }

    public PrefabType summonedPrefab() {
        return summonedPrefab;
    }

    public static Optional<RockRemnantSize> fromPrefab(PrefabType prefabType) {
        for (RockRemnantSize size : values()) {
            if (size.remnantPrefab == prefabType) {
                return Optional.of(size);
            }
        }
        return Optional.empty();
    }
}
