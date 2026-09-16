package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.*;
import com.wordonline.server.game.dto.frame.CreatedObjectDto;
import com.wordonline.server.game.dto.frame.ObjectsInfoDto;
import com.wordonline.server.game.dto.frame.projectile.PositionProjectileTarget;
import com.wordonline.server.game.dto.frame.projectile.ProjectileDto;
import com.wordonline.server.game.dto.frame.projectile.ProjectileTarget;
import com.wordonline.server.game.dto.frame.projectile.ReferenceProjectileTarget;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ObjectsInfoDtoBuilder {

    private List<CreatedObjectDto> createdObjectDtos = new ArrayList<>();
    private List<UpdatedObjectDto> updatedObjectDtos = new ArrayList<>();
    // index over updatedObjectDtos: ids cannot repeat there, so a lookup here and the
    // first list match are the same entry. Reset together with the list every frame.
    private Map<Integer, UpdatedObjectDto> updatedObjectDtosById = new HashMap<>();
    private List<ProjectileDto> projectileDtos = new ArrayList<>();
    private final GameContext gameContext;

    public ObjectsInfoDtoBuilder(GameContext gameContext) {
        this.gameContext = gameContext;
    }

    public ObjectsInfoDto getObjectsInfoDto() {
        ObjectsInfoDto result = new ObjectsInfoDto(createdObjectDtos, updatedObjectDtos, projectileDtos);
        if (!createdObjectDtos.isEmpty() || !updatedObjectDtos.isEmpty())
            log.trace("ObjectsInfoDto: {}", result);
        createdObjectDtos = new ArrayList<>();
        updatedObjectDtos = new ArrayList<>();
        updatedObjectDtosById = new HashMap<>();
        projectileDtos = new ArrayList<>();
        return result;
    }

    /** 굵기를 정하지 않은 projection. 클라이언트가 자기 기본값으로 그린다. */
    private static final float NO_WIDTH = 0f;

    public void createProjection(GameObject start, GameObject end, String type, float duration) {
        createProjection(
                new ReferenceProjectileTarget(start.getId()),
                new ReferenceProjectileTarget(end.getId()),
                type,
                duration
        );
    }

    /** 살아 있는 두 오브젝트를 잇는, 굵기까지 실은 projection. */
    public void createProjection(GameObject start, GameObject end, String type, float duration, float width) {
        createProjection(
                new ReferenceProjectileTarget(start.getId()),
                new ReferenceProjectileTarget(end.getId()),
                type,
                duration,
                width
        );
    }

    public void createProjection(Vector3 start, Vector3 end, String type, float duration) {
        createProjection(
                new PositionProjectileTarget(start),
                new PositionProjectileTarget(end),
                type,
                duration
        );
    }

    public void createProjection(ProjectileTarget start, ProjectileTarget destination, String type, float duration) {
        createProjection(start, destination, type, duration, NO_WIDTH);
    }

    /**
     * 굵기를 함께 실어 보내는 projection. 한 방의 세기가 시전마다 달라지는 마법이 쓴다 —
     * spirit_bomb 의 빔이 그렇다. 굵기가 고정인 projection 은 위의 overload 를 그대로 쓴다.
     */
    public void createProjection(
            ProjectileTarget start,
            ProjectileTarget destination,
            String type,
            float duration,
            float width) {
        projectileDtos.add(new ProjectileDto(type, start, destination, duration, width));
    }

    public void createGameObject(GameObject gameObject) {
        gameContext.addGameObject(gameObject);
        gameObject.start();
        CreatedObjectDto createdObjectDto = new CreatedObjectDto(
                gameObject.getId(),
                gameObject.getType(),
                gameObject.getPosition(),
                gameObject.getMaster(),
                copyOrEmpty(gameObject.getGizmos())
        );
        createdObjectDtos.add(createdObjectDto);
        log.trace("CreatedObjectDto: {}", createdObjectDto);
    }

    public void updateGameObject(GameObject gameObject) {
        UpdatedObjectDto updatedObjectDto = updatedObjectDtosById.get(gameObject.getId());
        if (updatedObjectDto != null) { // if the object is already in the update list, update it
            updatedObjectDto.setPosition(gameObject.getPosition());
            updatedObjectDto.setStatus(gameObject.getStatus());
            updatedObjectDto.setEffects(copyOrEmpty(gameObject.getEffects()));
            updatedObjectDto.setMaster(gameObject.getMaster());
            updatedObjectDto.updateGauges(gameObject);
        } else { // if the object is not in the update list, add it
            updatedObjectDto = new UpdatedObjectDto(gameObject);
            updatedObjectDto.setPosition(gameObject.getPosition());
            updatedObjectDtos.add(updatedObjectDto);
            updatedObjectDtosById.put(gameObject.getId(), updatedObjectDto);
        }
        log.trace("UpdatedObjectDto: {}", updatedObjectDto);
    }

    // ArrayList.toArray() allocates even for an empty source, and these lists are
    // usually empty, so hand back the shared empty list instead.
    private static <T> List<T> copyOrEmpty(List<T> source) {
        return source.isEmpty() ? List.of() : List.copyOf(source);
    }
}
