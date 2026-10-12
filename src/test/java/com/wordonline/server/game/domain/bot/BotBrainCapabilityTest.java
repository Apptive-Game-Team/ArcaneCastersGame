package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Each capability changes the decision it is about, and only when the bot has it. Every case runs
 * with zero noise, so what is asserted is the score and not a coin flip.
 */
class BotBrainCapabilityTest {

    private static final Vector3 ENEMY_CORE = new Vector3(17, 0, 5);

    private final Map<String, Double> values = new HashMap<>();
    private final Parameters parameters = mock(Parameters.class);
    private final DatabaseMagicParser parser = mock(DatabaseMagicParser.class);
    private final BotCounterEvaluator counterEvaluator = mock(BotCounterEvaluator.class);

    @BeforeEach
    void setUp() {
        when(parameters.getValueOrDefault(anyString(), anyString(), anyDouble())).thenAnswer(invocation ->
                values.getOrDefault(invocation.getArgument(0) + "." + invocation.getArgument(1),
                        invocation.<Double>getArgument(2)));
    }

    // 소환 가치가 고정이면 비용으로 나누는 순간 가장 싼 소환이 항상 이긴다.
    @Test
    void summonStrengthPicksTheSummonThatBuysMoreFightPerMana() {
        Magic cheap = summon(1, "cheap", PrefabType.FireSpirit, 3);
        Magic big = summon(2, "big", PrefabType.WaterSlime, 9);
        values.put("fire_spirit.hp", 10.0);
        values.put("fire_spirit.damage", 10.0);
        values.put("water_slime.hp", 160.0);
        values.put("water_slime.damage", 10.0);
        catalogue(cheap, big);
        BotEye eye = new BotEye(List.of(), hand(cheap, big), 20, 100);

        assertThat(brain(skill()).think(eye, parameters, Master.LeftPlayer).magicId()).isEqualTo(cheap.id);
        assertThat(brain(skill(BotCapability.SUMMON_STRENGTH)).think(eye, parameters, Master.LeftPlayer).magicId())
                .isEqualTo(big.id);
    }

    @Test
    void lethalFinishesTheEnemyPlayerBeforeAnythingElse() {
        Magic finisher = offensive(1, "finisher", 4, 6);
        Magic cheapSummon = summon(2, "cheap_summon", PrefabType.FireSpirit, 1);
        catalogue(finisher, cheapSummon);
        BotEye eye = new BotEye(List.of(enemyCore()), hand(finisher, cheapSummon), 20, 6);

        BotBrain.InputDecision without = brain(skill()).think(eye, parameters, Master.LeftPlayer);
        BotBrain.InputDecision with = brain(skill(BotCapability.LETHAL)).think(eye, parameters, Master.LeftPlayer);

        assertThat(without.magicId()).isEqualTo(cheapSummon.id);
        assertThat(with.ruleId()).isEqualTo(LethalRule.RULE_ID);
        assertThat(with.magicId()).isEqualTo(finisher.id);
        assertThat(with.target()).isEqualTo(ENEMY_CORE);
    }

    // 파라미터에 피해량이 없으면 추정치로는 막타를 노리지 않는다.
    @Test
    void lethalNeedsADamageTheParametersActuallyState() {
        Magic guessed = offensive(1, "guessed", 4, 0);
        catalogue(guessed);
        BotEye eye = new BotEye(List.of(enemyCore()), hand(guessed), 20, 1);

        BotBrain.InputDecision decision = brain(skill(BotCapability.LETHAL)).think(eye, parameters, Master.LeftPlayer);

        assertThat(decision.ruleId()).isNotEqualTo(LethalRule.RULE_ID);
    }

    @Test
    void manaDisciplineSavesForAClearlyBetterCardWhileCalm() {
        values.put("player.max_mana", 10.0);
        Magic small = summon(1, "small", PrefabType.FireSpirit, 4);
        Magic blast = offensive(2, "blast", 8, 40);
        catalogue(small, blast);
        BotEye eye = new BotEye(List.of(distantEnemy(40)), hand(small, blast), 4, 100);

        assertThat(brain(skill()).think(eye, parameters, Master.LeftPlayer).magicId()).isEqualTo(small.id);
        assertThat(brain(skill(BotCapability.MANA_DISCIPLINE)).think(eye, parameters, Master.LeftPlayer)).isNull();
    }

    @Test
    void manaDisciplineNeverWaitsForACardTheBotCanNeverAfford() {
        values.put("player.max_mana", 6.0);
        Magic small = summon(1, "small", PrefabType.FireSpirit, 4);
        Magic blast = offensive(2, "blast", 8, 40);
        catalogue(small, blast);
        BotEye eye = new BotEye(List.of(distantEnemy(40)), hand(small, blast), 4, 100);

        assertThat(brain(skill(BotCapability.MANA_DISCIPLINE)).think(eye, parameters, Master.LeftPlayer).magicId())
                .isEqualTo(small.id);
    }

    @Test
    void manaDisciplineDoesNotHoldOutWhenOverdue() {
        values.put("player.max_mana", 10.0);
        Magic small = summon(1, "small", PrefabType.FireSpirit, 4);
        Magic blast = offensive(2, "blast", 8, 40);
        catalogue(small, blast);
        BotEye eye = new BotEye(List.of(distantEnemy(40)), hand(small, blast), 4, 100);

        assertThat(brain(skill(BotCapability.MANA_DISCIPLINE)).think(eye, parameters, Master.LeftPlayer, true).magicId())
                .isEqualTo(small.id);
    }

    // 봇은 결정한 뒤 thinkingTime 만큼 기다렸다가 시전한다. 그동안 적은 계속 걷는다.
    @Test
    void leadTargetingAimsWhereAMovingEnemyWillBe() {
        Magic shot = offensive(1, "shot", 1, 5);
        catalogue(shot);
        BotBrain leading = brain(skill(BotCapability.LEAD_TARGETING));
        BotBrain plain = brain(skill());

        BotEye before = new BotEye(List.of(walker(new Vector3(10, 0, 5))), hand(shot), 20, 100, 1.0);
        BotEye after = new BotEye(List.of(walker(new Vector3(9, 0, 5))), hand(shot), 20, 100, 1.5);
        leading.think(before, parameters, Master.LeftPlayer);
        plain.think(before, parameters, Master.LeftPlayer);

        float lead = (float) (-2 * BotBrain.CAST_RESOLUTION_SECONDS);
        assertThat(leading.think(after, parameters, Master.LeftPlayer).target()).isEqualTo(new Vector3(9 + lead, 0, 5));
        assertThat(plain.think(after, parameters, Master.LeftPlayer).target()).isEqualTo(new Vector3(9, 0, 5));
    }

    private BotBrain brain(BotSkill skill) {
        BotPersona persona = new BotPersona(-1, "Test", BotTier.ELITE, 0, 1, 0.0, true, false, BotTemperament.WARM);
        return new BotBrain(parser, counterEvaluator, persona, HospitalityDirector.DEFAULT_BOARD_SHARE, skill);
    }

    private static BotSkill skill(BotCapability... capabilities) {
        return new BotSkill(0.0, Set.of(capabilities));
    }

    private Magic summon(long id, String name, PrefabType prefab, double cost) {
        Magic magic = BotBrainTestFixtures.summon(id, name, prefab, 1);
        values.put(name + ".mana_cost", cost);
        values.put(name + ".range", 10.0);
        return magic;
    }

    private Magic offensive(long id, String name, double cost, double damage) {
        Magic magic = BotBrainTestFixtures.offensive(id, name);
        values.put(name + ".mana_cost", cost);
        values.put(name + ".range", 20.0);
        values.put(name + ".radius", 1.0);
        if (damage > 0) {
            values.put(name + ".damage", damage);
        }
        return magic;
    }

    private void catalogue(Magic... magics) {
        for (Magic magic : magics) {
            when(parser.getMagic(magic.id)).thenReturn(magic);
        }
        when(parser.getAllMagics()).thenReturn(List.of(magics));
    }

    private static List<Long> hand(Magic... magics) {
        return List.of(magics).stream().map(magic -> magic.id).toList();
    }

    private static BotVisibleObject enemyCore() {
        return new BotVisibleObject(100, Master.RightPlayer, PrefabType.Player, ENEMY_CORE, 6, false, true);
    }

    // Beyond the pressure horizon, so the bot is calm.
    private static BotVisibleObject distantEnemy(int hp) {
        return new BotVisibleObject(7, Master.RightPlayer, PrefabType.FireSpirit, new Vector3(15, 0, 5), hp, true, true);
    }

    private static BotVisibleObject walker(Vector3 position) {
        return new BotVisibleObject(7, Master.RightPlayer, PrefabType.FireSpirit, position, 10, true, true);
    }
}
