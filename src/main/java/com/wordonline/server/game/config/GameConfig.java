package com.wordonline.server.game.config;

import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

public class GameConfig {
    public static final int X_BOUND = 9;
    public static final int Y_BOUND = 5;

    public static final int X_MID = 9;
    public static final int Y_MID = 5;

    public static final int WIDTH = 18;
    public static final int HEIGHT = 10;
    public static final float GRAVITY_ACCEL = 4f;
    public static final float AERIAL_STANDARD_HEIGHT = 2f;
    public static final float AERIAL_MOB_INIT_HEIGHT = 3f;
    public static final float DROP_MAGIC_INITIAL_HEIGHT = 10f;
    public static final float FALL_THRESHOLD_VELOCITY = 4f;
    public static final float FALL_THRESHOLD = 0.01f;

    public static final Vector3 LEFT_PLAYER_POSITION = new Vector3(1, 0, 5);
    public static final Vector3 RIGHT_PLAYER_POSITION = new Vector3(17, 0, 5);
    public static final Dictionary<Master, Vector3> PLAYER_POSITION = new Hashtable<>() {{
        put(Master.LeftPlayer, LEFT_PLAYER_POSITION);
        put(Master.RightPlayer, RIGHT_PLAYER_POSITION);
    }};

    // 경기마다 한 번 놓는 고정 바위 장애물의 자리. x = X_MID 를 기준으로 좌우 대칭인 네 쌍이다.
    // 배치를 바꿀 때는 이 목록만 고친다. GameLoop 는 목록을 복사해 쓰므로 여기 값은 변하지 않는다.
    public static final List<Vector3> ROCK_OBSTACLE_POSITIONS = List.of(
            new Vector3(4.0f, 0, 2.5f), new Vector3(14.0f, 0, 2.5f),
            new Vector3(4.0f, 0, 7.5f), new Vector3(14.0f, 0, 7.5f),
            new Vector3(6.5f, 0, 5.0f), new Vector3(11.5f, 0, 5.0f),
            new Vector3(8.0f, 0, 2.0f), new Vector3(10.0f, 0, 2.0f));

    public static final float DEFAULT_FLOATING_VELOCITY = 1;
}
