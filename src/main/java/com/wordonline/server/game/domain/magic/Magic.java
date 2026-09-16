package com.wordonline.server.game.domain.magic;

import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

import lombok.Getter;

@Getter
public abstract class Magic {

    // The magics row this bean was matched to. DatabaseMagicParser fills them in at startup; name
    // equals the Spring bean name, which equals magics.name, which is also the game_objects.name
    // the cast reads mana_cost and range under.
    public long id;
    public String name;
    public ElementType element = ElementType.NONE;

    public abstract void run(GameContext gameContext, Master master, Vector3 position);

    public void run(GameContext gameContext, Master master, Vector3 castOrigin, Vector3 position) {
        run(gameContext, master, position);
    }

}
