package com.wordonline.server.game.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.frame.GameEventDto;
import com.wordonline.server.game.dto.frame.ObjectsInfoDto;
import com.wordonline.server.game.util.Physics;
import com.wordonline.server.game.util.SimplePhysics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class GameContext {

    private final GameTimer gameTimer;
    private SessionObject sessionObject;
    private final GameSessionData gameSessionData;
    private ResultChecker resultChecker;
    private int frameNum = 0;
    private final Parameters parameters;
    private Physics physics;
    private final MagicInputHandler magicInputHandler;
    private final DatabaseMagicParser magicParser;
    private ObjectsInfoDtoBuilder objectsInfoDtoBuilder;
    private float deltaTime = 1f / GameLoop.FPS;
    private CardSelectVisualizer cardSelectVisualizer;
    private final List<GameEventDto> events = new ArrayList<>();
    private final GameActionQueue actionQueue = new GameActionQueue();

    private WordOnlineLoop gameLoop;

    public void init(SessionObject sessionObject, WordOnlineLoop gameLoop) {
        this.sessionObject = sessionObject;
        this.cardSelectVisualizer = new CardSelectVisualizer(magicParser);
        this.gameSessionData.initCardDeck(sessionObject.getLeftUserCardDeck(), sessionObject.getRightUserCardDeck());
        this.resultChecker = new ResultChecker(sessionObject);
        this.objectsInfoDtoBuilder = new ObjectsInfoDtoBuilder(this);
        physics = new SimplePhysics(gameSessionData.gameObjects);

        this.gameLoop = gameLoop;
    }

    public void setLoser(Master master) {
        resultChecker.setLoser(master);
    }

    public List<GameObject> getActiveGameObjects() {
        return gameSessionData.gameObjects
                .stream()
                .filter(GameObject::isActive)
                .toList();
    }

    public List<GameObject> getGameObjects() {
        return gameSessionData.gameObjects;
    }

    public Optional<GameObject> findPlayerGameObject(Master master) {
        return getActiveGameObjects()
                .stream()
                .filter(gameObject -> gameObject.getType() == PrefabType.Player)
                .filter(gameObject -> gameObject.getMaster() == master)
                .findFirst();
    }

    public Optional<GameObject> findPlayerGameObject(long userId) {
        return findPlayerGameObject(sessionObject.getUserSide(userId));
    }

    public void updateGameObject(GameObject gameObject) {
        objectsInfoDtoBuilder.updateGameObject(gameObject);
    }

    public void createGameObject(GameObject gameObject) {
        objectsInfoDtoBuilder.createGameObject(gameObject);
    }

    public void addGameObject(GameObject gameObject) {
        gameSessionData.addGameObject(gameObject);
    }

    public List<GameObject> overlapSphereAll(GameObject object, float distance) {
        return physics.overlapSphereAll(object, distance);
    }

    public List<GameObject> overlapSphereAll(Vector3 position, float distance) {
        return physics.overlapSphereAll(position, distance);
    }

    public ObjectsInfoDto getObjectsInfoDto() {
        return objectsInfoDtoBuilder.getObjectsInfoDto();
    }

    public void addEvent(GameEventDto event) {
        events.add(event);
    }

    // drains the events collected since the last frame
    public List<GameEventDto> drainEvents() {
        if (events.isEmpty()) {
            return List.of();
        }
        List<GameEventDto> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    public void incrementFrameNum() {
        this.frameNum++;
    }

    // Threads other than the loop thread never mutate game state directly. They queue what they
    // want done here, and the loop runs it between frames, so a cast can never interleave with an
    // update(). Returns false when the queue is full and the action was dropped.
    public boolean submitAction(String name, Runnable action) {
        return actionQueue.submit(name, action);
    }

    // Called by the loop thread only, at the top of every frame.
    public void drainActions() {
        actionQueue.drain();
    }

    // =============

    public void selectCard(long userId, long magicId) {
        cardSelectVisualizer.selectCard(this, userId, magicId);
    }

    public void unselectCard(long userId, long magicId) {
        cardSelectVisualizer.unselectCard(this, userId, magicId);
    }
}
