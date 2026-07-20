# L2 MES Collector

L1 설비 시뮬레이터의 TCP 메시지를 수집하고 Spring Boot MES에 REST API로 전달하는 C 프로그램이다.

## 구현된 기능

- `0.0.0.0:9000` TCP 서버
- 최대 6대 L1 동시 연결
- 연결별 독립 작업 스레드와 수신 버퍼
- 최초 `HELLO` 등록 및 중복 `MACHINE_ID` 차단
- 15초 무응답 시 `COMM_TIMEOUT`
- 연결 종료 시 `COMM_DISCONNECTED`
- L1 업무 이벤트를 Backend JSON으로 변환
- HTTP 재시도, 디스크 큐, 자동 복구 전송
- 연결된 설비별 Backend 작업명령 1초 Polling
- `START`, `STOP`, `RESUME`를 기존 L1 TCP 연결로 전달

## 공정 이벤트

일반 공정인 OP20, OP30, OP40_OP50, OP60, OP80은 `PRODUCTION`으로 생산실적을 보낸다.

OP70은 예외다. OP70 L1은 제품별 측정값을 `INSPECTION`으로 보내고 `PRODUCTION`은 보내지 않는다. Backend가 검사 기준과 비교해 제품별 OK/NG를 판정하고 OP70 생산실적을 집계한다.

| L1 이벤트 | Backend 경로 |
|---|---|
| `PRODUCTION` | `/api/collector/production-logs` |
| `INSPECTION` | `/api/collector/inspections` |
| `DEFECT` | `/api/collector/defects` |
| `ALARM` | `/api/collector/machine-alarms` |
| `MACHINE_STATUS` | `/api/collector/machine-statuses` |
| `COMMAND_ACK` | `/api/collector/command-acks` |

`HELLO`와 `HEARTBEAT`는 L2 연결 관리에만 사용하며 Backend로 보내지 않는다.

## Backend 작업명령 Polling

L2는 현재 연결된 설비만 대상으로 1초마다 다음 API를 호출한다.

```http
GET /api/collector/commands/pending?machineId=EQ-TEST-01
```

Backend에서 받은 명령은 다음 TCP 형식으로 해당 L1에 전달한다.

```text
V1,COMMAND,commandId,commandType,machineId,processCode,lotNo,inputQty
```

L1 전송 직전에 연결이 끊기거나 소켓 전송이 실패하면 L2가 다음 API를 호출해 명령을 다시 `PENDING`으로 돌린다.

```http
POST /api/collector/commands/{commandId}/release?machineId=EQ-TEST-01
```

Backend는 ACK 없이 10초 이상 `DISPATCHED`에 머문 명령도 다음 Polling 때 다시 `PENDING`으로 되돌린다. L1은 같은 `commandId`를 다시 받으면 작업을 재실행하지 않고 기존 ACK만 다시 보낸다.

## HTTP 데이터 유실 방지

생산·검사·불량·알람·설비 상태 이벤트에는 L2가 `eventId`를 생성한다.

```text
L1 이벤트 수신
→ eventId 생성
→ Backend HTTP 전송
→ 연결 실패·타임아웃·잘못된 응답·5xx 재시도
→ 계속 실패하면 mes_http_retry.queue 저장
→ Backend 복구 후 백그라운드 재전송
```

HTTP 4xx처럼 재전송만으로 해결되지 않는 요청은 다음 파일에 저장한다.

```text
mes_http_dead_letter.queue
```

큐 파일은 L2를 실행한 현재 폴더에 생성된다. 따라서 `mes_collector` 폴더에서 실행하는 것이 좋다.

## Build and Run

Windows MSYS2 UCRT64 또는 MinGW:

```bash
mingw32-make clean
mingw32-make
./mes_collector.exe
```

일반 `make`가 설정되어 있으면 다음 명령도 사용할 수 있다.

```bash
make clean
make
./mes_collector.exe
```

Linux:

```bash
make clean
make
./mes_collector
```

## Test

```bash
make test
```

검증 범위:

- TCP 프로토콜 검증
- 다중 L1 연결 레지스트리
- 이벤트별 Backend JSON 변환
- OP70 `unitSeq`와 측정값 JSON
- Backend 명령 JSON 파싱
- chunked HTTP 명령 응답 처리
- 명령을 지정 설비로 전달
- HTTP 실패 이벤트 영속 큐 저장 및 복구
- `COMM_TIMEOUT`, `COMM_DISCONNECTED`

현재 테스트 결과:

```text
Protocol                 76 checks
Collector                73 checks
Connection registry      37 checks
API client               77 checks
Durable queue replay     PASS
```
