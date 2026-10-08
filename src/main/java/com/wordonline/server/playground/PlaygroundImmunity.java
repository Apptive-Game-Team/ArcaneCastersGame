package com.wordonline.server.playground;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.DamageImmunity;

final class PlaygroundImmunity extends Component implements DamageImmunity {
    private final PlaygroundLoop loop;

    PlaygroundImmunity(GameObject object, PlaygroundLoop loop) {
        super(object);
        this.loop = loop;
    }

    @Override public boolean isImmuneTo(AttackInfo attack) {
        return attack.getDamage() > 0 && loop.isImmune(gameObject.getMaster());
    }
    @Override public void start() {}
    @Override public void update() {}
    @Override public void onDestroy() {}
}
