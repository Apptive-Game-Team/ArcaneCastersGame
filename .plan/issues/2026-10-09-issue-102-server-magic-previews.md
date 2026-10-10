# 2026-10-09 — 서버에서 최신 마법 미리보기 생성

- Date: 2026-10-09
- GitHub Issue: [#102](https://github.com/Apptive-Game-Team/ArcaneCastersGame/issues/102)
- Status: Implemented; PR preparation

## Goal
운영 마법/프리팹과 불변 파라미터 스냅샷으로 v2 리플레이를 생성하고 서비스 인증 API로 제공한다.

## Non-goals
실제 매치/통계 처리, DB 스키마 변경, 버전 변경.

## Context / Constraints
preview 패키지, GameContext의 기본 비활성 연결점, ParameterRepository.snapshot과 invalidate 경로. 기존 테스트 녹화기는 운영 코드에서 사용하지 않는다.

## Approach (Checklist)
- [x] **Step 0: Recon** 기존 동작과 모듈 지침을 확인했다. 공동 구현 계획은 fast/medium/heavy 검토를 거쳤다.
- [x] **Step 1: Implementation** 단일 백그라운드 생성, 원자적 게시, 마법별 실패/한도, 현재 파라미터와 생산 프리팹 사용.
- [x] **Step 2: Tests** Gradle 전체 801개 통과. 운영 녹화기 85마법/129상황, 피해·수량 변경, 무효화 경합, 서비스 권한 검증.
- [ ] **Step 3: Rollout / Rollback** 로비 #62와 클라이언트 #266 전에 배포한다. 실패 시 해당 모듈 커밋을 되돌린다.

## Validation
- **Commands to run:** ./gradlew test; git diff --check.
- **Expected output:** Gradle 전체 801개 통과. 운영 녹화기 85마법/129상황, 피해·수량 변경, 무효화 경합, 서비스 권한 검증.
- 실제 DB 및 배포 브라우저 전체 연동 검증은 배포 확인 항목이다. Graphify 실행 파일이 없어 루트 그래프 갱신은 실행하지 못했다.

## Risks & Rollback
- **Risks:** 운영 입력에 따라 상황 재생 길이가 달라질 수 있으며 서버 생성/전송 비용과 브라우저 저장 동작은 실제 환경에서 확인한다.
- **Rollback steps:** 로비 #62와 클라이언트 #266 전에 배포한다. 실패 시 해당 모듈 커밋을 되돌린다.

## Open Questions
- 실제 DB 입력과 WebGL 브라우저 재실행/저장 용량 초과 검증.
