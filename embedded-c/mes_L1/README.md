# L1 Equipment Simulator

EV Relay MES의 설비 한 대를 표현하는 C 시뮬레이터다. 하나의 실행 프로세스는 하나의 `MACHINE_ID`와 하나의 `PROCESS_CODE`만 사용한다.

## Machine mapping

| MACHINE_ID | PROCESS_CODE |
|---|---|
| `EQ-WIND-01` | `OP20` |
| `EQ-WELD-01` | `OP30` |
| `EQ-ASSY-01` | `OP40_OP50` |
| `EQ-SEAL-01` | `OP60` |
| `EQ-TEST-01` | `OP70` |
| `EQ-PACK-01` | `OP80` |

## 일반 생산 공정

OP20, OP30, OP40_OP50, OP60, OP80은 다음 생산실적을 전송한다.

```text
V1,PRODUCTION,machineId,processCode,lotNo,inputQty,okQty,ngQty,status
```

제품을 1초마다 한 개씩 처리하며, 처리 중에는 현재까지의 누적 수량을
`PRODUCTION(RUNNING)`으로 전송한다. 목표 수량을 처리한 후에는 최종 누적
`PRODUCTION(COMPLETED)`와 `MACHINE_STATUS(IDLE)`을 전송한다.
LOT·설비·제품 순번 기반의 결정적 의사난수로 제품별 NG를 약 3% 발생시키며,
재전송 시에도 같은 제품은 항상 같은 판정을 유지한다.

## OP70 검사 공정

OP70은 `PRODUCTION`으로 OK/NG를 보내지 않는다. 제품 한 개마다 다음 세 측정값을 전송한다.

```text
OPERATION_VOLTAGE   V
COIL_RESISTANCE     OHM
CONTACT_RESISTANCE  mOHM
```

TCP 형식:

```text
V1,INSPECTION,machineId,processCode,lotNo,unitSeq,inspectionItem,measuredValue,unit
```

`unitSeq`는 같은 LOT 안에서 검사한 제품 순번이다. Backend가 검사 기준으로 판정하고 OP70의 최종 OK/NG 수량을 집계한다. 정상 측정값은 검사 기준 안에서 생성하고, 약 3%의 제품은 한 측정값을 기준 밖으로 생성한다.

한 제품의 세 번째 측정값까지 L2 전송이 완료되지 않은 상태에서 TCP 연결이 끊어지면, 재접속 후 같은 `unitSeq`의 세 측정값을 다시 보낸다. Backend의 업무 키 중복 방지로 이미 저장된 항목은 중복 등록되지 않는다.

## 오류와 RESUME

일반 공정 오류:

```text
최신 누적 PRODUCTION(RUNNING)
→ ALARM(ERROR)
→ MACHINE_STATUS(ERROR)
→ RESUME
→ 남은 수량 처리
→ PRODUCTION(COMPLETED)
```

OP70 오류:

```text
오류 직전 제품의 INSPECTION 3개
→ ALARM(ERROR)
→ MACHINE_STATUS(ERROR)
→ RESUME
→ 다음 unitSeq부터 검사 계속
→ 마지막 제품 후 MACHINE_STATUS(IDLE)
```

같은 `commandId`가 재전송되면 작업 상태를 다시 초기화하지 않고 기존 `COMMAND_ACK`만 반복한다.

## Build

Windows MSYS2 UCRT64 또는 MinGW:

```bash
mingw32-make clean
mingw32-make
```

일반 `make`가 설정되어 있으면:

```bash
make clean
make
```

Linux:

```bash
make clean
make
```

## Run

지원 설비 목록:

```bash
./l1_simulator.exe --list
```

OP70 실행:

```bash
./l1_simulator.exe EQ-TEST-01
```

L2 주소와 오류 발생 수량까지 지정:

```bash
./l1_simulator.exe EQ-TEST-01 127.0.0.1 9000 3
```

마지막 값이 `3`이면 세 번째 제품 처리 후 한 번 오류를 발생시킨다. `0` 또는 생략은 오류 주입 없음이다.

프로그램 종료는 `Ctrl+C`를 사용한다.

## Test

```bash
make test
```

현재 테스트 결과:

```text
L1 protocol             93 checks
L1-L2 contract          82 checks
L1 client               24 checks
L1 machine runtime      234 checks
```

검증 범위에는 다음이 포함된다.

- 6개 설비·공정 고정 매핑
- `HELLO`, `HEARTBEAT`, `COMMAND_ACK`
- `START`, `STOP`, `RESUME`
- 일반 공정 부분 실적과 오류 재개
- OP70 제품별 세 측정값
- OP70에서 `PRODUCTION` 미전송
- OP70 오류 후 `unitSeq` 연속성
- 전송이 끝나지 않은 검사 제품 재전송
- 동일 `commandId` 중복 실행 방지
## Hybrid quality events (2026-07-22)

The simulator now sends one `JUDGMENT` event per unit for every process. OP20,
OP30, OP60, and OP70 additionally send raw `INSPECTION` measurements. L1 does
not send final OK/NG production totals for these unit events; Backend combines
the L1 judgment and all required measurement judgments and creates the single
completed `production_logs` row.

```text
V1,JUDGMENT,machineId,processCode,lotNo,unitSeq,OK,-,automatic_judgment_ok
V1,INSPECTION,machineId,processCode,lotNo,unitSeq,item,value,unit
```

An L1-side NG includes its process defect code. A measurement-side NG is mapped
to a defect code by Backend. Across each 100-unit sequence, the combined final
NG selection remains approximately 3% (three selected units).
