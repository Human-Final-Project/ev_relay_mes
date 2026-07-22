# EV Relay MES 전체 흐름 정리 v2

> 구현 기준일: 2026-07-13
> TCP 상세 형식은 [`../tcp-protocol.md`](../tcp-protocol.md), 공정별 BOM은 [`sql_code_ver.2.md`](sql_code_ver.2.md)를 기준으로 한다.

## 1. 시스템 구성

```text
[L1 C 설비 시뮬레이터 6대]
              |
              | TCP/CSV
              v
[L2 C 수집기 / 연결당 pthread]
              |
              | HTTP REST/JSON
              v
[Spring Boot MES Backend]
              |
              | JPA
              v
[MySQL] <---- REST 조회 ---- [React Frontend]
```

역할:

| 구성 | 역할 |
|---|---|
| L1 | 공정 수행 시뮬레이션, 생산 요약·불량·설비 알람 전송, `HELLO`·`HEARTBEAT` 전송 |
| L2 | 다중 TCP 연결, 메시지 분리·파싱·검증, Backend 명령 Polling, L1 명령 전달, 통신 장애 감지, Backend REST 전송 |
| Backend | 작업지시·LOT·BOM 관리, Collector API 수신, 공정별 요약·오류·상태 저장, 최종 수량 계산 |
| Frontend | 작업지시, BOM, LOT 진행, 공정별 수량, 불량·알람 표시 |

L2는 Backend REST API를 1초마다 Polling하여 대기 작업명령을 가져오고, 기존 L1 TCP 연결로 `START`, `STOP`, `RESUME` 명령을 전달한다. L1은 `COMMAND_ACK`로 수락 또는 거부를 응답한다.

L2는 현재 연결된 설비의 `machineId`로 명령을 조회한다. Backend는 조회 응답에 포함한 명령을 `DISPATCHED`로 선점한다. L1 TCP 전송 전에 연결이 끊기거나 전송이 실패하면 L2는 release API를 호출해 해당 명령을 `PENDING`으로 반환한다. ACK 없이 10초 이상 `DISPATCHED` 상태가 유지된 명령도 Backend가 다음 Polling 시 `PENDING`으로 되돌린다.

```text
Backend 명령 저장
→ L2 REST Polling
→ L2가 대상 L1 TCP 연결로 COMMAND 전송
→ L1 COMMAND_ACK
→ L2가 Backend에 명령 상태 보고
```

L2는 Backend 전송이 연결·송수신 오류, 잘못된 응답 또는 HTTP 5xx로 실패하면 500ms 간격으로 최대 2회 재시도한다. 계속 실패한 요청은 디스크 큐에 저장하고 3초마다 복구 전송한다. 생산·검사·불량·알람·설비 상태 이벤트에는 `eventId`를 부여하며 Backend는 같은 `eventId`를 중복 저장하지 않는다. `COMMAND_ACK`는 별도 `eventId` 대신 Backend가 발급한 `commandId`로 명령과 ACK를 연결한다.

## 2. 생산 공정 흐름

OP20과 OP30은 동시에 실행되는 병렬 선행 공정이며, 두 반제품이 OP40_OP50에서 합류한다.

```text
OP20 코일 권선 ── SA-COIL-001 ───────┐
                                      ├→ OP40_OP50 자동 조립
OP30 접점 용접 ── SA-CONTACT-001 ─────┘
                                               |
                                               v
                                      OP60 실링/가스충전
                                               |
                                               v
                                         OP70 최종 검사
                                               |
                                               v
                                         OP80 마킹/포장
                                               |
                                               v
                                         FG-EVR-001 완제품
```

### 2.1 공정별 BOM

| 공정 | 생산 품목 | 필요 품목(제품 1개 기준) |
|---|---|---|
| OP20 | `SA-COIL-001` | `RM-CU-001` 1, `RM-BOB-001` 1 |
| OP30 | `SA-CONTACT-001` | `RM-CONTACT-F` 2, `RM-CONTACT-M` 1 |
| OP40_OP50 | `SA-BODY-001` | `SA-COIL-001` 1, `SA-CONTACT-001` 1, 코어 1, 요크 1, 스프링 1, 고전압 단자 2, 코일 단자 2, 세라믹 챔버 1, 자석 2 |
| OP60 | `SA-SEALED-001` | `SA-BODY-001` 1, 하우징 1, 에폭시 1 |
| OP70 | `SA-TESTED-001` | `SA-SEALED-001` 1 |
| OP80 | `FG-EVR-001` | `SA-TESTED-001` 1, 포장 자재 1 |

### 2.2 병렬 공정 수량 규칙

OP20·OP30 이외의 필요한 자재가 충분하고 BOM 수량이 각각 1개일 때:

```text
OP40_OP50 inputQty = min(OP20 okQty, OP30 okQty)
```

일반식:

```text
조립 가능 수량
= min(각 선행 반제품의 okQty / 완제품 1개당 필요 수량)
```

OP20과 OP30 중 한 공정만 완료된 상태에서는 OP40_OP50을 완료 처리하지 않는다. 두 결과의 DB 저장 순서는 보장하지 않으며 `processCode`로 구분한다.

OP40_OP50 이후에는 다음 규칙을 적용한다.

```text
다음 공정 inputQty = 이전 공정 okQty
```

## 3. 생산 이벤트 흐름

Backend는 OP20과 OP30의 `START` 명령을 각각 생성하고 L2는 두 L1에 독립적으로 전달한다. 두 공정 결과가 모두 저장되면 Backend가 BOM 기준 OP40_OP50 투입 수량을 계산해 다음 `START` 명령을 생성한다.

각 L1은 연결 직후 `HELLO`, 연결 중 5초마다 `HEARTBEAT`를 보낸다. 공정 수행 시 다음 이벤트를 보낸다.

```text
MACHINE_STATUS RUNNING
→ 공정 수행
→ 1초마다 누적 PRODUCTION(RUNNING)으로 같은 실적 행 갱신
→ DEFECT (제품 불량이 있을 때만)
→ 누적 PRODUCTION(COMPLETED) (공정 완료 실적, 항상)
→ MACHINE_STATUS IDLE
```

설비 오류가 발생하면 프로세스와 TCP 연결은 유지하고 생산만 중단한다.

```text
현재까지의 누적 PRODUCTION(RUNNING)
→ ALARM(ERROR)
→ MACHINE_STATUS ERROR
→ 현재 LOT·공정·진행 수량 메모리 유지
→ RESUME 대기
→ COMMAND_ACK ACCEPTED
→ MACHINE_STATUS RUNNING
→ 남은 수량부터 재개
→ 최종 누적 PRODUCTION(COMPLETED)
→ MACHINE_STATUS IDLE
```

Backend는 LOT·공정별 실적 한 행을 최신 누적값으로 갱신한 뒤 `최초 목표 수량 - 누적 처리 수량`으로 RESUME 수량을 계산한다. L1은 같은 LOT·공정과 정확한 남은 수량의 RESUME만 수락한다.

L2 처리:

```text
TCP 수신
→ LF(\n) 단위 메시지 분리
→ 프로토콜 파싱·검증
→ HELLO/HEARTBEAT는 L2 내부에서만 처리
→ 업무 이벤트를 JSON으로 변환
→ Backend Collector REST API 호출
```

## 4. Backend 저장 정책

모든 터미널 로그와 모든 TCP 메시지를 DB에 저장하지 않는다.

| 이벤트 | Backend 전달 | DB 저장 정책 |
|---|---|---|
| `HELLO` | 아니요 | L2 연결 식별에만 사용 |
| `HEARTBEAT` | 아니요 | L2 Timeout 판정에만 사용 |
| `PRODUCTION` | 예 | 정상 완료 실적 또는 오류·정지 시 부분 실적 저장 |
| `DEFECT` | 발생 시 | 제품 불량 상세 저장 |
| `ALARM` | 발생 시 | 설비 알람 상세 저장 |
| `MACHINE_STATUS` | 상태 변경 시 | 현재 설비 상태 및 상태 이력 저장 |
| 개발자용 로그 | 아니요 | L1/L2 터미널에만 출력 |

### 4.1 공정별 생산 실적

정상 완료 공정은 LOT·공정별 `COMPLETED` 실적 한 건을 저장한다. 설비 오류나 정지가 발생한 공정은 중단 시점의 `RUNNING` 부분 실적과 재개 후 잔여 `COMPLETED` 실적을 각각 저장한다. Backend는 같은 LOT·공정의 여러 실적 행을 합산해 공정 누적 수량을 계산한다.

```text
정상 실행: LOT 1개 × 6개 공정 = production_logs 6행
오류·정지 발생: 해당 공정에 부분 실적 행이 추가될 수 있음
```

예시:

| 공정 | 투입 | OK | NG | 상태 |
|---|---:|---:|---:|---|
| OP20 | 100 | 97 | 3 | COMPLETED |
| OP30 | 100 | 95 | 5 | COMPLETED |
| OP40_OP50 | 95 | 94 | 1 | COMPLETED |
| OP60 | 94 | 94 | 0 | COMPLETED |
| OP70 | 94 | 92 | 2 | COMPLETED |
| OP80 | 92 | 92 | 0 | COMPLETED |

공통 검증식:

```text
inputQty = okQty + ngQty
```

### 4.2 불량과 알람

- `production_logs.ng_qty`: 해당 공정의 총 불량 수량
- `defect_histories`: 불량 코드별 상세 원인과 수량
- `machine_alarm_histories`: 설비 이상 및 통신 이상 이력

MVP에서는 불량품을 재작업하지 않고 폐기한 것으로 처리한다. 같은 공정에서 전송된 불량 상세 수량 합계와 `production_logs.ng_qty`의 일치 여부는 Backend가 검증한다.

L1이 생성하는 알람 예:

```text
MOTOR_OVERLOAD, WIRE_BREAK, SENSOR_ERROR, WELD_POWER_ERROR, ASSEMBLY_JAM
```

L2가 연결 상태를 감지하여 생성하는 알람:

```text
COMM_DISCONNECTED, COMM_TIMEOUT
```

## 5. LOT 최종 수량과 상태

최종 완제품 수량은 마지막 OP80 공정의 정상 수량을 사용한다.

```text
lot.okQty = OP80 okQty
lot.ngQty = 최초 LOT inputQty - OP80 okQty
lot.status = COMPLETED  (OP80가 정상 완료된 경우)
```

단순히 불량 이력 행의 수를 세거나 모든 불량 수량을 무조건 합산해서 완제품 수량을 계산하지 않는다. 공정별 수량의 기준은 `production_logs`, 최종 기준은 OP80 결과다.

## 6. 통신 장애 처리

```text
L1 소켓 정상 종료/오류
→ L2가 COMM_DISCONNECTED 생성
→ Backend 알람 API 전송
→ 설비 상태 ERROR 전송
```

```text
15초 동안 정상 메시지 미수신
→ L2가 COMM_TIMEOUT 생성
→ Backend 알람 API 전송
→ 설비 상태 ERROR 전송
→ 해당 소켓 종료 및 재접속 대기
```

Timeout 처리로 L2가 소켓을 닫은 경우 `COMM_DISCONNECTED`를 추가 생성하지 않는다.

## 7. Frontend 표시 기준

- LOT별 OP20·OP30 병렬 진행 상태
- 공정별 `inputQty`, `okQty`, `ngQty`, 상태
- OP80 `okQty` 기반 최종 완제품 수량
- 불량 코드와 알람 코드에 대응하는 DB의 한글 명칭
- 설비 현재 상태 및 최근 상태 변경 시각

L1/L2의 영문 개발자 로그를 Frontend에서 번역하지 않는다. L1/L2는 안정적인 영문 코드로 통신하고, Backend가 코드 마스터의 한글 이름을 응답하여 Frontend가 표시한다.

## 8. 통합 테스트 기준

1. Backend에서 작업지시와 LOT을 미리 생성한다.
2. OP20과 OP30 L1을 동시에 L2에 연결한다.
3. L2가 두 연결의 메시지를 독립적으로 처리한다.
4. `production_logs`에 OP20·OP30 요약이 각각 저장된다.
5. 두 공정이 모두 완료된 뒤 OP40_OP50 투입 수량이 계산된다.
6. OP40_OP50 → OP60 → OP70 → OP80 순서로 완료한다.
7. OP80 `okQty`와 LOT 최종 정상 수량이 일치하는지 확인한다.
8. 불량이 있는 공정만 `defect_histories`가 생성되는지 확인한다.
9. `HELLO`, `HEARTBEAT`가 DB에 저장되지 않는지 확인한다.
10. 연결 종료와 Heartbeat 중단 시 통신 알람이 중복 없이 한 번 생성되는지 확인한다.
11. L2가 Backend의 대기 명령을 Polling하여 올바른 L1에 전달하는지 확인한다.
12. L1 `COMMAND_ACK`가 Backend 명령 상태에 반영되는지 확인한다.

## 9. 구현 범위 요약

```text
정상 제품 개별 이력 저장       X
공정별 생산 요약 저장          O
불량·알람 발생 시 상세 저장    O
HELLO·HEARTBEAT DB 저장         X
OP20·OP30 병렬 실행             O
OP40_OP50에서 두 반제품 합류    O
최종 완제품 = OP80 okQty        O
재작업                           X (MVP 제외)
Backend 명령 REST Polling         O (1초)
L2→L1 START/STOP/RESUME          O
HTTP 자동 재시도/eventId         O (최대 2회 재시도·영속 큐·중복 방지)
```
