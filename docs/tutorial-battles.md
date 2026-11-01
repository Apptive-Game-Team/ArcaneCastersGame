# 세력별 튜토리얼 전투

`tutorial` 패키지는 실제 `Parameters`, `Magic`, `PrefabInitializer`, `GameContext`와
component/physics/lifecycle 처리를 사용한다. 정상 `SessionService`와 `GameLoop`를
만들지 않으므로 전적, MMR, 계정 상태, 복구 기록, 경험치·보상에 접근하지 않는다.
별도의 피해 계산이나 녹화 전용 passive target/사망 효과 생략 정책은 사용하지 않는다.

## API

- `POST /api/server/tutorial/battles`: WORDONLINE_SERVER 전용. `{ownerId,faction,requestId}`를 받아 `{battleId,ownerId,faction,server,expiresAt}`를 반환한다.
- `GET /api/tutorial/battles/{id}/state?afterFrame=N`: 인증된 소유자의 상태·최근 프레임·전체 snapshot을 반환한다.
- `POST /api/tutorial/battles/{id}/command`: `{commandId,revision,action,magic,position}`. action은 CAST/NEXT/RETRY다.
- `DELETE /api/tutorial/battles/{id}`: 소유자의 전투만 정리한다.

일반 사용자는 서버 전용 생성 API로 세력·사용자를 정할 수 없다. Lobby가 저장된
세력을 조회해 생성한다. game 인스턴스는 사용자당 활성 전투 하나를 재사용한다.
Lobby도 사용자별로 서버를 고정한다. 새 UUID만으로 다른 서버에 전투를 만들지 않는다.

소유권 오류는 403, 없는 전투는 404, 만료·종료는 410, 단계 불일치는 409,
허용하지 않은 카드·좌표·범위·마나는 400이다. 성공한 명령 ID와 원본 payload를
전투 종료까지 보존해 재요청을 실행하지 않는다. 같은 ID를 다른 payload에 쓰면 409다.
조회 직후 마지막 적이 죽어 추가 시전이 늦게 도착한 경우에도 409를 반환해 클라이언트가
현재 성공 화면을 조회하게 한다. 튜토리얼 전용 예외 처리기가 전역 처리기의 500 변환을
막아 400·403·404·409·410·429·503 상태를 보존한다.

## 진행과 자원

전투별 lock으로 tick·명령·상태 조회를 직렬화한다. 서버 단조시계에서 실제 경과한
20Hz tick만 진행하며, 요청당 최대 0.5초를 처리한다. 요청 횟수로 시간을 가속할 수
없고 긴 연결 단절이 한 번에 전투를 끝내지 않는다. 첫 시전 전과 구간 성공 후에는
시뮬레이션을 멈춘다. 각 구간은 마나 120으로 시작하고 기존 ManaCharger의 4/s를 쓴다.

shot → meteor → tornado → anti_air → formation → explosion → final 순서다.
공중 적과 목표 사망은 실제 Mob/CombatDeathListener로 확인한다. formation은 골렘을
먼저 소환하고 피격을 확인한 뒤 무리를 허용한다. final은 기본 마법 다섯 종을 모두
허용하며 지상·공중·속성 무리를 순서대로 보낸다. 패배와 구간 120초 초과는 FAILED,
RETRY는 현재 구간만 초기화한다. 마지막 전멸은 COMPLETED다.

메테오는 현재 무작위 낙하 마법이므로 체험 시전 뒤 기본 슛을 함께 허용해 남은 적을
정리한다. 물 폭발의 현재 피해는 30이므로 약점 실습 적은 HP 50의 EmberSpirit을
사용한다. HP 90의 FireChildSpirit을 놓으면 물 약점 2배로도 한 번에 죽지 않는다.
공유 마법 수치와 유닛 스탯을 튜토리얼에 맞춰 바꾸지 않는다.

기본 제한은 인스턴스당 전투 64개, 전투당 객체 256개, 수명 20분이다.
명령 기록은 1,024개, 프레임 기록은 100개·문자 메모리 약 1MB로 제한한다.
실패한 객체 초기화·업데이트는 성공으로 넘기지 않고 현재 구간 실패로 처리한다.
cleanup과 구간 교체는 `discard`를 사용해 종료 중 사망 소환물이 생기지 않게 한다.

## 검증

`TutorialBattleTest`는 읽기 전용으로 캡처한 `tutorial-parameters.json`과 production
마법·prefab을 사용한다. 두 세력의 모든 구간, 허공 시전, 허용 카드·좌표·마나,
명령 중복, 시간 가속 방지, 현재 구간 재시도, owner·생성 중복·상한·정리를 검사한다.
`TutorialSecurityTest`는 서버 전용 생성 권한을 확인한다. 실제 계정과 배포 서버에
연결한 E2E 검증은 별도로 필요하다.
