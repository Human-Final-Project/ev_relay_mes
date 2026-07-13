# EV Relay MES TCP 프로토콜 명세 (초안 v0.4)

> 상태: 팀 검토용 초안  
> 검토 담당: L1 박민, L2 변후민, Backend 홍준희/김도형  
> 목적: L1 설비 시뮬레이터가 L2 수집기로 보내는 메시지 규칙을 통일한다.

## 1. 적용 범위

이 문서는 아래 구간의 통신 규칙을 정의한다.

```text
[L1 설비 시뮬레이터 6대] -- TCP/CSV --> [L2 C 수집기]
                                           |
                                           +-- HTTP/JSON --> [Spring Boot MES]
```

- L1은 TCP 클라이언트로 동작한다.
- L2는 TCP 서버로 동작하며 여러 L1 연결을 수신한다.
- L2와 Spring Boot 사이에는 TCP가 아니라 REST API와 JSON을 사용한다.
- TCP 포트와 Backend URL은 확정 전까지 임시값을 사용한다.
- L2는 Backend REST API를 1초마다 Polling하여 대기 작업명령을 조회한다.
- L2는 조회한 작업명령을 기존 L1 TCP 연결로 전달하고 L1은 `COMMAND_ACK`로 수락 여부를 응답한다.
- MVP는 정상 네트워크를 가정하며 L2→Backend HTTP 요청은 자동 재시도하지 않는다.

## 2. 연결 규칙

| 항목 | 규칙 |
|---|---|
| 전송 방식 | TCP/IPv4 |
| 문자 인코딩 | UTF-8 |
| L2 주소 | 개발 시 `127.0.0.1` |
| L2 포트 | 임시 `9000` |
| 연결 주체 | L1이 L2에 접속 |
| 연결 방식 | 설비별 장기 연결 1개 |
| 메시지 최대 크기 | 개행 포함 1,024바이트 |
| 필드 구분자 | 쉼표 `,` |
| 메시지 종료 | LF 개행 `\n` 1개 |
| 재연결 | 연결 실패 또는 끊김 발생 시 3초 후 재시도 |
| 연결 식별 제한시간 | 연결 후 5초 이내 `HELLO` 전송 |
| Heartbeat 주기 | L1이 5초마다 전송 |
| 통신 Timeout | 마지막 정상 메시지 수신 후 15초 |
| 명령 Polling 주기 | L2가 Backend를 1초마다 조회 |

TCP의 `send()` 한 번과 `recv()` 한 번은 일대일로 대응하지 않는다. L2는 수신 데이터를 버퍼에 누적하고 `\n`을 발견했을 때 한 메시지로 분리해야 한다.

## 3. 공통 메시지 규칙

모든 L1 메시지는 다음 공통 형태로 시작한다.

```text
VERSION,EVENT_TYPE,MACHINE_ID,...\n
```

| 필드 | 설명 | 예시 |
|---|---|---|
| `VERSION` | 프로토콜 버전 | `V1` |
| `EVENT_TYPE` | 이벤트 종류 | `PRODUCTION` |
| `MACHINE_ID` | DB `machines.machine_id` 값 | `EQ-WIND-01` |

추가 규칙:

- 영문 코드값은 모두 대문자로 전송한다.
- 필드 앞뒤에 공백을 넣지 않는다.
- 값이 없는 선택 필드는 빈 문자열 대신 하이픈 `-`을 사용한다.
- 필드 안에는 쉼표, CR(`\r`), LF(`\n`)를 넣지 않는다.
- 수량은 0 이상의 정수로 전송한다.
- 측정값은 소수점을 `.`으로 표기한다.
- L2는 필드 개수, 코드값, 숫자 범위를 확인한 뒤 Backend로 전달한다.
- 한 TCP 연결에서는 `HELLO`로 등록한 설비의 `MACHINE_ID`만 전송할 수 있다.
- `HELLO`를 제외한 모든 메시지의 `MACHINE_ID`가 등록된 설비와 다르면 L2는 해당 메시지를 폐기한다.

## 4. 기준 코드

### 4.1 공정 및 설비 코드

| 공정 코드 | 공정명 | 설비 코드 |
|---|---|---|
| `OP20` | 코일 권선 | `EQ-WIND-01` |
| `OP30` | 접점 가공/용접 | `EQ-WELD-01` |
| `OP40_OP50` | 자동 조립 | `EQ-ASSY-01` |
| `OP60` | 실링/가스충전 | `EQ-SEAL-01` |
| `OP70` | 최종 검사 | `EQ-TEST-01` |
| `OP80` | 마킹/포장 | `EQ-PACK-01` |

L2는 `MACHINE_ID`와 `PROCESS_CODE`가 위 표처럼 서로 대응하는지 검사한다.

### 4.2 공정 실행 순서

OP20과 OP30은 병렬 선행 공정이며 두 반제품은 OP40_OP50에서 합류한다.

```text
OP20 코일 어셈블리 ───────┐
                          ├→ OP40_OP50 → OP60 → OP70 → OP80
OP30 접점 어셈블리 ───────┘
```

OP40_OP50에서 본체 어셈블리 1개를 생산하려면 OP20의 `SA-COIL-001` 1개와 OP30의 `SA-CONTACT-001` 1개가 필요하다. 다른 자재가 충분한 MVP 기준에서:

```text
OP40_OP50 inputQty = min(OP20 okQty, OP30 okQty)
```

L2는 두 결과를 모아 생산 수량을 계산하지 않고 각 메시지를 독립적으로 Backend에 전달한다. 병렬 공정 완료 여부와 다음 공정 투입 수량은 LOT 및 BOM을 관리하는 Backend가 판단한다.

### 4.3 상태 코드

설비 상태는 DB `machines.status` 기준으로 통일한다.

| 값 | 의미 |
|---|---|
| `IDLE` | 대기 |
| `RUNNING` | 가동 |
| `ERROR` | 이상 |
| `STOPPED` | 정지 |

생산 실적 상태는 우선 다음 값만 사용한다.

| 값 | 의미 |
|---|---|
| `COMPLETED` | 해당 공정 처리 완료 |
| `FAILED` | 해당 공정 처리 실패 |

검사 결과는 SQL의 `inspections.result`에 맞춰 `OK`, `NG`만 사용한다. README의 `PASS`, `FAIL`은 사용하지 않는다.

## 5. 이벤트별 메시지

### 5.1 연결 등록 `HELLO`

L1은 L2에 연결된 후 가장 먼저, 5초 이내에 `HELLO`를 한 번 전송한다. L2는 이 메시지를 이용하여 소켓과 설비를 연결해서 관리한다.

```text
V1,HELLO,MACHINE_ID\n
```

예시:

```text
V1,HELLO,EQ-WIND-01\n
```

검증 규칙:

- `MACHINE_ID`는 DB `machines`에 등록된 설비 코드여야 한다.
- 첫 번째 완성 메시지가 `HELLO`가 아니면 L2는 연결을 종료한다.
- 같은 `MACHINE_ID`가 이미 연결되어 있으면 L2는 새 연결을 거부한다.
- 등록이 끝난 뒤 다른 `MACHINE_ID`를 보내면 메시지를 폐기하고 오류를 기록한다.
- `HELLO`는 Backend로 전달하지 않는다.

### 5.2 연결 확인 `HEARTBEAT`

L1은 생산 여부와 관계없이 연결이 유지되는 동안 5초마다 전송한다.

```text
V1,HEARTBEAT,MACHINE_ID\n
```

예시:

```text
V1,HEARTBEAT,EQ-WIND-01\n
```

L2는 `HEARTBEAT` 또는 다른 정상 메시지를 받을 때마다 해당 연결의 마지막 정상 수신 시각을 갱신한다. 15초 동안 정상 메시지를 하나도 받지 못하면 `COMM_TIMEOUT`으로 처리한다. `HEARTBEAT`는 Backend로 전달하지 않는다.

### 5.3 생산 실적 `PRODUCTION`

공정 처리가 끝났을 때 1건 전송한다.

```text
V1,PRODUCTION,MACHINE_ID,PROCESS_CODE,LOT_NO,INPUT_QTY,OK_QTY,NG_QTY,STATUS\n
```

예시:

```text
V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-20260708-001,100,97,3,COMPLETED\n
```

검증 규칙:

- `INPUT_QTY = OK_QTY + NG_QTY`여야 한다.
- `STATUS`는 `COMPLETED` 또는 `FAILED`여야 한다.
- `LOT_NO`는 Backend에 이미 생성된 생산 LOT여야 한다.
- 정상 제품을 개별 메시지로 전송하지 않고 공정 전체 결과를 요약하여 한 번 전송한다.
- MVP에서는 공정 재실행과 재작업을 제외하므로 LOT·공정별 `PRODUCTION` 요약은 한 건을 기준으로 한다.
- OP20과 OP30은 같은 LOT에 대해 독립적으로 완료 메시지를 보낼 수 있으며 메시지 도착 순서는 보장하지 않는다.

Backend 저장 대상: `production_logs`

### 5.4 검사 결과 `INSPECTION`

검사 항목 1개당 메시지 1건을 전송한다.

```text
V1,INSPECTION,MACHINE_ID,PROCESS_CODE,LOT_NO,ITEM,VALUE,UNIT,LOWER_LIMIT,UPPER_LIMIT,RESULT\n
```

예시:

```text
V1,INSPECTION,EQ-TEST-01,OP70,EVR-LOT-20260708-001,operationVoltage,12.000,V,10.000,14.000,OK\n
```

검증 규칙:

- `VALUE`, `LOWER_LIMIT`, `UPPER_LIMIT`는 숫자여야 한다.
- `LOWER_LIMIT <= UPPER_LIMIT`여야 한다.
- `RESULT`는 `OK` 또는 `NG`여야 한다.
- 한계값이 없는 경우 `LOWER_LIMIT` 또는 `UPPER_LIMIT`에 `-`을 사용할 수 있다.

Backend 저장 대상: `inspections`

### 5.5 제품 불량 `DEFECT`

제품 불량이 발생했을 때 전송한다.

```text
V1,DEFECT,MACHINE_ID,PROCESS_CODE,LOT_NO,DEFECT_CODE,DEFECT_QTY,MESSAGE\n
```

예시:

```text
V1,DEFECT,EQ-WELD-01,OP30,EVR-LOT-20260708-001,WELD_STRENGTH_NG,3,weld_strength_ng\n
```

검증 규칙:

- `DEFECT_QTY`는 1 이상의 정수여야 한다.
- `DEFECT_CODE`는 DB `defect_codes`에 등록된 코드여야 한다.
- `MESSAGE`에는 쉼표와 개행을 사용할 수 없다. 내용이 없으면 `-`을 사용한다.

Backend 저장 대상: `defect_histories`

### 5.6 설비 알람 `ALARM`

설비 자체의 오류가 발생했을 때 전송한다. 제품 불량과 설비 알람을 혼합하지 않는다.

```text
V1,ALARM,MACHINE_ID,ALARM_CODE,ALARM_LEVEL,MESSAGE\n
```

예시:

```text
V1,ALARM,EQ-WIND-01,MOTOR_OVERLOAD,ERROR,motor_overload\n
```

검증 규칙:

- `ALARM_CODE`는 DB `alarm_codes`에 등록된 코드여야 한다.
- 초기 MVP의 `ALARM_LEVEL`은 `WARNING` 또는 `ERROR`를 사용한다.
- 알람 해제는 사용자가 MES에서 처리하므로 L1이 해제 메시지를 보내지 않는다.
- `COMM_DISCONNECTED`, `COMM_TIMEOUT`은 L1 메시지로 전송하지 않는다. L2가 연결 상태를 감지하여 직접 생성한다.

알람 생성 주체는 다음과 같이 구분한다.

| 생성 주체 | 알람 예시 |
|---|---|
| L1 | `MOTOR_OVERLOAD`, `WIRE_BREAK`, `SENSOR_ERROR`, `EMERGENCY_STOP`, `WELD_POWER_ERROR`, `ASSEMBLY_JAM` 등 설비 내부 이상 |
| L2 | `COMM_DISCONNECTED`, `COMM_TIMEOUT` |

L1이 `COMM_DISCONNECTED` 또는 `COMM_TIMEOUT`을 보낸 경우 L2는 잘못된 메시지로 판단하여 폐기한다.

Backend 저장 대상: `machine_alarm_histories`

### 5.7 설비 상태 `MACHINE_STATUS`

설비 상태가 변경될 때만 전송한다.

```text
V1,MACHINE_STATUS,MACHINE_ID,STATUS,LOT_NO,PROCESS_CODE,MESSAGE\n
```

예시:

```text
V1,MACHINE_STATUS,EQ-WIND-01,RUNNING,EVR-LOT-20260708-001,OP20,production_started\n
V1,MACHINE_STATUS,EQ-WIND-01,IDLE,-,OP20,production_finished\n
```

검증 규칙:

- `STATUS`는 `IDLE`, `RUNNING`, `ERROR`, `STOPPED` 중 하나여야 한다.
- 진행 중인 LOT이 없으면 `LOT_NO`에 `-`을 사용한다.
- L2 또는 Backend는 현재 상태와 동일한 중복 상태 이력을 저장하지 않는 것을 권장한다.

Backend 저장 대상: `machines.status`, `machine_status_histories`

### 5.8 L2 작업명령 `COMMAND`

L2가 Backend REST Polling으로 가져온 명령을 해당 설비의 기존 TCP 연결로 전송한다.

```text
V1,COMMAND,COMMAND_ID,COMMAND_TYPE,MACHINE_ID,PROCESS_CODE,LOT_NO,INPUT_QTY\n
```

예시:

```text
V1,COMMAND,101,START,EQ-WIND-01,OP20,EVR-LOT-001,100\n
V1,COMMAND,102,STOP,EQ-WIND-01,OP20,EVR-LOT-001,0\n
V1,COMMAND,103,RESUME,EQ-WIND-01,OP20,EVR-LOT-001,100\n
```

검증 규칙:

- `COMMAND_ID`는 Backend가 생성한 1 이상의 정수이며 명령과 응답을 연결하는 식별자다.
- `COMMAND_TYPE`은 `START`, `STOP`, `RESUME` 중 하나다.
- `MACHINE_ID`와 `PROCESS_CODE`는 서로 대응해야 한다.
- `START`, `RESUME`의 `INPUT_QTY`는 1 이상이어야 한다.
- `STOP`의 `INPUT_QTY`는 `0`을 사용한다.
- L2는 `MACHINE_ID`로 등록된 L1 연결을 찾아 명령을 전송한다.

### 5.9 L1 명령 응답 `COMMAND_ACK`

L1은 명령 수신 및 기본 검증 후 ACK를 L2로 전송한다.

```text
V1,COMMAND_ACK,MACHINE_ID,COMMAND_ID,ACK_STATUS,MESSAGE\n
```

예시:

```text
V1,COMMAND_ACK,EQ-WIND-01,101,ACCEPTED,-\n
V1,COMMAND_ACK,EQ-WIND-01,101,REJECTED,invalid_state\n
```

- `ACK_STATUS`는 `ACCEPTED`, `REJECTED` 중 하나다.
- L2는 ACK를 Backend 명령 상태 API로 전달한다.
- `COMMAND_ID`는 중복 저장 방지용 이벤트 ID가 아니라 작업명령과 ACK를 연결하는 식별자다.

## 6. L2 다중 연결 및 수신 처리 규칙

### 6.1 연결당 작업 스레드

L2는 여러 L1 연결을 다음 방식으로 처리한다.

- 메인 스레드는 서버 소켓의 `accept()`를 반복한다.
- L1 연결을 수락할 때마다 연결 전용 `pthread` 작업 스레드를 하나 생성한다.
- 각 작업 스레드는 자신의 클라이언트 소켓, 설비 ID, 수신 버퍼, 마지막 정상 수신 시각을 독립적으로 관리한다.
- 한 스레드의 잘못된 메시지나 연결 종료가 다른 L1 연결에 영향을 주면 안 된다.
- 작업 스레드 처리는 `collector.c`에 구현하며 별도 파일 추가는 필수가 아니다.
- 종료된 작업 스레드의 소켓, 버퍼, 컨텍스트 메모리는 반드시 정리한다.

```text
L2 메인 스레드: listen() -> accept() 반복
                         |
                         +-> EQ-WIND-01 작업 스레드
                         +-> EQ-WELD-01 작업 스레드
                         +-> EQ-ASSY-01 작업 스레드
                         +-> EQ-SEAL-01 작업 스레드
                         +-> EQ-TEST-01 작업 스레드
                         +-> EQ-PACK-01 작업 스레드
```

### 6.2 메시지 수신 처리

L2는 메시지 한 줄을 다음 순서로 처리한다.

```text
TCP 수신
  -> 수신 버퍼 누적
  -> LF(\n) 기준 한 줄 추출
  -> UTF-8 및 최대 길이 확인
  -> 쉼표 기준 필드 분리
  -> 버전/이벤트 종류/필드 개수 확인
  -> 코드값과 숫자 범위 확인
  -> JSON 변환
  -> Spring Boot REST API 전송
  -> 성공 또는 오류 로그 기록
```

잘못된 한 메시지는 폐기하되 수집기 전체를 종료하지 않는다. 원문에는 개인정보가 없지만, 로그 폭주를 막기 위해 같은 오류가 반복되면 출력 횟수를 제한하는 것을 권장한다.

### 6.3 L2 생성 통신 알람

L2가 연결 이상을 감지하면 TCP 메시지를 만들지 않고 Backend 전송용 JSON을 직접 생성한다.

#### 연결 종료

`recv()`가 `0`을 반환하거나 연결 종료 오류가 발생하면 다음 순서로 처리한다.

```text
L1 연결 종료 감지
  -> 해당 연결의 MACHINE_ID 확인
  -> COMM_DISCONNECTED JSON 생성
  -> POST /api/collector/machine-alarms
  -> MACHINE_STATUS ERROR JSON 생성
  -> POST /api/collector/machine-statuses
  -> 소켓과 연결 컨텍스트 정리
  -> L1 재접속 대기
```

#### 통신 시간 초과

마지막 정상 메시지 수신 후 15초가 지나면 다음 순서로 처리한다.

```text
15초 동안 정상 메시지 없음
  -> COMM_TIMEOUT JSON 생성
  -> POST /api/collector/machine-alarms
  -> MACHINE_STATUS ERROR JSON 생성
  -> POST /api/collector/machine-statuses
  -> 해당 소켓 종료
  -> L1 재접속 대기
```

통신 이상으로 변경하는 설비 상태는 `STOPPED`가 아니라 `ERROR`로 통일한다. `STOPPED`는 계획 정지와 같이 설비가 명시적으로 정지 상태를 전송할 때 사용한다.

중복 알람 방지 규칙:

- 정상 연결에서 끊김 상태로 바뀌는 순간 `COMM_DISCONNECTED`를 한 번만 생성한다.
- 정상 연결에서 Timeout 상태로 바뀌는 순간 `COMM_TIMEOUT`을 한 번만 생성한다.
- Timeout 처리 과정에서 L2가 소켓을 닫아 발생한 연결 종료에 대해서는 `COMM_DISCONNECTED`를 추가 생성하지 않는다.
- L1이 정상적으로 재접속하고 `HELLO` 등록을 완료하면 통신 장애 플래그를 초기화한다.

## 7. L2에서 Backend로 보내는 JSON 예시

REST API 경로는 Backend 담당자와 협의 후 확정한다. 임시 권장 경로는 이벤트별로 분리한다.

이벤트 전달 및 저장 정책:

| 이벤트 | Backend 전달 | 저장 기준 |
|---|---|---|
| `HELLO` | 아니요 | L2 연결 식별에만 사용 |
| `HEARTBEAT` | 아니요 | L2 Timeout 판정에만 사용 |
| `PRODUCTION` | 예 | 공정 완료 요약을 `production_logs`에 저장 |
| `INSPECTION` | 예 | 검사 항목 결과 저장 |
| `DEFECT` | 발생 시 | 제품 불량 상세 저장 |
| `ALARM` | 발생 시 | 설비 알람 상세 저장 |
| `MACHINE_STATUS` | 상태 변경 시 | 설비 현재 상태와 상태 이력 저장 |

최종 완제품 수량은 OP80 `PRODUCTION`의 `okQty`를 사용한다. L2는 최종 수량을 계산하지 않으며 Backend가 LOT에 반영한다.

| 이벤트 | 임시 API 경로 |
|---|---|
| `PRODUCTION` | `POST /api/collector/production-logs` |
| `INSPECTION` | `POST /api/collector/inspections` |
| `DEFECT` | `POST /api/collector/defects` |
| `ALARM` | `POST /api/collector/machine-alarms` |
| `MACHINE_STATUS` | `POST /api/collector/machine-statuses` |

생산 실적 JSON 예시:

```json
{
  "machineId": "EQ-WIND-01",
  "processCode": "OP20",
  "lotNo": "EVR-LOT-20260708-001",
  "inputQty": 100,
  "okQty": 97,
  "ngQty": 3,
  "status": "COMPLETED"
}
```

L2가 직접 생성하는 통신 끊김 알람 JSON 예시:

```json
{
  "machineId": "EQ-WIND-01",
  "alarmCode": "COMM_DISCONNECTED",
  "alarmLevel": "ERROR",
  "message": "L1 connection disconnected"
}
```

Backend의 DTO와 API 구현이 아직 저장소에 반영되지 않았으므로 위 API 경로와 JSON 필드명은 Backend 담당자 구현과 최종 대조하여 확정한다.

- `Content-Type`은 `application/json; charset=UTF-8`을 사용한다.
- HTTP 2xx는 성공으로 처리한다.
- HTTP 4xx는 데이터 오류로 기록한다.
- HTTP 5xx 또는 연결 실패는 서버 장애로 기록한다.
- MVP는 정상 네트워크를 가정하므로 HTTP 요청은 한 번만 전송하고 자동 재시도·디스크 큐·`eventId` 중복 제거를 구현하지 않는다.

## 8. 오류 처리

| 상황 | L2 처리 |
|---|---|
| 알 수 없는 프로토콜 버전 | 메시지 폐기 및 오류 로그 |
| 알 수 없는 이벤트 종류 | 메시지 폐기 및 오류 로그 |
| 필드 개수 불일치 | 메시지 폐기 및 오류 로그 |
| 숫자 변환 실패 | 메시지 폐기 및 오류 로그 |
| 메시지 1,024바이트 초과 | 해당 개행까지 폐기하고 연결 유지 |
| 연결 후 5초 안에 `HELLO` 미수신 | 연결 종료 및 오류 로그 |
| 등록된 설비 ID와 메시지의 설비 ID 불일치 | 메시지 폐기 및 오류 로그 |
| 동일한 설비의 중복 연결 | 새 연결 거부 및 오류 로그 |
| L1 연결 종료 | `COMM_DISCONNECTED`와 설비 `ERROR` 전송 후 소켓 정리 |
| 15초 동안 정상 메시지 미수신 | `COMM_TIMEOUT`과 설비 `ERROR` 전송 후 소켓 정리 |
| Backend HTTP 4xx | 오류 로그, 재전송 없음 |
| Backend HTTP 5xx/연결 실패 | 실패 로그, 재전송 없음 |

## 9. 초안에서 확정이 필요한 항목

아래 항목은 구현 전에 팀 합의가 필요하다.

- [x] 전체 구조를 `L1 -> L2 TCP -> Backend HTTP`로 확정
- [ ] L2 TCP 포트 `9000` 사용 여부
- [x] L1 한 대당 장기 연결 1개 유지 방식 확정
- [x] 연결당 `pthread` 작업 스레드 1개 방식 확정
- [x] `HELLO` 연결 식별 및 `HEARTBEAT` 방식 적용
- [x] Heartbeat 5초, Timeout 15초 기준 적용
- [x] 생산 상태 `COMPLETED`, `FAILED` 적용
- [x] 검사 결과를 SQL 기준 `OK`, `NG`로 적용
- [x] OP20·OP30 병렬 실행 및 OP40_OP50 합류 구조 적용
- [x] 공정 완료 시 `PRODUCTION` 요약 전송, 정상 제품 개별 전송 제외
- [x] 최종 완제품 수량은 OP80 `okQty` 기준
- [x] MVP 재작업 및 공정 재실행 제외
- [x] `WELD_STRENGTH_NG` 불량 코드 적용
- [x] `COMM_DISCONNECTED`, `COMM_TIMEOUT`은 L2가 생성
- [ ] 그 외 `defect_codes`, `alarm_codes`의 실제 사용 목록 확정
- [ ] Backend REST API 경로와 JSON 필드명 확정
- [x] L2 REST Polling 1초 주기로 Backend 작업명령 조회
- [x] L2→L1 `START`, `STOP`, `RESUME` 및 L1→L2 `COMMAND_ACK` 적용
- [x] 정상 네트워크 가정, HTTP 자동 재시도와 `eventId` 제외
- [ ] Linux 기준으로 L1/L2를 실행할지 Windows 호환도 포함할지 확정

## 10. MVP 통합 테스트 예시

1. Backend에서 `EVR-LOT-20260708-001` LOT을 미리 생성한다.
2. L2를 실행하여 TCP 포트 `9000`을 연다.
3. `EQ-WIND-01` L1을 L2에 연결한다.
4. L1이 `HELLO`를 전송하고 이후 5초마다 `HEARTBEAT`를 전송한다.
5. L1이 `MACHINE_STATUS RUNNING`을 전송한다.
6. L1이 `PRODUCTION` 완료 메시지를 전송한다.
7. L1이 `MACHINE_STATUS IDLE`을 전송한다.
8. L2 로그와 Backend HTTP 응답을 확인한다.
9. DB의 `production_logs`, `machines`, `machine_status_histories`를 확인한다.
10. L1 프로세스를 강제 종료하여 `COMM_DISCONNECTED`가 한 번 저장되고 설비 상태가 `ERROR`로 바뀌는지 확인한다.
11. L1 연결은 유지하되 Heartbeat를 중단하여 15초 뒤 `COMM_TIMEOUT`만 한 번 저장되는지 확인한다.
