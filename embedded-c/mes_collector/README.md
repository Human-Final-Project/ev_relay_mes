# L2 MES Collector

L1 설비 시뮬레이터의 TCP 메시지를 수집하고 Spring Boot MES에 REST API로 전달하는 C 프로그램이다.

기본 프로젝트, TCP 프로토콜 파서, 단일 L1 TCP 연결, 다중 설비 수집과 Backend REST API 전송이 구현되어 있다. 이후 기능은 아래 순서로 구현한다.

1. 통신 Timeout 및 오류 처리
2. Backend 명령 Polling과 전체 통합 검증

현재 L2는 TCP 서버로 `0.0.0.0:9000`을 열고 최대 6대의 L1을 동시에 처리한다. 각 연결은 독립 작업 스레드와 수신 버퍼를 사용한다. Linux에서는 연결당 detached `pthread`, 현재 Windows MinGW 빌드에서는 같은 구조의 Win32 thread를 사용한다. 연결 후 5초 안에 첫 완성 메시지로 `HELLO`를 받아야 하며 같은 `MACHINE_ID`의 중복 연결은 거부한다. 등록 이후에는 해당 연결과 같은 `MACHINE_ID`의 메시지만 처리한다.

생산 흐름은 OP20과 OP30이 병렬로 실행된 후 OP40_OP50에서 합류한다. L2는 두 공정 결과를 각각 독립적으로 Backend에 전달하며 조립 가능 수량은 계산하지 않는다. 공정 완료 `PRODUCTION`은 항상 전달하고, `DEFECT`와 `ALARM`은 발생할 때만 전달한다. `HELLO`와 `HEARTBEAT`는 L2 내부 연결 관리에만 사용한다.

L2는 Backend의 `127.0.0.1:8111`로 다음 이벤트를 HTTP POST한다.

| L1 이벤트 | Backend 경로 |
|---|---|
| `PRODUCTION` | `/api/collector/production-logs` |
| `INSPECTION` | `/api/collector/inspections` |
| `DEFECT` | `/api/collector/defects` |
| `ALARM` | `/api/collector/machine-alarms` |
| `MACHINE_STATUS` | `/api/collector/machine-statuses` |
| `COMMAND_ACK` | `/api/collector/command-acks` |

- `Content-Type`은 `application/json; charset=UTF-8`이다.
- `HELLO`와 `HEARTBEAT`는 Backend로 전송하지 않는다.
- TCP 선택값 `-`는 Backend의 선택 필드에서 JSON `null`로 변환한다.
- TCP 알람 수준 `WARNING`은 Backend DTO 값 `WARN`으로 변환한다.
- HTTP 요청은 한 번만 보내고 실패해도 자동 재시도하지 않는다.
- TCP 생산 상태 `FAILED`는 현재 Backend DTO가 허용하지 않는다. 이 값을 실제로 사용하기 전 Backend 담당자와 상태 계약을 맞춰야 하며, 현재 전송하면 HTTP 400으로 기록된다.

작업명령은 L2가 Backend REST API를 1초마다 Polling하여 가져오고, 기존 L1 TCP 연결로 `START`, `STOP`, `RESUME` 명령을 전달한다. MVP는 정상 네트워크를 가정하므로 Backend HTTP 요청은 자동 재시도하지 않는다.

통신 규칙은 [`../../docs/tcp-protocol.md`](../../docs/tcp-protocol.md)를 따른다.

## Build

Linux에서는 `pthread`, Windows MinGW에서는 Win32 thread 호환 계층으로 빌드한다.

```bash
make
./mes_collector
```

Windows MinGW에서는 다음과 같이 실행할 수 있다.

```powershell
mingw32-make
.\mes_collector.exe
```

프로토콜 단위 테스트는 다음 명령으로 실행한다.

```bash
make test
```

Windows MinGW:

```powershell
mingw32-make test
```

테스트에는 이벤트별 JSON·경로 변환, JSON 문자열 이스케이프, 선택값의 `null` 변환과 로컬 HTTP 서버를 이용한 실제 POST/201 응답 검증이 포함된다.
