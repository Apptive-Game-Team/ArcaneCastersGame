package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.object.Vector3;

/**
 * A snapshot of one hostile object as the bot sees it during a single think pass.
 *
 * @param objectId                   id of the observed game object, for logging only
 * @param position                   position at observation time
 * @param hp                         remaining hit points, used to avoid overkill
 * @param distanceToDefendedPosition distance from the position the bot is defending
 * @param playerCore                 true when this is the enemy player itself, the win condition
 * @param strength                   how many average units this one counts as in pressure; 1 when
 *                                   the bot does not weigh threats by strength
 */
public record EnemyThreat(
        int objectId,
        Vector3 position,
        int hp,
        double distanceToDefendedPosition,
        boolean playerCore,
        double strength
) {

    /** A threat that counts as one unit, which is how every threat counted before strength existed. */
    public EnemyThreat(int objectId, Vector3 position, int hp, double distanceToDefendedPosition, boolean playerCore) {
        this(objectId, position, hp, distanceToDefendedPosition, playerCore, 1.0);
    }

    /** The same threat standing somewhere else, measured against the same defended position. */
    EnemyThreat movedTo(Vector3 newPosition, Vector3 defendedPosition) {
        return new EnemyThreat(objectId, newPosition, hp, newPosition.distance(defendedPosition), playerCore, strength);
    }
}
