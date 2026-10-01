package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.object.prefab.PrefabType;

import java.util.Optional;

public enum RockRemnantSize {
    SMALL(PrefabType.RockRemnant, PrefabType.MiniRock, null),
    MEDIUM(PrefabType.MediumRockRemnant, PrefabType.RockGolem, PrefabType.RockRemnant);

    private final PrefabType remnantPrefab;
    private final PrefabType summonedPrefab;
    private final PrefabType blastRemainderPrefab;

    RockRemnantSize(
            PrefabType remnantPrefab,
            PrefabType summonedPrefab,
            PrefabType blastRemainderPrefab
    ) {
        this.remnantPrefab = remnantPrefab;
        this.summonedPrefab = summonedPrefab;
        this.blastRemainderPrefab = blastRemainderPrefab;
    }

    public PrefabType remnantPrefab() {
        return remnantPrefab;
    }

    public PrefabType summonedPrefab() {
        return summonedPrefab;
    }

    public Optional<PrefabType> blastRemainderPrefab() {
        return Optional.ofNullable(blastRemainderPrefab);
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
