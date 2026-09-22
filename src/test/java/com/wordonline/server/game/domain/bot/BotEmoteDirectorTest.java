package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.game.dto.Emote;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 성향 표와 횟수 제한을 고정한다. 한 판을 100밀리초 간격으로 돌려 실제 loop 처럼 매 frame
 * 물어보고, seed 를 고정한 {@link Random} 으로 같은 판이 다시 나오게 한다.
 */
class BotEmoteDirectorTest {

    private static final long TICK_MILLIS = 100;
    private static final long MATCH_MILLIS = 300_000;
    private static final int FULL_HP = 1_000;
    private static final int LOW_HP = 400;
    private static final int MATCH_COUNT = 200;

    private record Sent(long atMillis, Emote emote) {
    }

    // 체력이 팽팽한 판. 인사 말고는 쓸 상황이 없다.
    private static final Health EVEN = now -> new int[] {FULL_HP, FULL_HP};

    // 봇이 판 내내 앞선다.
    private static final Health AHEAD = now -> new int[] {FULL_HP, LOW_HP};

    // 봇이 판 내내 밀린다.
    private static final Health BEHIND = now -> new int[] {LOW_HP, FULL_HP};

    // 절반까지 앞서다가 뒤집힌다. 두 상황이 한 판에 다 열린다.
    private static final Health SWINGING =
            now -> now < MATCH_MILLIS / 2 ? new int[] {FULL_HP, LOW_HP} : new int[] {LOW_HP, FULL_HP};

    private interface Health {
        /** 그 시각의 {@code {봇 체력, 상대 체력}}. */
        int[] at(long nowMillis);
    }

    // STOIC 은 인사만, 그것도 가끔 한다. WARM 과 같은 판을 200번 돌려 총량을 비교한다.
    @Test
    void stoicStaysFarQuieterThanWarm() {
        int warmCount = totalEmotes(BotTemperament.WARM, SWINGING);
        int stoicCount = totalEmotes(BotTemperament.STOIC, SWINGING);

        assertThat(stoicCount).isLessThan(warmCount / 5);
        assertThat(warmCount).isGreaterThan(MATCH_COUNT);
    }

    // 상황이 계속 바뀌어도 한 판에 4회를 넘지 않는다. 제한이 실제로 걸리는지 보려고
    // 4회를 채운 판이 있었다는 것도 같이 확인한다.
    @Test
    void sendsAtMostFourEmotesInOneMatch() {
        Random random = new Random(4242);
        int busiest = 0;

        for (int match = 0; match < MATCH_COUNT; match++) {
            List<Sent> sent = runMatch(BotTemperament.SMUG, random, SWINGING);
            assertThat(sent).hasSizeLessThanOrEqualTo(BotEmoteDirector.MATCH_BUDGET);
            busiest = Math.max(busiest, sent.size());
        }

        assertThat(busiest).isEqualTo(BotEmoteDirector.MATCH_BUDGET);
    }

    // 사람이 겪는 3초 cooldown 과 별개로, 봇의 두 emote 사이는 최소 20초다.
    @Test
    void keepsAtLeastTwentySecondsBetweenEmotes() {
        Random random = new Random(7);

        for (BotTemperament temperament : BotTemperament.values()) {
            for (int match = 0; match < MATCH_COUNT; match++) {
                List<Sent> sent = runMatch(temperament, random, SWINGING);
                for (int i = 1; i < sent.size(); i++) {
                    assertThat(sent.get(i).atMillis() - sent.get(i - 1).atMillis())
                            .isGreaterThanOrEqualTo(BotEmoteDirector.MIN_GAP_MILLIS);
                }
            }
        }
    }

    // WARM 은 어떤 상황에서도 약 올리지 않는다.
    @Test
    void warmNeverTaunts() {
        Random random = new Random(11);

        for (Health health : List.of(EVEN, AHEAD, BEHIND, SWINGING)) {
            for (int match = 0; match < MATCH_COUNT; match++) {
                assertThat(runMatch(BotTemperament.WARM, random, health))
                        .extracting(Sent::emote)
                        .doesNotContain(Emote.Taunt);
            }
        }
    }

    // 앞설 때와 밀릴 때 고르는 집합이 다르다. 인사는 20초 안에 끝나므로 그 뒤의 것만 본다.
    @Test
    void aheadAndBehindDrawFromTheirOwnSets() {
        Random random = new Random(99);

        for (int match = 0; match < MATCH_COUNT; match++) {
            assertThat(situational(runMatch(BotTemperament.SMUG, random, AHEAD)))
                    .extracting(Sent::emote)
                    .isSubsetOf(Emote.Taunt, Emote.Laugh);
            assertThat(situational(runMatch(BotTemperament.TIMID, random, BEHIND)))
                    .extracting(Sent::emote)
                    .isSubsetOf(Emote.Cry, Emote.Surprised);
        }
    }

    // 한 상황이 오래 이어져도 그 상황의 몫은 두 번이다. 판 내내 앞선 봇은 인사까지 합쳐
    // 세 번을 넘지 않는다.
    @Test
    void spendsASeparateBudgetPerSituation() {
        Random random = new Random(1234);

        for (int match = 0; match < MATCH_COUNT; match++) {
            assertThat(situational(runMatch(BotTemperament.SMUG, random, AHEAD)))
                    .hasSizeLessThanOrEqualTo(BotEmoteDirector.SITUATION_BUDGET);
        }
    }

    // 체력이 팽팽하면 인사 말고는 하지 않는다.
    @Test
    void staysQuietWhileTheScoreIsClose() {
        Random random = new Random(55);

        for (int match = 0; match < MATCH_COUNT; match++) {
            assertThat(situational(runMatch(BotTemperament.SMUG, random, EVEN))).isEmpty();
        }
    }

    // 인사 시각이 판마다 다르다. 늘 같은 초에 오면 기계로 보인다.
    @Test
    void variesTheGreetingTime() {
        Random random = new Random(2026);
        List<Long> greetingTimes = new ArrayList<>();

        for (int match = 0; match < MATCH_COUNT; match++) {
            List<Sent> sent = runMatch(BotTemperament.WARM, random, EVEN);
            if (!sent.isEmpty()) {
                greetingTimes.add(sent.getFirst().atMillis());
            }
        }

        assertThat(greetingTimes).isNotEmpty();
        assertThat(greetingTimes.stream().distinct().count()).isGreaterThan(10);
        assertThat(greetingTimes).allSatisfy(atMillis -> assertThat(atMillis)
                .isBetween(BotEmoteDirector.GREETING_MIN_DELAY_MILLIS,
                        BotEmoteDirector.GREETING_MIN_DELAY_MILLIS
                                + BotEmoteDirector.GREETING_DELAY_SPREAD_MILLIS
                                + TICK_MILLIS));
    }

    private int totalEmotes(BotTemperament temperament, Health health) {
        Random random = new Random(20260922);
        int total = 0;
        for (int match = 0; match < MATCH_COUNT; match++) {
            total += runMatch(temperament, random, health).size();
        }
        return total;
    }

    private List<Sent> runMatch(BotTemperament temperament, Random random, Health health) {
        BotEmoteDirector director = new BotEmoteDirector(temperament);
        List<Sent> sent = new ArrayList<>();

        for (long nowMillis = 0; nowMillis <= MATCH_MILLIS; nowMillis += TICK_MILLIS) {
            int[] hp = health.at(nowMillis);
            Emote emote = director.nextEmote(nowMillis, hp[0], hp[1], random);
            if (emote != null) {
                sent.add(new Sent(nowMillis, emote));
            }
        }
        return sent;
    }

    private List<Sent> situational(List<Sent> sent) {
        return sent.stream()
                .filter(one -> one.atMillis() >= BotEmoteDirector.MIN_GAP_MILLIS)
                .toList();
    }
}
