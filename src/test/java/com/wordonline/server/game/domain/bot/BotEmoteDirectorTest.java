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

    // 사람이 이 시각에 emote 를 보냈다는 뜻. 시각은 tick 간격의 배수여야 한다.
    private record Incoming(long atMillis, Emote emote) {
    }

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

    // 상황이 계속 바뀌어도 스스로 보내는 것은 한 판에 4회를 넘지 않는다. 제한이 실제로
    // 걸리는지 보려고 4회를 채운 판이 있었다는 것도 같이 확인한다.
    @Test
    void sendsAtMostFourSituationalEmotesInOneMatch() {
        Random random = new Random(4242);
        int busiest = 0;

        for (int match = 0; match < MATCH_COUNT; match++) {
            List<Sent> sent = runMatch(BotTemperament.SMUG, random, SWINGING);
            assertThat(sent).hasSizeLessThanOrEqualTo(BotEmoteDirector.SITUATIONAL_MATCH_BUDGET);
            busiest = Math.max(busiest, sent.size());
        }

        assertThat(busiest).isEqualTo(BotEmoteDirector.SITUATIONAL_MATCH_BUDGET);
    }

    // 답장까지 더해도 한 판 총합은 6회다.
    @Test
    void sendsAtMostSixEmotesInOneMatchWithRepliesIncluded() {
        Random random = new Random(606);
        List<Incoming> chatty = List.of(
                new Incoming(10_000, Emote.Greet),
                new Incoming(30_000, Emote.Laugh),
                new Incoming(60_000, Emote.Taunt),
                new Incoming(120_000, Emote.Cry),
                new Incoming(200_000, Emote.Surprised));

        for (int match = 0; match < MATCH_COUNT; match++) {
            assertThat(runMatch(BotTemperament.SMUG, random, SWINGING, chatty))
                    .hasSizeLessThanOrEqualTo(BotEmoteDirector.MATCH_BUDGET);
        }
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

    // 인사를 건네면 WARM 은 거의 늘 인사로 받고, STOIC 은 가끔만 받는다.
    @Test
    void warmGreetsBackFarMoreOftenThanStoic() {
        int warmGreetBacks = greetBacks(BotTemperament.WARM);
        int stoicGreetBacks = greetBacks(BotTemperament.STOIC);

        assertThat(stoicGreetBacks).isLessThan(warmGreetBacks / 3);
        assertThat(warmGreetBacks).isGreaterThan(MATCH_COUNT / 2);
    }

    // 답장에서도 WARM 은 약 올리지 않는다. 약 올림을 받아도 웃어넘긴다.
    @Test
    void warmNeverTauntsInReply() {
        Random random = new Random(808);

        for (Emote incoming : Emote.values()) {
            for (int match = 0; match < MATCH_COUNT; match++) {
                List<Sent> sent = runMatch(BotTemperament.WARM, random, EVEN,
                        List.of(new Incoming(12_000, incoming)));
                assertThat(sent).extracting(Sent::emote).doesNotContain(Emote.Taunt);
            }
        }
    }

    // 답장은 한 판에 두 번까지다. 사람이 다섯 번 걸어와도 두 번만 받는다.
    @Test
    void sendsAtMostTwoRepliesInOneMatch() {
        Random random = new Random(212);
        List<Incoming> spacedOut = List.of(
                new Incoming(10_000, Emote.Greet),
                new Incoming(30_000, Emote.Laugh),
                new Incoming(50_000, Emote.Taunt),
                new Incoming(70_000, Emote.Cry),
                new Incoming(90_000, Emote.Surprised));
        int busiest = 0;

        for (int match = 0; match < MATCH_COUNT; match++) {
            List<Sent> replies = replies(runMatch(BotTemperament.WARM, random, EVEN, spacedOut));
            assertThat(replies).hasSizeLessThanOrEqualTo(BotEmoteDirector.REPLY_BUDGET);
            busiest = Math.max(busiest, replies.size());
        }

        assertThat(busiest).isEqualTo(BotEmoteDirector.REPLY_BUDGET);
    }

    // 사람이 연달아 다섯 번 보내도 8초 안에 오는 답장은 하나뿐이다.
    @Test
    void answersABurstOnlyOnce() {
        Random random = new Random(313);
        List<Incoming> burst = List.of(
                new Incoming(10_000, Emote.Greet),
                new Incoming(10_300, Emote.Laugh),
                new Incoming(10_600, Emote.Taunt),
                new Incoming(10_900, Emote.Cry),
                new Incoming(11_200, Emote.Surprised));

        for (int match = 0; match < MATCH_COUNT; match++) {
            List<Sent> withinTheGap = replies(runMatch(BotTemperament.WARM, random, EVEN, burst)).stream()
                    .filter(one -> one.atMillis() < 10_000 + BotEmoteDirector.REPLY_GAP_MILLIS)
                    .toList();
            assertThat(withinTheGap).hasSizeLessThanOrEqualTo(1);
        }
    }

    // 답장은 상황별 20초 간격 밖에 있다. 인사를 보낸 직후라 그 간격이 닫혀 있어도 답한다.
    @Test
    void answersWhileTheSituationalGapIsClosed() {
        Random random = new Random(414);
        int answered = 0;

        for (int match = 0; match < MATCH_COUNT; match++) {
            List<Sent> sent = runMatch(BotTemperament.WARM, random, EVEN,
                    List.of(new Incoming(12_000, Emote.Greet)));
            // 인사는 8.1초 안에 끝나므로, 20초 간격이 아직 열리지 않은 이 시각의 emote 는 답장뿐이다.
            if (!replies(sent).isEmpty()) {
                answered++;
                assertThat(replies(sent).getFirst().atMillis())
                        .isLessThan(BotEmoteDirector.MIN_GAP_MILLIS);
            }
        }

        assertThat(answered).isGreaterThan(MATCH_COUNT * 3 / 4);
    }

    // 답장 시각도 흔들린다. 늘 같은 간격으로 오면 기계로 보인다.
    @Test
    void variesTheReplyDelay() {
        Random random = new Random(515);
        List<Long> delays = new ArrayList<>();

        for (int match = 0; match < MATCH_COUNT; match++) {
            List<Sent> replies = replies(runMatch(BotTemperament.WARM, random, EVEN,
                    List.of(new Incoming(12_000, Emote.Greet))));
            if (!replies.isEmpty()) {
                delays.add(replies.getFirst().atMillis() - 12_000);
            }
        }

        assertThat(delays).isNotEmpty();
        assertThat(delays.stream().distinct().count()).isGreaterThan(10);
        assertThat(delays).allSatisfy(delay -> assertThat(delay)
                .isBetween(BotEmoteDirector.REPLY_MIN_DELAY_MILLIS,
                        BotEmoteDirector.REPLY_MIN_DELAY_MILLIS
                                + BotEmoteDirector.REPLY_DELAY_SPREAD_MILLIS
                                + TICK_MILLIS));
    }

    private int greetBacks(BotTemperament temperament) {
        Random random = new Random(20260923);
        int greetBacks = 0;

        for (int match = 0; match < MATCH_COUNT; match++) {
            List<Sent> replies = replies(runMatch(temperament, random, EVEN,
                    List.of(new Incoming(12_000, Emote.Greet))));
            if (!replies.isEmpty() && replies.getFirst().emote() == Emote.Greet) {
                greetBacks++;
            }
        }
        return greetBacks;
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
        return runMatch(temperament, random, health, List.of());
    }

    private List<Sent> runMatch(BotTemperament temperament,
                                Random random,
                                Health health,
                                List<Incoming> incoming) {
        BotEmoteDirector director = new BotEmoteDirector(temperament);
        List<Sent> sent = new ArrayList<>();

        for (long nowMillis = 0; nowMillis <= MATCH_MILLIS; nowMillis += TICK_MILLIS) {
            for (Incoming one : incoming) {
                if (one.atMillis() == nowMillis) {
                    director.onOpponentEmote(one.emote(), nowMillis, random);
                }
            }
            int[] hp = health.at(nowMillis);
            Emote emote = director.nextEmote(nowMillis, hp[0], hp[1], random);
            if (emote != null) {
                sent.add(new Sent(nowMillis, emote));
            }
        }
        return sent;
    }

    // 체력이 팽팽한 판에서 인사 창(최대 8.1초)이 지난 뒤에 나온 것은 답장뿐이다.
    private List<Sent> replies(List<Sent> sent) {
        return sent.stream()
                .filter(one -> one.atMillis() > BotEmoteDirector.GREETING_MIN_DELAY_MILLIS
                        + BotEmoteDirector.GREETING_DELAY_SPREAD_MILLIS + TICK_MILLIS)
                .toList();
    }

    private List<Sent> situational(List<Sent> sent) {
        return sent.stream()
                .filter(one -> one.atMillis() >= BotEmoteDirector.MIN_GAP_MILLIS)
                .toList();
    }
}
