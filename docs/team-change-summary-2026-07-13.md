# 2026-07-13 설계 변경 공유

## 1. 변경 배경

팀 회의를 통해 실제 공정 흐름과 데이터 저장 정책을 다음과 같이 정리했다.

- OP20 코일 권선과 OP30 접점 용접은 동시에 실행한다.
- 두 공정의 반제품이 준비된 뒤 OP40_OP50 자동 조립에서 합류한다.
- 정상 제품 한 개마다 DB 행을 만들지 않는다.
- 정상 공정 완료 시 LOT·공정 단위 생산 실적을 `production_logs`에 저장한다.
- 설비 오류·정지 시에는 중단 전 부분 실적과 재개 후 잔여 실적을 별도 행으로 저장한다.
- 불량과 설비 알람은 발생한 경우에만 상세 이력을 저장한다.
- 최종 완제품 수량은 OP80 `okQty`를 기준으로 한다.

## 2. 확정 구조

```text
L1 C 설비 6대
  → TCP/CSV
L2 C 수집기
  → REST/JSON
Spring Boot Backend
  → JPA
MySQL
  → REST 조회
React Frontend
```

Backend는 TCP를 직접 수신하지 않는다. L2 전용 REST Request DTO, Controller, Service가 필요하다.

## 3. 공정 흐름

```text
OP20 ── SA-COIL-001 ───────┐
                            ├→ OP40_OP50 → OP60 → OP70 → OP80
OP30 ── SA-CONTACT-001 ─────┘
```

현재 BOM에서 OP40_OP50 제품 1개에 두 반제품이 각각 1개 필요하므로 다른 자재가 충분할 때:

```text
OP40_OP50 inputQty = min(OP20 okQty, OP30 okQty)
```

OP40_OP50 이후에는 직전 공정 `okQty`를 다음 공정 `inputQty`로 사용한다.

## 4. DB 저장 기준

| 데이터 | 저장 기준 |
|---|---|
| `PRODUCTION` | 정상 완료 시 `COMPLETED`, 오류·정지 시 부분 `RUNNING`과 재개 후 잔여 `COMPLETED` 저장 |
| `DEFECT` | 제품 불량이 발생할 때만 상세 저장 |
| `ALARM` | 설비 또는 통신 이상이 발생할 때만 저장 |
| `MACHINE_STATUS` | 상태가 실제로 변경될 때만 저장 |
| `HELLO`, `HEARTBEAT` | DB 저장 안 함, L2 내부 연결 관리에만 사용 |
| L1/L2 개발자 로그 | DB 저장 안 함 |

MVP에서는 재작업과 공정 재실행을 제외한다. 다만 설비 오류·정지로 한 공정이 나뉘면 같은 LOT·공정에 여러 구간 실적 행이 생길 수 있고 Backend가 이를 합산한다.

```text
정상 실행: LOT 1개 × 6개 공정 = production_logs 6행
오류·정지 발생: 해당 공정에 부분 실적 행 추가
```

최종 LOT 수량:

```text
lots.okQty = OP80 okQty
lots.ngQty = 최초 LOT inputQty - OP80 okQty
```

## 5. 역할별 영향

### L1

- OP20과 OP30 설비를 동시에 실행할 수 있어야 한다.
- 공정 완료 시 `PRODUCTION(COMPLETED)`를 반드시 전송한다.
- 일반 공정은 1초마다 누적 `PRODUCTION(RUNNING)`을 전송하고 Backend는 LOT·공정별 실적 한 행을 갱신한다.
- 오류·정지 시 최신 누적 `PRODUCTION(RUNNING)`을 전송하고 현재 LOT·공정·진행 수량을 메모리에 유지한다.
- 오류 시 `ALARM(ERROR)`, `MACHINE_STATUS(ERROR)` 순서로 전송하고 `RESUME` 전까지 생산을 중단한다.
- `RESUME` 수신 시 `COMMAND_ACK(ACCEPTED)`, `MACHINE_STATUS(RUNNING)`을 전송하고 남은 수량부터 재개한다.
- `inputQty = okQty + ngQty`를 만족해야 한다.
- 불량·알람은 실제 발생 시에만 전송한다.
- 연결 후 `HELLO`, 연결 중 5초마다 `HEARTBEAT`를 전송한다.
- 설비·공정·불량 코드를 SQL 코드와 일치시킨다.

### L2

- 연결당 `pthread` 하나로 OP20·OP30 동시 메시지를 독립 처리한다.
- 메시지 도착 순서를 생산 순서로 해석하지 않는다.
- 설비·공정 대응, 필드 수, 수량과 코드값을 검증한다.
- OP40_OP50 투입 수량이나 최종 생산량을 계산하지 않고 각 이벤트를 Backend에 전달한다.
- `HELLO`, `HEARTBEAT`는 Backend로 전달하지 않는다.
- `COMM_DISCONNECTED`, `COMM_TIMEOUT`은 L2가 생성한다.

### Backend

- Collector 전용 DTO, Controller, Service를 구현한다.
- OP20·OP30 결과를 순서와 무관하게 각각 저장한다.
- 두 선행 공정 완료 및 BOM을 확인해 OP40_OP50 투입 가능 수량을 계산한다.
- 공정별 `production_logs` 구간 실적을 저장하고 LOT·공정별 누적 수량을 합산하며, 불량·알람·상태 변경을 저장한다.
- OP80 완료 시 LOT 최종 수량과 상태를 갱신한다.
- 같은 공정 결과가 중복 저장되지 않게 멱등성을 보장한다.
- L2가 Polling할 작업명령 API와 ACK 상태 갱신 API를 구현한다.

### Frontend

- OP20·OP30을 병렬 진행 상태로 표시한다.
- 공정별 투입/OK/NG를 표시한다.
- OP80 `okQty`를 최종 완제품 수량으로 표시한다.
- 영문 코드 자체를 번역하지 않고 Backend가 제공하는 코드 마스터의 한글 이름을 표시한다.

## 6. 작업명령 및 네트워크 장애 대응

> 2026-07-20 변경: 정상 네트워크 가정을 제거하고 최신 `develop`의 HTTP 재시도·영속 큐·`eventId` 구현을 기준으로 통일한다.

- L2는 Backend REST API를 1초마다 Polling하여 `PENDING` 작업명령을 가져온다.
- L2는 기존 L1 TCP 연결로 `START`, `STOP`, `RESUME` 명령을 전달한다.
- L1은 `COMMAND_ACK`로 `ACCEPTED` 또는 `REJECTED`를 응답한다.
- Backend가 생성한 `commandId`로 명령과 ACK를 연결한다.
- L2→Backend HTTP의 연결·송수신 오류, 잘못된 응답과 HTTP 5xx는 최초 전송 후 최대 2회 재시도한다.
- 재시도 후에도 실패하면 요청과 JSON을 `mes_http_retry.queue`에 저장하고 3초마다 복구 전송한다.
- HTTP 4xx와 예상하지 못한 상태 코드는 `mes_http_dead_letter.queue`에 저장한다.
- 생산·검사·불량·알람·설비 상태 이벤트에는 L2가 `eventId`를 생성한다.
- Backend는 같은 `eventId`가 다시 수신되면 새 행을 만들지 않고 기존 결과를 반환한다.
- `COMMAND_ACK`는 별도 `eventId`가 아니라 `commandId`로 중복 명령과 ACK를 처리한다.

같은 LOT·공정의 업무 중복 검사와 별도로, 네트워크 재시도에 따른 동일 이벤트 중복 저장은 `eventId`로 방지한다.

### 아직 임시인 값

- L2 TCP 포트 `9000`
- Linux/Windows 최종 실행 환경

## 7. 구현 기준 문서

우선순위:

1. [`tcp-protocol.md`](tcp-protocol.md)
2. [`reference/EV_Relay_MES_Overall_Flow.md`](reference/EV_Relay_MES_Overall_Flow.md)
3. [`reference/sql_code_ver.2.md`](reference/sql_code_ver.2.md)
4. 루트 [`../README.md`](../README.md)

`reference/Guide.md`와 `reference/EV_Relay_MES_Folder_Structure_ver.2.2.md`에 남아 있는 초기 Backend TCP Listener 내용은 구현 기준이 아니다.
