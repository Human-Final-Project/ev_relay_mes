# L1 Equipment Simulator

EV Relay MES의 공정 설비 한 대를 표현하는 C 시뮬레이터다. 하나의 실행 프로세스는 하나의 `MACHINE_ID`와 하나의 `PROCESS_CODE`만 사용한다.

현재 완료 범위:

1. Windows MinGW와 Linux에서 빌드 가능한 기본 프로젝트
2. 6개 설비 중 하나를 실행 인자로 선택하는 단일 설비 구조
3. TCP 프로토콜 v0.4 송신 메시지 생성기와 수신 `COMMAND` 파서

TCP 연결, Heartbeat 스레드, 생산 상태 머신은 다음 단계에서 구현한다.

## Machine mapping

| MACHINE_ID | PROCESS_CODE |
|---|---|
| `EQ-WIND-01` | `OP20` |
| `EQ-WELD-01` | `OP30` |
| `EQ-ASSY-01` | `OP40_OP50` |
| `EQ-SEAL-01` | `OP60` |
| `EQ-TEST-01` | `OP70` |
| `EQ-PACK-01` | `OP80` |

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

단일 설비 선택:

```powershell
.\l1_simulator.exe EQ-WIND-01
```

나머지 설비도 같은 실행 파일에 각 `MACHINE_ID`를 전달한다. `PROCESS_CODE`는 설비 ID에 따라 자동으로 결정되므로 실행 인자로 따로 받지 않는다.

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
- 버전, LF, 필드 개수, 숫자 범위, 설비·공정 불일치 검증
- L1이 생성한 8종 메시지를 실제 L2 파서로 검증
- L2가 생성한 `COMMAND`를 실제 L1 파서로 검증

통신 명세는 [`../../docs/tcp-protocol.md`](../../docs/tcp-protocol.md)를 따른다.
