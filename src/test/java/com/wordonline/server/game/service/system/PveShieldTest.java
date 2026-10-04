package com.wordonline.server.game.service.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.DOTStatusEffect;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.PveShieldComponent;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.pve.PveInstallObject;
import com.wordonline.server.game.domain.pve.PveInstallObjectAction;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.domain.pve.PveScenario;
import com.wordonline.server.game.domain.pve.PveScenarioEvent;
import com.wordonline.server.game.domain.pve.PveScenarioRules;
import com.wordonline.server.game.domain.pve.PveShield;
import com.wordonline.server.game.domain.pve.PveTriggerType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PveShieldTest {

    private final List<GameObject> world = new ArrayList<>();
    private final GameContext context = mock(GameContext.class);
    private final PveScenarioInstaller installer = new PveScenarioInstaller();

    private static class TestMob extends Mob {
        private TestMob(GameObject gameObject, int maxHp) {
            super(gameObject, maxHp, 0);
        }

        @Override
        public void onDeath() {
        }

        @Override
        public void start() {
        }

        @Override
        public void onDestroy() {
        }
    }

    PveShieldTest() {
        when(context.getSessionObject()).thenReturn(mock(SessionObject.class));
        when(context.getGameObjects()).thenReturn(world);
        doAnswer(invocation -> {
            GameObject gameObject = invocation.getArgument(0);
            gameObject.addComponent(new TestMob(gameObject, 100));
            gameObject.flushComponents();
            gameObject.setStatus(Status.Idle);
            world.add(gameObject);
            return null;
        }).when(context).createGameObject(any());
        installer.install(List.of(), context);
    }

    private GameObject install(String installerId) {
        return installer.installOne(
                new PveInstallObject(installerId, PrefabType.ZapMouse, Master.RightPlayer, Vector3.ZERO, null),
                context);
    }

    private PveScriptSystem system(List<PveShield> shields, List<PveScenarioEvent> events) {
        PveScriptSystem system = new PveScriptSystem();
        system.setScenario(new PveScenario(List.of("boss"), List.of(), events, PveScenarioRules.defaultRules(), shields));
        system.setInstaller(installer);
        system.setRuntime(installer.getRuntime());
        return system;
    }

    // One frame: the system attaches components, then the world flushes the new component.
    private void tick(PveScriptSystem system) {
        system.update(context);
        world.forEach(GameObject::flushComponents);
        world.forEach(object -> object.getComponents(PveShieldComponent.class).forEach(PveShieldComponent::update));
    }

    private static int hp(GameObject gameObject) {
        return gameObject.getComponent(Mob.class).getHp();
    }

    private static void hit(GameObject gameObject, int damage) {
        gameObject.getComponent(Mob.class).onDamaged(new AttackInfo(damage, ElementType.NONE));
    }

    private void destroy(GameObject gameObject) {
        gameObject.destroy();
        world.remove(gameObject);
    }

    @Test
    void shieldIsDownWhileNoSourceHasBeenInstalled() {
        GameObject boss = install("boss");
        PveScriptSystem system = system(List.of(new PveShield("boss", "pillar")), List.of());
        tick(system);

        hit(boss, 10);

        assertThat(hp(boss)).isEqualTo(90);
        assertThat(boss.getEffects()).doesNotContain(Effect.Bubble);
    }

    @Test
    void shieldBlocksEveryDamagePathWhileASourceIsAlive() {
        GameObject boss = install("boss");
        install("pillar");
        PveScriptSystem system = system(List.of(new PveShield("boss", "pillar")), List.of());
        tick(system);

        hit(boss, 10);                                                       // direct and area hits
        boss.getComponent(Mob.class).onDamaged(new AttackInfo(10, ElementType.FIRE), 0.5f);
        boss.getComponent(Mob.class).applyDamage(new AttackInfo(10, ElementType.FIRE)); // DOT, Snared, fall
        when(context.getDeltaTime()).thenReturn(1f);
        DOTStatusEffect burn = new DOTStatusEffect(boss, 1f, 10, ElementType.FIRE, StatusEffectKey.DOTDeal_Burn);
        burn.update();

        assertThat(hp(boss)).isEqualTo(100);
        assertThat(boss.getEffects()).contains(Effect.Bubble);
    }

    @Test
    void healingIsNotBlocked() {
        GameObject boss = install("boss");
        install("pillar");
        PveScriptSystem system = system(List.of(new PveShield("boss", "pillar")), List.of());
        tick(system);
        boss.getComponent(Mob.class).drainHpAboveFraction(0.5f);
        assertThat(hp(boss)).isEqualTo(50);

        boss.getComponent(Mob.class).applyDamage(new AttackInfo(-5, ElementType.NONE));

        assertThat(hp(boss)).isEqualTo(55);
    }

    @Test
    void destroyingTheOnlySourceDropsTheShieldAndBossCanBeKilled() {
        GameObject boss = install("boss");
        GameObject pillar = install("pillar");
        PveScriptSystem system = system(List.of(new PveShield("boss", "pillar")), List.of());
        tick(system);
        hit(boss, 10);
        assertThat(hp(boss)).isEqualTo(100);

        destroy(pillar);
        tick(system);
        hit(boss, 30);

        assertThat(hp(boss)).isEqualTo(70);
        assertThat(boss.getEffects()).doesNotContain(Effect.Bubble);
        hit(boss, 70);
        assertThat(hp(boss)).isLessThanOrEqualTo(0);
    }

    @Test
    void aSourceInstalledLaterByAnInstallObjectActionRaisesTheShieldAgain() {
        GameObject boss = install("boss");
        GameObject pillar = install("pillar");
        var respawn = new PveScenarioEvent("respawn", PveTriggerType.InstallerDestroyed, 0, "pillar", null, "k",
                List.of(), List.of(new PveInstallObjectAction("pillar", PrefabType.ZapMouse, 0, 0, null)));
        PveScriptSystem system = system(List.of(new PveShield("boss", "pillar")), List.of(respawn));
        tick(system);

        destroy(pillar);
        // The destroyed trigger and its InstallObject action run in this same update, so the new
        // pillar is installed before the next damage attempt.
        tick(system);
        assertThat(installer.getInstalledObject(context, "pillar")).isNotSameAs(pillar);
        hit(boss, 10);

        assertThat(hp(boss)).isEqualTo(100);
        assertThat(boss.getEffects()).contains(Effect.Bubble);
    }

    @Test
    void shieldStaysUpWhileAnotherSourceIsAlive() {
        GameObject boss = install("boss");
        GameObject pillarA = install("pillar_a");
        install("pillar_b");
        PveScriptSystem system = system(List.of(
                new PveShield("boss", "pillar_a"), new PveShield("boss", "pillar_b")), List.of());
        tick(system);

        destroy(pillarA);
        tick(system);
        hit(boss, 10);

        assertThat(hp(boss)).isEqualTo(100);
        assertThat(boss.getEffects()).contains(Effect.Bubble);
    }

    @Test
    void aSourceThatIsAnObjectiveItselfCanBeDamagedAndTheShieldedBossStillCounts() {
        GameObject boss = install("boss");
        GameObject pillar = install("pillar");
        PveScriptSystem system = system(List.of(new PveShield("boss", "pillar")), List.of());
        tick(system);

        hit(pillar, 10);
        assertThat(hp(pillar)).isEqualTo(90);
        assertThat(PveObjectiveTarget.isTerminal(boss)).isFalse();
        hit(boss, 50);
        assertThat(PveObjectiveTarget.isTerminal(boss)).isFalse();
        assertThat(hp(boss)).isEqualTo(100);
    }

    @Test
    void scenarioWithoutShieldRowsTakesDamageAsBefore() {
        GameObject boss = install("boss");
        install("pillar");
        PveScriptSystem system = system(List.of(), List.of());
        tick(system);

        hit(boss, 10);
        boss.getComponent(Mob.class).applyDamage(new AttackInfo(10, ElementType.NONE));

        assertThat(hp(boss)).isEqualTo(80);
        assertThat(boss.getEffects()).doesNotContain(Effect.Bubble);
    }
}
