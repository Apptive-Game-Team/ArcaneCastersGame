package com.wordonline.server.game.domain.object.component.effect.statuseffect;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.dto.StatusChangeEvent;
import lombok.Getter;

import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;

// Marks that the owner has at least one card selected, whatever the card type. The client raises
// the staff while Effect.CardSelected is on a player object, and it sees both players' objects.
public class CardSelectedStatusEffect extends BaseStatusEffect implements Flow.Subscriber<StatusChangeEvent> {

    // the same card type can be selected more than once, so this counts cards rather than types
    private int selectedCount = 1;
    private Subscription subscription;
    @Getter
    private boolean expired;

    public CardSelectedStatusEffect(GameObject owner) {
        super(owner, Float.POSITIVE_INFINITY, StatusEffectKey.Default, Effect.CardSelected);
        gameObject.subscribeStatusChange(this);
    }

    public void select() {
        selectedCount++;
    }

    public void unselect() {
        selectedCount--;
        if (selectedCount <= 0) {
            expire();
        }
    }

    public void cancel() {
        expire();
    }

    @Override
    public void update() {
    }

    @Override
    public void onAttacked(ElementType attackType) {
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        if (expired) {
            subscription.cancel();
            this.subscription = null;
            return;
        }
        subscription.request(1);
    }

    // No input message tells the server that a cast consumed the selected cards, so the player's
    // own status is what ends the selection: PlayerStatusSetter sets Attack on a successful cast
    // and Hindered when the cards parsed to no magic, and MagicInputHandler has already spent the
    // cards in both cases. Without this the raised staff would never come back down.
    @Override
    public void onNext(StatusChangeEvent event) {
        subscription.request(1);
        if (event.current() == Status.Attack || event.current() == Status.Hindered) {
            expire();
        }
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void onDestroy() {
        cancelSubscription();
        super.onDestroy();
    }

    @Override
    protected void expire() {
        if (expired) {
            return;
        }
        expired = true;
        selectedCount = 0;
        cancelSubscription();
        super.expire();
    }

    private void cancelSubscription() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }
}
