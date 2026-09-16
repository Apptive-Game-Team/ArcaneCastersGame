package com.wordonline.server.game.dto.frame.projectile;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProjectileDto {
    public String type;
    public ProjectileTarget start;
    public ProjectileTarget end;
    public float duration;

    /**
     * 클라이언트가 그릴 굵기, world 단위. 0 이면 서버가 정하지 않은 것이고 클라이언트가 자기
     * 기본값을 쓴다. 대부분의 projection 은 굵기가 고정이라 0 으로 나간다.
     */
    public float width;
}
