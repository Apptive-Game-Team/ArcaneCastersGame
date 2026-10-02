package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.CombatDeathListener;
import com.wordonline.server.game.domain.object.component.Component;

public class RockDeathRemnant extends Component implements CombatDeathListener {
    private final RockRemnantSize size;
    private boolean consumed;

    public RockDeathRemnant(GameObject gameObject) {
        this(gameObject, RockRemnantSize.SMALL);
    }

    public RockDeathRemnant(GameObject gameObject, RockRemnantSize size) {
        super(gameObject);
        this.size = size;
    }

    @Override
    public void onCombatDeath() {
        if (consumed) {
            return;
        }
        consumed = true;
        new GameObject(
                gameObject.getMaster(),
                size.remnantPrefab(),
                new Vector3(gameObject.getPosition()),
                getGameContext()
        );
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
    }

    @Override
    public void onDestroy() {
    }
}
