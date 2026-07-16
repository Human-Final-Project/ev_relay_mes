# L1 Equipment Simulator

EV Relay MES의 공정 설비 한 대를 표현하는 C 시뮬레이터다. 하나의 실행 프로세스는 하나의 `MACHINE_ID`와 하나의 `PROCESS_CODE`만 사용한다.

현재 완료 범위:

1. Windows MinGW와 Linux에서 빌드 가능한 기본 프로젝트
2. 6개 설비 중 하나를 실행 인자로 선택하는 단일 설비 구조
3. TCP 프로토콜 v0.4 송신 메시지 생성기와 수신 `COMMAND` 파서
4. 단일 L2 TCP 연결, 최초 `HELLO` 전송, 수신 `COMMAND` LF 분리와 `COMMAND_ACK`
5. 연결 중 5초 주기 `HEARTBEAT`, 연결 실패·종료 후 3초 주기 재접속
6. 설비별 독립 생산 상태 머신, 오류 시 부분 실적 보존과 `RESUME` 재개

각 실행 프로세스는 100ms마다 제품 1개를 처리한다. 오류를 지정하지 않으면 목표 수량까지 정상 완료한다.

## Machine mapping

| MACHINE_ID | PROCESS_CODE |
|---|---|
| `EQ-WIND-01` | `OP20` |
| `EQ-WELD-01` | `OP30` |
| `EQ-ASSY-01` | `OP40_OP50` |
| `EQ-SEAL-01` | `OP60` |
| `EQ-TEST-01` | `OP70` |
| `EQ-PACK-01` | `OP80` |

오류 주입 시 사용하는 설비별 알람:

| MACHINE_ID | ALARM_CODE |
|---|---|
| `EQ-WIND-01` | `WIRE_BREAK` |
| `EQ-WELD-01` | `WELD_POWER_ERROR` |
| `EQ-ASSY-01` | `ASSEMBLY_JAM` |
| `EQ-SEAL-01` | `CHAMBER_PRESSURE_ERROR` |
| `EQ-TEST-01` | `TEST_PROBE_ERROR` |
| `EQ-PACK-01` | `LABEL_PRINTER_ERROR` |

L1 한 프로세스가 다른 설비의 메시지를 함께 전송하면 안 된다. 공정 연결과 다음 공정 투입 수량은 Backend가 판단한다.

## Build

Windows MinGW:

```powershell
mingw32-make
```

Linux:

```bash
make
```

## Run

지원 설비 목록:

```powershell
.\l1_simulator.exe --list
```

기본 L2 주소(`127.0.0.1:9000`)에 단일 설비 연결:

```powershell
.\l1_simulator.exe EQ-WIND-01
```

L2 주소와 포트를 직접 지정:

```powershell
.\l1_simulator.exe EQ-WIND-01 192.168.0.10 9000
```

목표 생산 중 40개 처리 후 오류를 한 번 발생시키는 예시:

```powershell
.\l1_simulator.exe EQ-WIND-01 127.0.0.1 9000 40
```

마지막 인자 `ERROR_AFTER_QTY`를 생략하거나 `0`으로 지정하면 오류를 주입하지 않는다. 오류가 발생하면 다음 순서를 따른다.

```text
부분 PRODUCTION(RUNNING)
→ ALARM(ERROR)
→ MACHINE_STATUS(ERROR)
→ RESUME 대기
→ COMMAND_ACK(ACCEPTED)
→ MACHINE_STATUS(RUNNING)
→ 남은 수량 처리
→ PRODUCTION(COMPLETED)
→ MACHINE_STATUS(IDLE)
```

나머지 설비도 같은 실행 파일에 각 `MACHINE_ID`를 전달한다. `PROCESS_CODE`는 설비 ID에 따라 자동으로 결정되므로 실행 인자로 따로 받지 않는다.

실행 후에는 다음 규칙으로 연결을 유지한다.

- 연결 직후 해당 설비의 `HELLO`를 전송한다.
- 연결 중에는 생산 여부와 관계없이 5초마다 `HEARTBEAT`를 전송한다.
- L2가 연결을 닫거나 송수신 오류가 발생하면 소켓을 닫고 3초 후 재접속한다.
- 재접속에 성공하면 새 연결에서 `HELLO`부터 다시 전송한다.
- 프로그램은 연결 실패만으로 종료하지 않는다. 종료할 때는 터미널에서 `Ctrl+C`를 누른다.

## Test

```powershell
mingw32-make test
```

테스트 범위:

- 6개 설비·공정 고정 매핑
- `HELLO`, `HEARTBEAT`
- `PRODUCTION`, `INSPECTION`, `DEFECT`, `ALARM`
- `MACHINE_STATUS`, `COMMAND_ACK`
- `START`, `STOP`, `RESUME` COMMAND 파싱
- 분할 수신 및 한 번에 여러 개 수신된 `COMMAND`의 LF 메시지 분리
- 선택한 설비와 다른 `MACHINE_ID` 명령 거부
- 버전, LF, 필드 개수, 숫자 범위, 설비·공정 불일치 검증
- L1이 생성한 8종 메시지를 실제 L2 파서로 검증
- L2가 생성한 `COMMAND`를 실제 L1 파서로 검증
- 오류 시 부분 실적·알람·ERROR 상태의 전송 순서
- 현재 LOT·목표·처리·보고 수량 메모리 유지
- 잘못된 LOT·남은 수량의 `RESUME` 거부
- 재개 후 중단 수량 다음부터 처리하고 누적 목표 수량 완료

실제 L1·L2 프로세스를 함께 실행하는 수동 통합 검증 항목:

- 연결 후 5초 주기 `HEARTBEAT`를 L2가 수신하는지 확인
- L2 종료 시 L1이 3초 후 재접속을 시도하는지 확인
- L2 재실행 후 L1이 새 연결에서 `HELLO`를 다시 보내는지 확인

통신 명세는 [`../../docs/tcp-protocol.md`](../../docs/tcp-protocol.md)를 따른다.
