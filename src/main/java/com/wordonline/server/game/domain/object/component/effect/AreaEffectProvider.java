package com.wordonline.server.game.domain.object.component.effect;

import java.util.List;

import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.dto.Effect;

public class AreaEffectProvider extends Component {

    private final float interval;
    private final float radius;
    private final Effect effect;

    private float counter = 0;

    public AreaEffectProvider(GameObject gameObject, float interval, float radius, Effect effect) {
        super(gameObject);
        this.interval = interval;
        this.radius = radius;
        this.effect = effect;
    }

    @Override
    public void start() {
        gameObject.drawCircle(Vector3.ZERO, radius, GizmoCategory.AreaOfEffect);
    }

    @Override
    public void update() {
        if (counter < interval) {
            counter += getGameContext().getDeltaTime();
            return;
        }

        counter = 0;
        List<GameObject> gameObjects = getGameContext().overlapSphereAll(gameObject, radius);
        gameObjects.stream()
                .filter(target -> target.getComponent(EffectReceiver.class) != null)
                .forEach(target -> target.getComponent(EffectReceiver.class)
                        .onReceive(new EffectApplication(effect, gameObject)));
    }

    @Override
    public void onDestroy() {

    }
}
