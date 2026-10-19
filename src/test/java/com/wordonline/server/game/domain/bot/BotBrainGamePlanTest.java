package com.wordonline.server.game.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

/**
 * {@link BotCapability#SUMMON_RELEVANCE} and {@link BotCapability#GAME_PLAN}, at zero noise. The
 * magics carry real names, since {@link CardRole} knows a card by its name.
 */
class BotBrainGamePlanTest {

    private static final Vector3 OWN_BASE = new Vector3(1, 0, 5);

    private final Map<String, Double> values = new HashMap<>();
    private final Parameters parameters = mock(Parameters.class);
    private final DatabaseMagicParser parser = mock(DatabaseMagicParser.class);
    private final BotCounterEvaluator counterEvaluator = mock(BotCounterEvaluator.class);

    @BeforeEach
    void setUp() {
        when(parameters.getValueOrDefault(anyString(), anyString(), anyDouble())).thenAnswer(invocation ->
                values.getOrDefault(invocation.getArgument(0) + "." + invocation.getArgument(1),
                        invocation.<Double>getArgument(2)));
        values.put("player.max_mana", 120.0);
    }

    @Test
    void anAntiAirUnitWaitsForAnEnemyFlyer() {
        Magic windSpirit = summon(1, "wind_spirit", PrefabType.WindSpirit, 15);
        catalogue(windSpirit);
        BotEye noFlyer = new BotEye(List.of(), hand(windSpirit), 20, 1000);
        values.put("thunder_bird.spawn_height", 3.0);
        BotEye flyer = new BotEye(List.of(enemy(PrefabType.ThunderBird, new Vector3(14, 0, 5))), hand(windSpirit), 20, 1000);

        assertThat(brain(skill()).think(noFlyer, parameters, Master.LeftPlayer).magicId()).isEqualTo(windSpirit.id);
        assertThat(brain(skill(BotCapability.SUMMON_RELEVANCE)).think(noFlyer, parameters, Master.LeftPlayer)).isNull();
        assertThat(brain(skill(BotCapability.SUMMON_RELEVANCE)).think(flyer, parameters, Master.LeftPlayer).magicId())
                .isEqualTo(windSpirit.id);
    }

    @Test
    void aPusherWaitsForAnEnemyOnOurHalfAndManaNearTheCapStillGoesToIt() {
        Magic totem = summon(1, "wind_totem", PrefabType.WindTotem, 15);
        catalogue(totem);
        BotEye farEnemy = new BotEye(List.of(enemy(PrefabType.FireSpirit, new Vector3(15, 0, 5))), hand(totem), 20, 1000);
        BotEye nearEnemy = new BotEye(List.of(enemy(PrefabType.FireSpirit, new Vector3(6, 0, 5))), hand(totem), 20, 1000);
        BotEye fullMana = new BotEye(List.of(), hand(totem), 115, 1000);
        BotBrain brain = brain(skill(BotCapability.SUMMON_RELEVANCE));

        assertThat(brain.think(farEnemy, parameters, Master.LeftPlayer)).isNull();
        assertThat(brain.think(nearEnemy, parameters, Master.LeftPlayer).magicId()).isEqualTo(totem.id);
        assertThat(brain.think(fullMana, parameters, Master.LeftPlayer).magicId()).isEqualTo(totem.id);
    }

    @Test
    void aFullHandCastsAUselessSummonToFreeItsSlot() {
        Magic windSpirit = summon(1, "wind_spirit", PrefabType.WindSpirit, 15);
        catalogue(windSpirit);
        List<Long> sixCards = List.of(windSpirit.id, windSpirit.id, windSpirit.id, windSpirit.id, windSpirit.id,
                windSpirit.id);
        BotEye fullHand = new BotEye(List.of(), sixCards, 20, 1000);

        assertThat(brain(skill(BotCapability.SUMMON_RELEVANCE)).think(fullHand, parameters, Master.LeftPlayer)
                .magicId()).isEqualTo(windSpirit.id);
    }

    @Test
    void aDearFighterInHandMakesTheBotSaveForTheWholeWave() {
        Magic ent = summon(1, "evil_ent", PrefabType.EvilEnt, 80);
        Magic embers = summon(2, "ember_spirit_swarm", PrefabType.FireSlime, 15);
        catalogue(ent, embers);
        BotBrain plain = brain(skill());
        BotBrain planner = brain(skill(BotCapability.GAME_PLAN));

        BotEye short_ = new BotEye(List.of(), hand(ent, embers), 20, 1000);
        assertThat(plain.think(short_, parameters, Master.LeftPlayer).magicId()).isEqualTo(embers.id);
        assertThat(planner.think(short_, parameters, Master.LeftPlayer)).as("saving for 95").isNull();

        BotEye enough = new BotEye(List.of(), hand(ent, embers), 95, 1000);
        BotBrain.InputDecision first = planner.think(enough, parameters, Master.LeftPlayer);
        assertThat(first.ruleId()).isEqualTo(GamePlanner.WAVE_RULE);
        assertThat(first.magicId()).isEqualTo(ent.id);
    }

    @Test
    void aFighterBelowTheWaveCostIsNotSavedFor() {
        Magic thunderSpirit = summon(1, "thunder_spirit", PrefabType.ThunderSpirit, 35);
        catalogue(thunderSpirit);
        BotEye eye = new BotEye(List.of(), hand(thunderSpirit), 20, 1000);

        assertThat(brain(skill(BotCapability.GAME_PLAN)).think(eye, parameters, Master.LeftPlayer))
                .as("nothing affordable, and no plan holds it").isNull();
        assertThat(GamePlanner.WAVE_CARD_COST).isGreaterThan(35);
    }

    @Test
    void aFighterStillNearOurBaseIsAWaveTheNextFighterJoins() {
        Magic ent = summon(1, "evil_ent", PrefabType.EvilEnt, 80);
        Magic embers = summon(2, "ember_spirit_swarm", PrefabType.FireSlime, 15);
        catalogue(ent, embers);
        values.put("evil_ent.speed", 1.0);
        Vector3 staged = new Vector3(4, 0, 6);
        BotVisibleObject ours = new BotVisibleObject(30, Master.LeftPlayer, PrefabType.EvilEnt, staged, 900, true, true);
        BotEye eye = new BotEye(List.of(ours), hand(ent, embers), 15, 1000);

        BotBrain.InputDecision joined = brain(skill(BotCapability.GAME_PLAN)).think(eye, parameters, Master.LeftPlayer);

        assertThat(joined.ruleId()).isEqualTo(GamePlanner.WAVE_RULE);
        assertThat(joined.magicId()).isEqualTo(embers.id);
        assertThat(joined.target()).isEqualTo(staged);
    }

    @Test
    void aDeckWithoutEnoughDearFightersNeverSaves() {
        Magic ent = summon(1, "evil_ent", PrefabType.EvilEnt, 80);
        Magic embers = summon(2, "ember_spirit_swarm", PrefabType.FireSlime, 15);
        catalogue(ent, embers);
        BotEye eye = new BotEye(List.of(), hand(ent, embers), 20, 1000);
        List<Long> oneEnt = List.of(ent.id, embers.id, embers.id, embers.id);
        List<Long> threeEnts = List.of(ent.id, ent.id, ent.id, embers.id);

        assertThat(brain(skill(BotCapability.GAME_PLAN), oneEnt).think(eye, parameters, Master.LeftPlayer).magicId())
                .as("one dear fighter in the deck: cast on sight").isEqualTo(embers.id);
        assertThat(brain(skill(BotCapability.GAME_PLAN), threeEnts).think(eye, parameters, Master.LeftPlayer))
                .as("a deck built for waves saves").isNull();
    }

    @Test
    void everyFighterRowIsAKnownRole() {
        assertThat(CardRole.of("wind_spirit")).isEqualTo(CardRole.ANTI_AIR);
        assertThat(CardRole.of("thunder_bird_swarm")).isEqualTo(CardRole.FIGHTER);
        assertThat(CardRole.of("no_such_magic")).isNull();
        assertThat(CardRole.table()).hasSize(44);
    }

    private BotBrain brain(BotSkill skill) {
        return brain(skill, List.of());
    }

    private BotBrain brain(BotSkill skill, List<Long> deck) {
        BotPersona persona = new BotPersona(-1, "Test", BotTier.ELITE, 0, 1, 0.0, true, false, BotTemperament.WARM);
        return new BotBrain(parser, counterEvaluator, persona, HospitalityDirector.DEFAULT_BOARD_SHARE, skill,
                () -> deck);
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

    private void catalogue(Magic... magics) {
        for (Magic magic : magics) {
            when(parser.getMagic(magic.id)).thenReturn(magic);
        }
        when(parser.getAllMagics()).thenReturn(List.of(magics));
    }

    private static List<Long> hand(Magic... magics) {
        return List.of(magics).stream().map(magic -> magic.id).toList();
    }

    private static BotVisibleObject enemy(PrefabType type, Vector3 position) {
        return new BotVisibleObject(7, Master.RightPlayer, type, position, 50, true, true);
    }
}
