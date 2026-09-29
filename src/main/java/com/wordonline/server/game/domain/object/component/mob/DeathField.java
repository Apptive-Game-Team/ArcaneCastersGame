package com.wordonline.server.game.domain.object.component.mob;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.Drop;
import com.wordonline.server.game.domain.object.component.mob.simple.PlayerHealthComponent;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

/**
 * 유닛과 건물이 전투로 죽으면 자기 원소의 필드를 죽은 자리에 주인 편으로 깐다. 필드가 있는 원소가
 * 둘 이상이면 그중 하나를 무작위로 고른다. 바위·바람처럼 필드가 없는 원소만 가진 대상은 아무것도
 * 깔지 않는다.
 */
public final class DeathField {

    private static final Map<ElementType, PrefabType> FIELD_BY_ELEMENT = Map.of(
            ElementType.FIRE, PrefabType.FireField,
            ElementType.WATER, PrefabType.WaterField,
            ElementType.NATURE, PrefabType.LeafField,
            ElementType.LIGHTNING, PrefabType.ElectricField
    );

    // 순서를 고정해 두어야 같은 원소 조합에서 무작위 선택이 매번 같은 후보 목록을 본다.
    private static final List<ElementType> FIELD_ELEMENTS = List.of(
            ElementType.FIRE, ElementType.WATER, ElementType.NATURE, ElementType.LIGHTNING
    );

    private DeathField() {
    }

    static void spawn(GameObject deceased) {
        if (!leavesField(deceased)) {
            return;
        }

        List<ElementType> candidates = FIELD_ELEMENTS.stream()
                .filter(element -> deceased.getElement().nativeHas(element))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        ElementType chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Vector3 fieldPosition = deceased.getPosition().grounded();
        new GameObject(deceased.getMaster(), FIELD_BY_ELEMENT.get(chosen), fieldPosition, deceased.getGameContext());
    }

    // 떨어지는 마법(Drop)도 Mob 이고 플레이어 본체도 Mob 이지만, 둘 다 유닛이나 건물이 아니다.
    private static boolean leavesField(GameObject deceased) {
        return deceased.getComponent(Drop.class) == null
                && deceased.getComponent(PlayerHealthComponent.class) == null;
    }
}
