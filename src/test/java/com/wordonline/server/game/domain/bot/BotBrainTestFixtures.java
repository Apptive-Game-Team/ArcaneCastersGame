package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

final class BotBrainTestFixtures {

    private BotBrainTestFixtures() {
    }

    /** One enemy body on the field, so the brain has something to score a matchup against. */
    static BotVisibleObject enemyUnit() {
        return new BotVisibleObject(
                1, Master.RightPlayer, PrefabType.FireSpirit, new Vector3(0, 0, 0), 10, true, true);
    }

    /** A magic that leaves nothing standing: the brain scores it against a target. */
    static Magic offensive(long id, String name) {
        Magic magic = new Magic() {
            @Override
            public void run(GameContext gameContext, Master master, Vector3 position) {
            }
        };
        magic.id = id;
        magic.name = name;
        return magic;
    }

    /** A magic that puts bodies on the field, which is what BoardValue prices. */
    static Magic summon(long id, String name, PrefabType prefab, int quantity) {
        TestSummon magic = new TestSummon(prefab, quantity);
        magic.id = id;
        magic.name = name;
        return magic;
    }

    private static final class TestSummon extends Magic implements ObjectSummoningMagic {

        private final PrefabType prefab;
        private final int quantity;

        private TestSummon(PrefabType prefab, int quantity) {
            this.prefab = prefab;
            this.quantity = quantity;
        }

        @Override
        public void run(GameContext gameContext, Master master, Vector3 position) {
        }

        @Override
        public PrefabType summonedPrefab() {
            return prefab;
        }

        @Override
        public int summonedQuantity() {
            return quantity;
        }
    }
}
