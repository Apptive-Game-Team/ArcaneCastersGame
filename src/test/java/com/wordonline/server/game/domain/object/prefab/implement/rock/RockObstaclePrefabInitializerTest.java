package com.wordonline.server.game.domain.object.prefab.implement.rock;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.StaticObstacle;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RockObstaclePrefabInitializerTest {

    @Test
    void advertisesRockObstaclePrefabType() {
        assertThat(new RockObstaclePrefabInitializer().prefabType).isEqualTo(PrefabType.RockObstacle);
        assertThat(PrefabType.RockObstacle.getBeanName()).isEqualTo("rock_obstacle_prefab");
    }

    @Test
    void hasASolidImmovableCircleCollider() {
        GameObject rock = initializedRock();

        CircleCollider collider = rock.getFirstCircleCollider().orElseThrow();
        assertThat(collider.getRadius()).isEqualTo(0.6f);
        assertThat(collider.isTrigger()).isFalse();
        assertThat(collider.getInvMass()).isZero();
        assertThat(components(rock)).noneMatch(RigidBody.class::isInstance);
    }

    @Test
    void blocksGroundPathWithAStaticObstacle() {
        GameObject rock = initializedRock();

        assertThat(components(rock)).anyMatch(StaticObstacle.class::isInstance);
        assertThat(rock.getElement().nativeHas(ElementType.ROCK)).isTrue();
    }

    @Test
    void cannotBeHurtActOrExpire() {
        GameObject rock = initializedRock();

        assertThat(components(rock)).noneMatch(Damageable.class::isInstance);
        assertThat(components(rock)).noneMatch(Mob.class::isInstance);
        assertThat(components(rock)).noneMatch(TimedSelfDestroyer.class::isInstance);
    }

    private GameObject initializedRock() {
        GameObject rock = new GameObject(
                Master.None, PrefabType.RockObstacle, Vector3.ZERO, mock(GameContext.class));
        new RockObstaclePrefabInitializer().initialize(rock);
        return rock;
    }

    private Stream<Component> components(GameObject gameObject) {
        return Stream.concat(
                gameObject.getComponents().stream(),
                gameObject.getComponentsToAdd().stream());
    }
}
