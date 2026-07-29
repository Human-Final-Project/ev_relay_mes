# 코드단순화/hoomin 공부 가이드

## 1. 이 브랜치의 목적

이 브랜치는 기능을 줄이는 브랜치가 아니다.

- 기존 생산 흐름과 통신 기능은 유지한다.
- 한 줄에 여러 조건을 넣은 코드를 일반 `if`문으로 풀어 쓴다.
- 변수 이름만 읽어도 값의 의미를 알 수 있게 한다.
- 중요한 메서드에는 실행 순서대로 번호를 붙인 주석을 추가한다.
- 통신과 동시성 때문에 제거할 수 없는 코드는 이 문서에서 따로 설명한다.

변경 전 기준 커밋은 `189d527`이다.

```bash
# 특정 파일의 변경 전·후 비교
git diff 189d527 -- backend/src/main/java/com/human/ev_relay_mes/Service/WorkOrderService.java

# 변경 전 파일만 보기
git show 189d527:backend/src/main/java/com/human/ev_relay_mes/Service/WorkOrderService.java

# 현재 공부용 파일은 VS Code에서 같은 경로의 파일을 연다.
# 공부용 변경을 커밋한 뒤에는 아래 명령으로도 볼 수 있다.
git show 코드단순화/hoomin:backend/src/main/java/com/human/ev_relay_mes/Service/WorkOrderService.java
```

## 2. 전체 실행 순서

```text
사용자
  ↓ 작업지시 생성·확정
Frontend
  ↓ REST/JSON
Backend
  ↓ PENDING 작업명령 저장
L2 scheduler
  ↓ Backend 명령 polling
L2 collector
  ↓ TCP/CSV COMMAND
L1 simulator
  ↓ 생산·검사 실행
L1 simulator
  ↓ TCP/CSV 실적·판정·알람·상태
L2 collector
  ↓ REST/JSON
Backend
  ↓ DB 저장·다음 공정 계산
Frontend
```

## 3. 처음 공부할 때 파일 읽는 순서

### Backend

1. `WorkOrderController.java`
   - 사용자의 HTTP 요청을 받는다.
2. `WorkOrderService.java`
   - 작업지시 생성과 확정을 처리한다.
3. `LotService.java`
   - 생산 LOT을 만들고 생산 시작을 시도한다.
4. `ProductionSchedulerService.java`
   - 현재 공정과 설비 상태를 확인해 START 명령 생성을 요청한다.
5. `WorkCommandService.java`
   - START, STOP, RESUME 명령의 상태를 관리한다.
6. `ProductionService.java`
   - L1 생산실적을 저장하고 다음 공정을 결정한다.
7. `InspectionService.java`
   - L1 판정과 측정값을 합쳐 제품별 최종 OK/NG를 결정한다.

### L2

1. `main.c`
   - 네트워크, HTTP 클라이언트, scheduler, collector를 시작한다.
2. `collector.c`
   - L1 TCP 연결과 수신을 담당한다.
3. `protocol.c`
   - L1 CSV 문자열을 C 구조체로 바꾼다.
4. `api_client.c`
   - C 구조체를 JSON으로 바꾸고 Backend REST API를 호출한다.
5. `scheduler.c`
   - Backend 명령을 polling하고 L1으로 보낸다.
6. `command_json.c`
   - Backend 명령 JSON을 C 구조체로 바꾼다.

### L1

1. `main.c`
   - 실행할 설비와 오류 발생 옵션을 읽는다.
2. `client.c`
   - L2 연결, COMMAND 수신, 이벤트 전송, 재접속을 담당한다.
3. `protocol.c`
   - TCP CSV 문자열과 C 구조체 사이를 변환한다.
4. `machine_runtime.c`
   - START, STOP, RESUME와 제품 생산 상태를 관리한다.
5. `device_config.c`
   - 6개 설비의 공정과 발생 가능한 알람을 정의한다.

## 4. Backend 작업지시 흐름

### 작업지시 생성

`WorkOrderService.createWorkOrder`

```text
1. 계획 시작·종료 시각 검사
2. 완제품 품목 조회
3. 작업지시 생성자 조회
4. BOM 기준 자재 수량 확인
5. 작업지시 저장
6. 화면에 보낼 응답 생성
```

자재는 이 시점에 차감하지 않는다. 생산을 실제로 시작할 때 다시 확인한 뒤 차감한다.

### 작업지시 확정과 생산 시작 요청

`WorkOrderService.releaseAndRequestStart`

```text
1. 작업지시 잠금 조회
2. 이미 RUNNING이면 현재 결과 반환
3. 이미 RELEASED이면 빠진 최초 LOT만 복구
4. CREATED이면 RELEASED로 변경
5. 최초 LOT 생성
6. 생산 시작 시도
```

메서드 이름에 `Request`가 들어가는 이유는 자재가 부족하면 즉시 생산이 시작되지 않을 수 있기 때문이다.

### 최초 LOT 생산 시작

`LotService.createInitialLotAndTryStartProduction`

```text
1. 작업지시가 RELEASED인지 확인
2. 최초 LOT 중복 확인
3. 목표 수량으로 LOT 저장
4. 자재 차감 시도
5. 성공하면 LOT과 작업지시를 RUNNING으로 변경
6. 비어 있는 설비에 작업 배정 요청
```

## 5. 병렬 공정과 다음 공정 계산

OP20과 OP30은 동시에 시작한다.

```text
           ┌─ OP20 ─┐
최초 LOT ──┤        ├─ OP40_OP50 → OP60 → OP70 → OP80
           └─ OP30 ─┘
```

OP40_OP50 투입 수량은 두 병렬 공정 양품 수량 중 작은 값이다.

```text
OP20 양품 = 97
OP30 양품 = 96

OP40_OP50 투입 수량
= min(97, 96)
= 96
```

그 이후 공정은 바로 이전 공정의 양품 수량을 투입 수량으로 사용한다.

## 6. L1 코드 흐름

### 명령 수신

```text
TCP 데이터 수신
→ '\n'을 만날 때까지 수신 버퍼에 저장
→ protocol.c에서 COMMAND 해석
→ machine_runtime.c에 명령 전달
```

### START

```text
현재 상태가 IDLE인지 확인
→ LOT 번호와 목표 수량 저장
→ 처리 수량 0으로 초기화
→ RUNNING 변경
→ COMMAND_ACK(ACCEPTED)
→ MACHINE_STATUS(RUNNING)
```

### 제품 한 개 처리

```text
processedQty 1 증가
→ JUDGMENT 생성
→ 해당 공정의 INSPECTION 측정값 생성
→ 설정된 수량에서 알람 발생 여부 확인
→ 목표 수량이면 IDLE 변경
```

### 오류와 재개

```text
오류 발생
→ 미전송 실적 전송
→ ALARM(ERROR)
→ MACHINE_STATUS(ERROR)
→ ERROR_PAUSED 상태 유지

RESUME 수신
→ LOT와 남은 수량 확인
→ RUNNING 변경
→ 남은 수량부터 생산
```

## 7. L2 코드 흐름

### L1 이벤트 수신

```text
L1 연결 수락
→ 연결 전용 스레드 생성
→ HELLO 확인 및 machineId 등록
→ '\n' 단위로 메시지 분리
→ protocol.c에서 CSV 해석
→ api_client.c에서 JSON 생성
→ Backend POST
```

### Backend 명령 전달

```text
scheduler 1회 실행
→ 연결된 설비 확인
→ GET /api/collector/commands/pending
→ command_json.c에서 JSON 해석
→ protocol.c에서 TCP COMMAND 생성
→ 해당 L1 소켓으로 전송
```

### 통신 오류

```text
L1 연결 종료
→ L2가 COMM_DISCONNECTED 생성
→ Backend 알람 API 전송
→ Backend 설비 상태 API에 ERROR 전송

정해진 시간 동안 메시지 없음
→ L2가 COMM_TIMEOUT 생성
→ Backend 알람·설비 상태 전송
```

## 8. 변경 전·후 코드 읽기 예시

### 여러 검사 메시지 생성

변경 전에는 `||`와 삼항연산자로 세 번의 함수 호출 결과를 한 번에 계산했다.

```c
return first() != 0 || second() != 0 || third() != 0 ? -1 : 0;
```

공부용 코드는 각 단계에서 실패를 확인한다.

```c
if (first() != 0) {
    return -1;
}
if (second() != 0) {
    return -1;
}
if (third() != 0) {
    return -1;
}
return 0;
```

동작은 같지만 어느 메시지 생성 중 실패했는지 따라가기 쉽다.

### 조건에 따른 값 선택

변경 전:

```java
String status = completed ? "COMPLETED" : "RUNNING";
```

이처럼 짧고 뜻이 분명한 삼항연산자는 일부 남겨 둘 수 있다.
조건 안에서 다시 삼항연산자를 사용하거나 함수 호출을 여러 개 연결한 경우는 일반 `if`문으로 바꾼다.

## 9. 제거하면 안 되는 어려운 부분

### `@Transactional`

여러 DB 변경을 하나의 작업으로 묶는다.

예를 들어 작업지시만 RELEASED로 바뀌고 LOT 저장에 실패하면 전체 변경을 되돌려야 한다.

### `findByIdForUpdate`

DB 행을 잠금 조회한다.

두 사용자가 같은 작업지시를 동시에 확정하거나 두 scheduler가 같은 설비에 명령을 동시에 배정하는 것을 막는다.

### 중복 요청 방지

TCP와 HTTP에서는 응답이 늦어 같은 요청이 다시 올 수 있다.

- Backend 이벤트: `eventId`
- L1 명령: `commandId`
- 최초 LOT: 작업지시별 LOT 존재 여부

위 값을 확인해 같은 데이터가 두 번 저장되지 않게 한다.

### 연결당 스레드

L1 설비 6대가 동시에 메시지를 보낼 수 있다.
L2가 설비 하나의 수신을 기다리는 동안 다른 설비 수신이 멈추지 않게 연결마다 스레드 하나를 사용한다.

### 수신 버퍼

TCP는 메시지 한 줄이 한 번에 도착한다는 보장이 없다.

```text
첫 수신: V1,ALAR
두 번째 수신: M,EQ-WIND-01,...
```

따라서 `\n`이 도착할 때까지 버퍼에 합친 후 파싱해야 한다.

### mutex

여러 L1 스레드와 scheduler가 연결 목록을 동시에 읽고 수정할 수 있다.
연결 목록이 손상되지 않도록 한 번에 한 스레드만 수정하게 한다.

### HTTP 재시도 큐

Backend가 잠시 응답하지 않을 때 생산 이벤트를 바로 버리면 실적이 유실된다.
재시도 후에도 실패한 이벤트를 파일 큐에 남겼다가 다시 전송한다.

### CSV·JSON 파서의 길이 검사

C 문자열은 저장 공간보다 긴 값을 복사하면 프로그램이 손상될 수 있다.
따라서 필드 수, 문자열 길이, 숫자 범위 검사는 삭제하면 안 된다.

## 10. 발표할 때 설명할 핵심 문장

### Backend

“작업지시를 확정하면 최초 LOT을 만들고 자재 차감을 시도합니다. 자재가 준비되면 LOT을 RUNNING으로 바꾸고 scheduler가 비어 있는 설비에 START 명령을 생성합니다.”

### L2

“L2는 L1의 TCP CSV 메시지를 구조체로 파싱한 뒤 JSON으로 변환해 Backend REST API에 전달합니다. 반대로 Backend 명령은 polling해서 해당 L1 TCP 연결로 전달합니다.”

### L1

“L1은 START 명령을 받으면 목표 수량을 저장하고 제품을 한 개씩 처리합니다. 제품별 판정과 측정값을 L2에 전송하며, 오류가 발생하면 현재 수량을 유지한 채 RESUME을 기다립니다.”

## 11. 기능 보존 확인 방법

```bash
# Backend
cd backend
gradlew.bat test

# L1
cd embedded-c/mes_L1
mingw32-make clean
mingw32-make test

# L2
cd embedded-c/mes_collector
mingw32-make clean
mingw32-make test
```

전체 테스트 수만 보는 것이 아니라 변경한 흐름과 직접 관련된 테스트가 통과했는지도 확인한다.

- 작업지시 확정과 최초 LOT 중복 방지
- OP20·OP30 병렬 시작
- 다음 공정 투입 수량
- STOP·RESUME
- TCP 메시지 분리
- 명령 JSON 해석
- HTTP 재시도 큐
- 통신 종료·타임아웃 알람
