package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.Spawner;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PVEBossMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimplePveBossInitializerTest {

    // Damage finds its target with getComponent(Mob.class). A boss also carries a Spawner, which
    // is a Mob that ignores damage, so the boss mob must be the first Mob or the boss is immortal.
    @Test
    void damageReachesTheBossMobNotTheSpawner() {
        Parameters parameters = mock(Parameters.class);
        when(parameters.getValue("pve_nature_slime_nest", "mass")).thenReturn(1_000_000d);
        when(parameters.getValue("pve_nature_slime_nest", "radius")).thenReturn(1d);
        when(parameters.getValue("pve_nature_slime_nest", "hp")).thenReturn(1000d);

        GameObject nest = new GameObject(Master.RightPlayer, PrefabType.PveNatureSlimeNest, Vector3.ZERO,
                mock(GameContext.class));
        new PveNatureSlimeNestPrefabInitializer(parameters).initialize(nest);
        nest.flushComponents();

        assertThat(nest.getComponent(Spawner.class)).isNotNull();
        // PhysicSystem only pairs objects that carry a Collidable; projectiles miss a boss without one.
        assertThat(nest.getComponents(com.wordonline.server.game.domain.object.component.physic.Collidable.class)).isNotEmpty();
        Mob target = nest.getComponent(Mob.class);
        assertThat(target).isInstanceOf(PVEBossMob.class);

        target.applyDamage(new AttackInfo(100, ElementType.NONE));
        assertThat(nest.getComponent(PVEBossMob.class).getHp()).isEqualTo(900);

        // The client's hp bar is the HP gauge; the Spawner's 0/0 must not replace the boss's.
        var hpGauges = com.wordonline.server.game.util.GaugeExtractor.extractGaugeDto(nest).stream()
                .filter(g -> g.category() == com.wordonline.server.game.dto.frame.GaugeCategory.HP)
                .toList();
        assertThat(hpGauges).hasSize(1);
        assertThat(hpGauges.get(0).value()).isEqualTo(900f);
        assertThat(hpGauges.get(0).maxValue()).isEqualTo(1000f);
    }
}
