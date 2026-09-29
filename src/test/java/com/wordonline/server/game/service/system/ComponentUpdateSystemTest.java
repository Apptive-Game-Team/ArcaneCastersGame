package com.wordonline.server.game.service.system;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComponentUpdateSystemTest {

    private static class CountingComponent extends Component {
        int updates;
        private final boolean fails;

        CountingComponent(GameObject gameObject, boolean fails) {
            super(gameObject);
            this.fails = fails;
        }

        @Override
        public void start() {
        }

        @Override
        public void update() {
            updates++;
            if (fails) {
                throw new IllegalStateException("broken component");
            }
        }

        @Override
        public void onDestroy() {
        }
    }

    // One object's broken component must not end the match or starve the objects after it.
    @Test
    void anObjectThatThrowsIsSkippedAndTheOthersStillUpdate() {
        GameContext context = mock(GameContext.class);
        GameObject broken = new GameObject(Master.RightPlayer, PrefabType.ZapMouse, Vector3.ZERO, context);
        GameObject healthy = new GameObject(Master.LeftPlayer, PrefabType.ZapMouse, Vector3.ZERO, context);
        CountingComponent brokenComponent = new CountingComponent(broken, true);
        CountingComponent healthyComponent = new CountingComponent(healthy, false);
        broken.getComponents().add(brokenComponent);
        healthy.getComponents().add(healthyComponent);
        when(context.getGameObjects()).thenReturn(List.of(broken, healthy));

        ComponentUpdateSystem system = new ComponentUpdateSystem();
        assertThatCode(() -> {
            system.update(context);
            system.update(context);
        }).doesNotThrowAnyException();

        assertThat(brokenComponent.updates).isEqualTo(2);
        assertThat(healthyComponent.updates).isEqualTo(2);
    }
}
