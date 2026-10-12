package com.wordonline.server.game.domain.object;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.debug.Gizmo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.Collider;
import com.wordonline.server.game.domain.object.prefab.PrefabProvider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.dto.StatusChangeEvent;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.util.SynchronousFlowPublisher;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

// This class is used to store the game object data
@Getter
public class GameObject {

    private static final AtomicInteger idCounter = new AtomicInteger(0);

    private final int id;
    private Master master;

    private final PrefabType type;
    private Status status;

    public boolean isActive() {
        return !isDestroyed() && isInitialized();
    }

    public boolean isDestroyed() {
        return status == Status.Destroyed;
    }

    // lethally damaged but still simulated: aerial mobs keep falling until they reach the ground
    public boolean isDying() {
        return status == Status.Dying;
    }

    public boolean isInitialized() {
        return status != Status.Initializing;
    }

    private final List<Effect> effects = new ArrayList<>();
    private final Element element = new Element();
    private Vector3 position;

    private final GameContext gameContext;
    private final List<Collider> colliders = new ArrayList<Collider>();
    private final List<Component> components = new ArrayList<Component>();
    private final List<Component> componentsToAdd = new ArrayList<Component>();
    private final List<Component> componentsToRemove = new ArrayList<Component>();
    private final List<Gizmo> gizmos = new ArrayList<Gizmo>();
    private final SynchronousFlowPublisher<StatusChangeEvent> statusChangePublisher = new SynchronousFlowPublisher<>();

    public GameObject(GameObject parent, Master master, PrefabType prefabType) {
        this(master, prefabType, parent.getPosition(), parent.gameContext);
    }

    public GameObject(GameObject parent, PrefabType prefabType) {
        this(parent, parent.getMaster(), prefabType);
    }

    public GameObject(Master master, PrefabType prefabType, Vector3 position, GameContext gameContext) {
        this.status = Status.Initializing;
        this.id = idCounter.getAndIncrement();
        this.master = master;
        this.type = prefabType;
        this.position = position;
        this.gameContext = gameContext;
        gameContext.createGameObject(this);
    }

    public Optional<CircleCollider> getFirstCircleCollider(boolean isTrigger) {
        return colliders.stream()
                .filter(CircleCollider.class::isInstance)
                .filter(collider -> collider.isTrigger() == isTrigger)
                .map(CircleCollider.class::cast)
                .findFirst();
    }

    public Optional<CircleCollider> getFirstCircleCollider() {
        return colliders.stream()
                .filter(CircleCollider.class::isInstance)
                .map(CircleCollider.class::cast)
                .findFirst();
    }

    public void addCollider(Collider collider) {
        colliders.add(collider);
        if (collider instanceof CircleCollider circleCollider) {
            gizmos.add(Gizmo.circle(Vector3.ZERO, circleCollider.getRadius(), GizmoCategory.Collider));
        }
    }

    // indexed loops instead of streams: these run about 160 times per frame per session in the
    // physics broad phase alone, and the stream pipeline allocated a dozen objects per call
    public <T> boolean hasComponent(Class<T> clazz) {
        for (int i = 0; i < components.size(); i++) {
            if (clazz.isInstance(components.get(i))) {
                return true;
            }
        }
        return false;
    }

    public <T> T getComponent(Class<T> clazz) {
        for (int i = 0; i < components.size(); i++) {
            Component component = components.get(i);
            if (clazz.isInstance(component)) {
                return clazz.cast(component);
            }
        }
        return null;
    }

    public <T> Optional<T> getComponentOptional(Class<T> clazz) {
        return Optional.ofNullable(getComponent(clazz));
    }

    public <T> List<T> getComponents(Class<T> clazz) {
        List<T> matched = null;
        for (int i = 0; i < components.size(); i++) {
            Component component = components.get(i);
            if (clazz.isInstance(component)) {
                if (matched == null) {
                    matched = new ArrayList<>();
                }
                matched.add(clazz.cast(component));
            }
        }
        // the no-match case is the common one, so it allocates nothing at all
        return matched == null ? List.of() : Collections.unmodifiableList(matched);
    }

    public void addComponent(Component component)
    {
        componentsToAdd.add(component);
    }

    public void removeComponent(Component component)
    {
        componentsToRemove.add(component);
    }

    public void destroy() {
        setStatus(Status.Destroyed);
        applyUpdate();
        onDestroy();
    }

    // Explicit administrative removal, with no death/status/destruction effects or spawning.
    // Only call on the owning loop thread.
    public void discard() {
        status = Status.Destroyed;
        statusChangePublisher.close();
        applyUpdate();
        components.clear();
        componentsToAdd.clear();
        componentsToRemove.clear();
        colliders.clear();
    }

    public void setPosition(Vector3 position) {
        this.position = position;
        if (Math.abs(position.getX() - GameConfig.X_MID) > GameConfig.X_BOUND || Math.abs(position.getZ() - GameConfig.Y_MID) > GameConfig.Y_BOUND) {
            destroy();
            return;
        }
        applyUpdate();
    }

    public void setStatus(Status status) {
        if (this.status == Status.Destroyed) return;
        // a dying object keeps its status until it is actually removed
        if (this.status == Status.Dying && status != Status.Destroyed) return;
        Status previous = this.status;
        if (previous == status && !isRepeatableStatus(status)) return;
        this.status = status;
        publishStatusChange(previous, status);
        applyUpdate();
    }

    private boolean isRepeatableStatus(Status status) {
        return status == Status.Attack || status == Status.Hindered;
    }

    public void subscribeStatusChange(Flow.Subscriber<StatusChangeEvent> subscriber) {
        statusChangePublisher.subscribe(subscriber);
    }

    private void publishStatusChange(Status previous, Status current) {
        statusChangePublisher.publish(new StatusChangeEvent(previous, current));
    }

    public void setMaster(Master master) {
        if (this.master == master) return;
        this.master = master;
        applyUpdate();
    }

    public void addEffect(Effect effect) {
        if (effect == Effect.None || effects.contains(effect)) {
            return;
        }
        effects.add(effect);
        applyUpdate();
    }

    public void removeEffect(Effect effect) {
        if (effects.remove(effect)) {
            applyUpdate();
        }
    }

    public void removeEffects(Predicate<Effect> predicate) {
        if (effects.removeIf(predicate)) {
            applyUpdate();
        }
    }

    public void clearEffects() {
        if (effects.isEmpty()) {
            return;
        }
        effects.clear();
        applyUpdate();
    }

    public void setElement(ElementType element) {
        this.element.addNative(element);
    }
    public void setElement(Set<ElementType> elementSet) {
        elementSet.forEach(this::setElement);
    }

    public void addTempElement(ElementType element, Object source) {
        this.element.addBonus(element, source);
    }
    public void removeTempElement(ElementType element, Object source) {
        this.element.removeBonus(element, source);
    }

    public void start() {
        if (!gameContext.initializePrefab(this)) PrefabProvider.get(type).initialize(this);
        components.addAll(componentsToAdd);
        componentsToAdd.clear();
        for (Component component : components)
            component.start();
        setStatus(Status.Idle);
    }

    public void update() {
        boolean dying = isDying();
        for (Component component : components) {
            if (dying && !component.isActiveWhileDying()) continue;
            component.update();
        }
    }

    public void onDestroy() {
        for (Component component : components)
            component.onDestroy();
        statusChangePublisher.close();
    }

    public void drawCircle(Vector3 relativePosition, float radius, GizmoCategory category) {
        gizmos.add(Gizmo.circle(relativePosition, radius, category));
    }

    public void drawBox(Vector3 relativePosition, Vector3 boxSize, GizmoCategory category) {
        gizmos.add(Gizmo.box(relativePosition, boxSize, category));
    }

    public void applyUpdate() {
        gameContext.updateGameObject(this);
    }

    public void flushComponents() {
        if (!this.getComponentsToAdd().isEmpty()) {
            List<Component> toAdd = new ArrayList<>(this.getComponentsToAdd());
            this.getComponentsToAdd().clear();
            this.getComponents().addAll(toAdd);
            for (Component c : toAdd) {
                c.start();
            }
        }

        if (!this.getComponentsToRemove().isEmpty()) {
            List<Component> toRem = new ArrayList<>(this.getComponentsToRemove());
            this.getComponentsToRemove().clear();
            for (Component c : toRem) {
                c.onDestroy();
            }
            this.getComponents().removeAll(toRem);
        }
    }
}
