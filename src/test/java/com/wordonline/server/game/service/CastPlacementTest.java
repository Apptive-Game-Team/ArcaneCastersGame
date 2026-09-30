package com.wordonline.server.game.service;

import java.util.List;
import java.util.Optional;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class CastPlacementTest {

    @Test
    void groundSummonOverlappingAGroundBodyIsBlocked() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));
        Magic tower = summoning(false);

        // 타워 반경 1 + 유닛 반경 0.5 = 1.5 보다 가까우면 겹친다
        assertThat(CastPlacement.isBlocked(gameContext, tower, new Vector3(6.4f, 0f, 5f))).isTrue();
        assertThat(CastPlacement.isBlocked(gameContext, tower, new Vector3(6.6f, 0f, 5f))).isFalse();
    }

    @Test
    void airborneBodiesNeitherBlockNorAreBlocked() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 3f, 5f), 0.5f)));

        assertThat(CastPlacement.isBlocked(gameContext, summoning(false), new Vector3(5f, 0f, 5f))).isFalse();

        GameContext crowded = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));
        assertThat(CastPlacement.isBlocked(crowded, summoning(true), new Vector3(5f, 0f, 5f))).isFalse();
    }

    @Test
    void magicsThatLeaveNoBodyAreNeverBlocked() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));

        assertThat(CastPlacement.isBlocked(gameContext, mock(Magic.class), new Vector3(5f, 0f, 5f))).isFalse();
    }

    @Test
    void botIsNudgedToTheNearestFreeSpotInRange() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));
        Magic tower = summoning(false);

        Optional<Vector3> spot = CastPlacement.findFreeSpotForBot(
                gameContext, tower, new Vector3(5f, 0f, 5f), new Vector3(1f, 0f, 5f), 9);

        assertThat(spot).isPresent();
        assertThat(CastPlacement.isBlocked(gameContext, tower, spot.get())).isFalse();
    }

    private Magic summoning(boolean airborne) {
        Magic magic = mock(Magic.class, withSettings().extraInterfaces(ObjectSummoningMagic.class));
        ObjectSummoningMagic summoning = (ObjectSummoningMagic) magic;
        when(summoning.summonedPrefab()).thenReturn(PrefabType.GroundTower);
        when(summoning.summonsAirborne()).thenReturn(airborne);
        return magic;
    }

    private GameContext context(List<GameObject> objects) {
        GameContext gameContext = mock(GameContext.class);
        Parameters parameters = mock(Parameters.class);
        when(gameContext.getParameters()).thenReturn(parameters);
        when(parameters.getValueOrDefault(eq("ground_tower"), eq("radius"), anyDouble())).thenReturn(1d);
        when(gameContext.getActiveGameObjects()).thenReturn(objects);
        return gameContext;
    }

    private GameObject body(Vector3 position, float radius) {
        GameObject gameObject = mock(GameObject.class);
        CircleCollider collider = mock(CircleCollider.class);
        when(collider.getRadius()).thenReturn(radius);
        when(gameObject.isActive()).thenReturn(true);
        when(gameObject.getPosition()).thenReturn(position);
        when(gameObject.getComponents(Damageable.class)).thenReturn(List.of(mock(Damageable.class)));
        when(gameObject.getFirstCircleCollider(false)).thenReturn(Optional.of(collider));
        return gameObject;
    }
}
