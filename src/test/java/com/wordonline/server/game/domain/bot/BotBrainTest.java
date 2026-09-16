package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sign of {@code counterAggression} decides which direction of the matchup the brain scores.
 * Both cards here cost the same and place one body, so the counter term is the only thing that can
 * separate them. Every price is read under the magic's own name.
 */
class BotBrainTest {

    private static final Magic FAVOURABLE =
            BotBrainTestFixtures.summon(1, "favourable", PrefabType.FireSpirit, 1);
    private static final Magic LOSING =
            BotBrainTestFixtures.summon(2, "losing", PrefabType.FireSpirit, 1);
    private static final Magic SHOT =
            BotBrainTestFixtures.offensive(3, "shot");
    // 적 필드에 서 있는 유닛의 값을 정하는 마법. 봇이 낼 수 있는 5마나짜리보다 비싸야
    // "필드보다 싼 것만 낸다" 규칙이 후보를 남긴다.
    private static final Magic ENEMY_BOARD =
            BotBrainTestFixtures.summon(4, "enemy_board", PrefabType.FireSpirit, 1);

    private final DatabaseMagicParser magicParser = mock(DatabaseMagicParser.class);
    private final BotCounterEvaluator counterEvaluator = mock(BotCounterEvaluator.class);
    private final Parameters parameters = mock(Parameters.class);

    @BeforeEach
    void setUp() {
        stubHand(FAVOURABLE, LOSING, SHOT, ENEMY_BOARD);
        priceEnemyBoardWith(ENEMY_BOARD);
        when(parameters.getValueOrDefault(anyString(), anyString(), anyDouble()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        manaCost(FAVOURABLE, 5.0);
        manaCost(LOSING, 5.0);
        manaCost(SHOT, 5.0);
        manaCost(ENEMY_BOARD, 25.0);

        when(counterEvaluator.evaluate(eq(FAVOURABLE), any())).thenReturn(10.0);
        when(counterEvaluator.evaluate(eq(LOSING), any())).thenReturn(0.0);
        when(counterEvaluator.evaluateVulnerability(eq(FAVOURABLE), any())).thenReturn(0.0);
        when(counterEvaluator.evaluateVulnerability(eq(LOSING), any())).thenReturn(10.0);
    }

    @Test
    void positiveAggressionPicksThePlayThatBeatsTheField() {
        BotBrain.InputDecision decision = think(persona(1.0), hand(FAVOURABLE, LOSING));

        assertThat(decision).isNotNull();
        assertThat(decision.magicId()).isEqualTo(FAVOURABLE.id);
    }

    @Test
    void negativeAggressionPicksThePlayTheFieldAnswersBest() {
        BotBrain.InputDecision decision = think(persona(-1.0), hand(FAVOURABLE, LOSING));

        assertThat(decision).isNotNull();
        assertThat(decision.magicId()).isEqualTo(LOSING.id);
    }

    // A bot that is meant to lose still commits a unit every time. Choosing nothing, or choosing
    // something irrelevant, is what reads to the player as being humoured.
    @Test
    void negativeAggressionStillCasts() {
        assertThat(think(persona(-1.0), hand(FAVOURABLE, LOSING))).isNotNull();
    }

    @Test
    void neutralAggressionAsksForNeitherDirection() {
        think(persona(0.0), hand(FAVOURABLE, LOSING));

        verify(counterEvaluator, never()).evaluate(any(), any());
        verify(counterEvaluator, never()).evaluateVulnerability(any(), any());
    }

    // The hospitality bot's deck cannot hold anything but summons, but a hand is dealt from a deck
    // rather than fixed by it, so the code filter is what actually holds the rule.
    @Test
    void hospitalityNeverCastsANonSummon() {
        BotBrain.InputDecision decision = think(hospitalityPersona(), hand(SHOT, LOSING));

        assertThat(decision).isNotNull();
        assertThat(decision.magicId()).isEqualTo(LOSING.id);
    }

    // Its stored aggression is overridden per cast by the board state, so an unfavourable persona
    // value cannot make it start playing to win.
    @Test
    void hospitalityPrefersThePlayTheFieldAnswersEvenWithAPositivePersonaValue() {
        BotBrain.InputDecision decision = think(
                new BotPersona(-1, "Host", BotTier.HOSPITALITY, 0, 1, 1.0, true, true),
                hand(FAVOURABLE, LOSING));

        assertThat(decision).isNotNull();
        assertThat(decision.magicId()).isEqualTo(LOSING.id);
    }

    // 핵심 규칙. 플레이어 필드가 25마나짜리 하나뿐이면 진행도 0.5 기준 허용치는 12이고,
    // 25마나짜리 소환은 후보에서 빠지고 5마나짜리만 남는다.
    @Test
    void hospitalityOnlyCastsSummonsCheaperThanTheEnemyBoard() {
        Magic tooBig = BotBrainTestFixtures.summon(5, "too_big", PrefabType.FireSpirit, 1);
        manaCost(tooBig, 25.0);
        stubHand(tooBig);

        BotBrain.InputDecision decision = think(hospitalityPersona(), hand(tooBig, LOSING));

        assertThat(decision).isNotNull();
        assertThat(decision.magicId()).isEqualTo(LOSING.id);
    }

    // 규칙을 지킬 수 없으면 소환하지 않는다. 데드라인이 지났어도 마찬가지다. 플레이어보다 센
    // 것을 한 번 내놓는 순간 접대는 실패하고, 그건 잠깐 조용한 것보다 나쁘다.
    @Test
    void hospitalityRefusesToSummonWhenNothingStaysUnderTheBoard() {
        priceEnemyBoardWith(LOSING);

        BotBrain brain = new BotBrain(magicParser, counterEvaluator, hospitalityPersona());

        assertThat(brain.think(eye(hand(FAVOURABLE, LOSING)), parameters, Master.LeftPlayer, true)).isNull();
    }

    // 일반 봇은 이 규칙과 무관하다. 이기려는 봇이 상대 필드보다 싸게만 낼 이유가 없다.
    @Test
    void ordinaryTiersIgnoreTheEnemyBoardCap() {
        priceEnemyBoardWith(LOSING);

        BotBrain.InputDecision decision = think(persona(1.0), hand(FAVOURABLE, LOSING));

        assertThat(decision).isNotNull();
        assertThat(decision.magicId()).isEqualTo(FAVOURABLE.id);
    }

    // 스웜 하나가 다섯 마리를 내놓으면 한 마리 값은 시전 비용의 5분의 1이다. 그러지 않으면
    // 같은 소환이 필드 위에서 다섯 배로 계산돼, 봇이 훨씬 센 것을 내도 되는 것처럼 보인다.
    @Test
    void aSwarmBodyIsWorthItsShareOfTheCast() {
        // 적 보드가 25가 아니라 5로 계산되므로 5마나짜리 소환도 규칙을 지키지 못한다.
        Magic swarm = BotBrainTestFixtures.summon(6, "swarm", PrefabType.FireSpirit, 5);
        manaCost(swarm, 25.0);
        priceEnemyBoardWith(swarm);

        BotBrain brain = new BotBrain(magicParser, counterEvaluator, hospitalityPersona());

        assertThat(brain.think(eye(hand(FAVOURABLE, LOSING)), parameters, Master.LeftPlayer, true)).isNull();
    }

    // Holding for mana is right for a bot that is trying to win and wrong for one that has been
    // quiet too long. A card is one magic now, so when nothing in hand is affordable there is
    // nothing cheaper to cycle into either: waiting is the only move left, deadline or not.
    @Test
    void holdsForManaWhenNothingInHandIsAffordable() {
        manaCost(FAVOURABLE, 200.0);
        manaCost(LOSING, 200.0);

        BotBrain brain = new BotBrain(magicParser, counterEvaluator, hospitalityPersona());

        assertThat(brain.think(eye(hand(FAVOURABLE, LOSING)), parameters, Master.LeftPlayer, true)).isNull();
    }

    // An offensive card with an empty field scores no target. Standing on it reads as going easy on
    // the player, so once overdue the bot spends the cheapest affordable card anyway.
    @Test
    void spendsACardWhenOverdueAndNothingScoredATarget() {
        BotBrain brain = new BotBrain(magicParser, counterEvaluator, persona(0.0));
        BotEye emptyField = new BotEye(List.of(), hand(SHOT), 100, 100);

        BotBrain.InputDecision decision = brain.think(emptyField, parameters, Master.LeftPlayer, true);

        assertThat(decision).isNotNull();
        assertThat(decision.ruleId()).isEqualTo(BotBrain.CYCLE_RULE);
        assertThat(decision.magicId()).isEqualTo(SHOT.id);
    }

    private BotPersona hospitalityPersona() {
        return new BotPersona(-1, "Host", BotTier.HOSPITALITY, 0, 1, -1.0, true, true);
    }

    private void manaCost(Magic magic, double cost) {
        when(parameters.getValueOrDefault(eq(magic.name), eq("mana_cost"), anyDouble())).thenReturn(cost);
    }

    // 적 유닛의 가격은 그 유닛을 소환하는 마법의 시전 비용을 소환 개수로 나눈 값이다.
    private void priceEnemyBoardWith(Magic summon) {
        Collection<Magic> catalogue = List.of(summon);
        when(magicParser.getAllMagics()).thenReturn(catalogue);
    }

    private void stubHand(Magic... magics) {
        for (Magic magic : magics) {
            when(magicParser.getMagic(magic.id)).thenReturn(magic);
        }
    }

    private static List<Long> hand(Magic... magics) {
        return List.of(magics).stream().map(magic -> magic.id).toList();
    }

    private BotBrain.InputDecision think(BotPersona persona, List<Long> hand) {
        BotBrain brain = new BotBrain(magicParser, counterEvaluator, persona);
        return brain.think(eye(hand), parameters, Master.LeftPlayer);
    }

    private static BotEye eye(List<Long> hand) {
        return new BotEye(List.of(BotBrainTestFixtures.enemyUnit()), hand, 100, 100);
    }

    // ELITE keeps the noise amplitude at 0, so the ranking is never thrown away and the assertion is
    // about the score rather than about a coin flip.
    private static BotPersona persona(double counterAggression) {
        return new BotPersona(-1, "Test", BotTier.ELITE, 0, 1, counterAggression, true, false);
    }
}
