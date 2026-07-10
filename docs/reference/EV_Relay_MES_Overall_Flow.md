# EV Relay MES 전체 흐름 정리

## 1. 문서 목적

이 문서는 EV Relay MES 프로젝트의 전체 동작 흐름을 정리하기 위한 문서이다.

기존 프로젝트 구조가 단순히 `C 장비 시뮬레이터 → Backend → DB → Frontend` 흐름이었다면, 본 문서에서는 실제 MES 구조에 가깝게 다음 흐름을 기준으로 정리한다.

```text
Frontend
→ Backend
→ L2 Controller / Collector
→ L1 Equipment Simulator
→ L2 결과 취합
→ Backend 저장
→ Frontend Dashboard 출력
```

핵심 목표는 다음과 같다.

- 사용자가 Frontend에서 자재 입고와 작업지시를 수행한다.
- Backend는 작업지시를 DB에 저장하고 L2 장비로 전달한다.
- L2는 각 L1 설비에 공정별 작업을 지시한다.
- L1 설비는 생산 결과, OK/NG 수량, 설비 알람 로그를 L2에 전달한다.
- L2는 정보를 취합하여 Backend로 전달한다.
- Backend는 DTO, Service, Entity, JPA를 통해 DB에 저장한다.
- Frontend는 API를 통해 LOT 진행 상태, 생산 실적, 불량, 알람 정보를 조회하고 대시보드에 출력한다.

---

## 2. 전체 시스템 구성

```text
[ React Frontend ]
        |
        | 자재 입고 / 작업지시 / 조회 요청
        v
[ Spring Boot Backend ]
        |
        | TCP/IP 작업지시 전송
        v
[ C L2 Controller / Collector ]
        |
        | TCP/IP 공정별 작업 지시
        v
[ C L1 Equipment Simulators ]
        |
        | 생산 결과 / 설비 로그 / 알람 전달
        v
[ C L2 Controller / Collector ]
        |
        | 결과 취합 후 Backend 전달
        v
[ Spring Boot Backend ]
        |
        | DTO → Service → Entity → JPA
        v
[ MySQL Database ]
        |
        | REST API 조회
        v
[ React Dashboard ]
```

---

## 3. 주요 역할 구분

| 구분 | 역할 |
|---|---|
| Frontend | 사용자 로그인, 자재 입고, 작업지시, LOT 조회, 대시보드 출력 |
| Backend | MES 업무 로직 처리, 작업지시 저장, L2 통신, DB 저장, API 제공 |
| L2 | Backend와 L1 사이의 중간 제어기/수집기 역할 |
| L1 | 실제 설비 역할을 하는 C 기반 장비 시뮬레이터 |
| Database | 사용자, 자재, 작업지시, LOT, 생산결과, 불량, 알람, 설비상태 저장 |

---

## 4. 기본 업무 흐름

## 4.1 사용자 등록 및 로그인

```text
0. 관리자가 사용자 등록
   ↓
1. 사용자가 로그인
   ↓
2. 이후 모든 과정은 로그인된 사용자 기준으로 진행
```

### 설명

- 최초 관리자 계정은 초기 SQL 또는 하드코딩 방식으로 등록한다.
- 일반 사용자는 관리자가 등록하고 권한을 부여한다.
- 로그인 이후 사용자는 권한에 따라 자재 입고, 작업지시, 조회, 알람 조치 등을 수행한다.

---

## 4.2 자재 입고 흐름

```text
1. 사용자가 Frontend에서 자재 입고 등록
   ↓
2. Frontend가 Backend API 호출
   ↓
3. Backend가 자재 LOT 생성
   ↓
4. MySQL에 자재 입고 정보 저장
   ↓
5. Frontend에서 자재 입고 내역 조회
```

### 예시

```text
RM-CU-001 자재 100개 입고
→ MAT-LOT-20260709-001 생성
→ status = AVAILABLE
```

### 관련 테이블 예시

```text
material_lots
items
members
```

---

## 4.3 작업지시 생성 흐름

```text
1. 사용자가 Frontend에서 작업지시 생성
   예: EV Relay 완성품 10개 제작
   ↓
2. Frontend가 Backend API 호출
   ↓
3. Backend가 production_orders 저장
   ↓
4. Backend가 production_lots 생성
   ↓
5. Backend가 L2로 작업지시 메시지 전송
```

### 작업지시 예시

```text
작업지시 번호: WO-20260709-001
생산 LOT 번호: LOT-20260709-001
생산 품목: FG-EVR-001
목표 수량: 10개
```

### 관련 테이블 예시

```text
production_orders
production_lots
items
members
```

---

## 4.4 Backend → L2 작업지시 흐름

```text
1. Backend가 작업지시 정보를 확인
   ↓
2. Backend TCP Client 또는 TCP Sender가 L2로 명령 전송
   ↓
3. L2가 작업지시 메시지 수신
   ↓
4. L2가 LOT 번호, 품목, 목표 수량, 공정 정보를 확인
   ↓
5. L2가 각 L1 설비에 공정별 작업을 분배
```

### Backend → L2 메시지 예시

```text
TYPE=WORK_ORDER;ORDER=WO-20260709-001;LOT=LOT-20260709-001;ITEM=FG-EVR-001;QTY=10
```

---

## 4.5 L2 → L1 작업지시 흐름

```text
1. L2가 전체 작업지시 수량 확인
   ↓
2. L2가 공정 순서에 맞게 L1 설비에 작업 명령 전달
   ↓
3. 각 L1 설비가 작업 수행
   ↓
4. 각 L1 설비가 OK/NG 결과 또는 알람 로그를 L2로 전달
```

### L1 설비 예시

| 설비 | 공정 코드 | 역할 |
|---|---|---|
| L1-1 | OP20 | 코일 권선 |
| L1-2 | OP30 | 접점 가공/용접 |
| L1-3 | OP40_OP50 | 자동 조립 |
| L1-4 | OP60 | 실링/가스충전 |
| L1-5 | OP70 | 최종 검사 |
| L1-6 | OP80 | 마킹/포장 |

---

## 4.6 L1 → L2 생산 결과 전달 흐름

```text
1. L1 설비가 작업 수행
   ↓
2. 작업 결과 생성
   - inputQty
   - okQty
   - ngQty
   - status
   ↓
3. L1이 L2로 생산 결과 로그 전달
   ↓
4. L2가 각 설비 결과를 취합
```

### 생산 결과 메시지 예시

```text
TYPE=RESULT;LOT=LOT-20260709-001;EQ=EQ-WIND-01;PROC=OP20;INPUT=10;OK=9;NG=1;STATUS=COMPLETED
```

---

## 4.7 L2 → Backend 결과 취합 전달 흐름

```text
1. L2가 L1 설비별 결과 수신
   ↓
2. L2가 공정별 OK/NG 수량 취합
   ↓
3. L2가 Backend로 생산 결과 메시지 전송
   ↓
4. Backend가 메시지를 DTO로 변환
   ↓
5. Service에서 LOT 상태, 생산 결과, 불량 이력 처리
   ↓
6. JPA를 통해 DB 저장
```

### 관련 Backend 처리 예시

```text
TcpMessageService
→ ProductionService
→ LotService
→ EquipmentService
→ Repository
→ MySQL
```

### 관련 테이블 예시

```text
production_results
production_lots
inspection_results
defect_histories
equipment_status_histories
```

---

## 4.8 Backend → Frontend 대시보드 출력 흐름

```text
1. Frontend가 대시보드 API 호출
   ↓
2. Backend가 DB에서 생산 현황 조회
   ↓
3. Backend가 DTO로 응답
   ↓
4. Frontend가 LOT 진행률, OK/NG 수량, 설비 상태, 알람 정보를 표시
```

### 대시보드 표시 정보 예시

```text
- 전체 작업지시 수량
- 현재 LOT 진행 상태
- 공정별 OK/NG 수량
- 최종 완성 OK 수량
- 누적 NG 수량
- 설비별 상태
- 최근 알람 내역
- 최근 생산 이벤트
```

---

# 5. OK / NG 수량 처리 규칙

## 5.1 기본 규칙

```text
공정별 생산 결과:
- inputQty = 해당 공정에 투입된 수량
- okQty = 해당 공정에서 정상 처리된 수량
- ngQty = 해당 공정에서 불량 처리된 수량

다음 공정 투입 수량:
- 이전 공정의 OK 수량만 다음 공정으로 전달
```

---

## 5.2 최종 수량 계산 규칙

```text
최종 완성 OK 수량 = 마지막 공정의 OK 수량
최종 NG 수량 = 최초 작업지시 수량 - 마지막 공정 OK 수량
```

또는 다음과 같이 볼 수 있다.

```text
최종 NG 수량 = 각 공정에서 발생한 NG 수량의 합계
```

단, 재작업이나 폐기 후 재투입 같은 고급 흐름은 이번 미니 MES 범위에서는 제외한다.

---

## 5.3 예시: 작업지시 10개

```text
작업지시: EV Relay 완성품 10개 제작
```

| 공정 | 투입 수량 | OK 수량 | NG 수량 | 다음 공정 투입 |
|---|---:|---:|---:|---:|
| OP20 | 10 | 9 | 1 | 9 |
| OP30 | 9 | 8 | 1 | 8 |
| OP40_OP50 | 8 | 8 | 0 | 8 |
| OP60 | 8 | 7 | 1 | 7 |
| OP70 | 7 | 7 | 0 | 7 |
| OP80 | 7 | 7 | 0 | - |

### 최종 결과

```text
최종 OK 수량 = 7개
최종 NG 수량 = 10개 - 7개 = 3개
```

공정별 NG 합계도 다음과 같이 일치한다.

```text
OP20 NG 1개
+ OP30 NG 1개
+ OP60 NG 1개
= 총 NG 3개
```

---

# 6. 병렬 선행 공정 처리 규칙

일부 공정은 여러 선행 공정의 결과물이 모두 필요할 수 있다.

예를 들어 다음과 같은 흐름이 있을 수 있다.

```text
EQ1 + EQ2 → EQ3 → EQ4 → EQ5 → EQ6
```

이 경우 EQ3에 투입 가능한 수량은 EQ1과 EQ2의 OK 수량 중 더 작은 값이다.

```text
EQ3 투입 가능 수량 = min(EQ1 OK 수량, EQ2 OK 수량)
```

### 예시

```text
EQ1 결과: OK 8개, NG 2개
EQ2 결과: OK 9개, NG 1개

EQ3 투입 가능 수량 = min(8, 9) = 8개
```

즉, 병렬 선행 공정에서는 한쪽 결과물이 더 많아도 다른 쪽 결과물이 부족하면 다음 공정 투입 수량은 부족한 쪽 기준으로 결정된다.

---

# 7. 설비 이상 / 알람 처리 흐름

## 7.1 설비 이상 발생 흐름

```text
1. L1 설비에서 이상 발생
   예: 모터 과부하, 센서 오류, 통신 끊김
   ↓
2. L1이 L2로 알람 로그 전달
   ↓
3. L2가 해당 설비 상태를 ERROR 또는 STOPPED로 변경
   ↓
4. L2가 Backend로 알람 메시지 전달
   ↓
5. Backend가 알람 이력 저장
   ↓
6. Backend가 설비 상태 변경 이력 저장
   ↓
7. Frontend 대시보드에 알람 표시
```

### 알람 메시지 예시

```text
TYPE=ALARM;EQ=EQ-WIND-01;ALARM=MOTOR_OVERLOAD;STATUS=STOPPED;MESSAGE=Motor overload detected
```

---

## 7.2 알람 발생 시 처리 규칙

```text
- 알람 발생 시 해당 설비는 즉시 STOP 또는 ERROR 상태가 된다.
- 해당 설비가 담당하는 공정은 진행 중단된다.
- 알람 정보는 equipment_alarm_histories에 저장한다.
- 설비 상태 변화는 equipment_status_histories에 저장한다.
- Frontend 대시보드에는 설비 이상 상태를 표시한다.
```

---

## 7.3 조치 완료 및 작업 재개 흐름

```text
1. 사용자가 Frontend에서 알람 확인
   ↓
2. 사용자가 실제 조치 완료 후 Frontend에서 조치 완료 처리
   ↓
3. Backend가 알람 cleared_at, cleared_by 저장
   ↓
4. Backend가 설비 상태를 IDLE 또는 RUNNING으로 변경
   ↓
5. Backend 또는 L2가 작업 재개 명령 전달
   ↓
6. L2가 해당 L1 설비 작업 재개
```

### 알람 해제 메시지 예시

```text
TYPE=ALARM_CLEAR;EQ=EQ-WIND-01;ALARM=MOTOR_OVERLOAD;STATUS=IDLE
```

---

# 8. TCP/IP 메시지 방향 정리

이번 구조에서는 TCP/IP 메시지가 단방향이 아니라 여러 방향으로 오간다.

## 8.1 Backend → L2

Backend가 L2로 작업지시 또는 제어 명령을 전달한다.

```text
- 작업지시 메시지
- 작업 시작 명령
- 작업 중지 명령
- 작업 재개 명령
```

### 예시

```text
TYPE=WORK_ORDER;ORDER=WO-20260709-001;LOT=LOT-20260709-001;ITEM=FG-EVR-001;QTY=10
TYPE=START;LOT=LOT-20260709-001
TYPE=PAUSE;EQ=EQ-WIND-01
TYPE=RESUME;EQ=EQ-WIND-01
```

---

## 8.2 L2 → L1

L2가 각 L1 설비에 공정별 작업 명령을 전달한다.

```text
- 공정 시작 명령
- 투입 수량 전달
- LOT 번호 전달
- 정지/재개 명령
```

### 예시

```text
TYPE=PROCESS_START;LOT=LOT-20260709-001;EQ=EQ-WIND-01;PROC=OP20;INPUT=10
```

---

## 8.3 L1 → L2

L1 설비가 L2로 생산 결과나 알람 로그를 전달한다.

```text
- 생산 결과 메시지
- 설비 상태 메시지
- 알람 메시지
```

### 예시

```text
TYPE=RESULT;LOT=LOT-20260709-001;EQ=EQ-WIND-01;PROC=OP20;INPUT=10;OK=9;NG=1;STATUS=COMPLETED
TYPE=ALARM;EQ=EQ-WIND-01;ALARM=MOTOR_OVERLOAD;STATUS=STOPPED
```

---

## 8.4 L2 → Backend

L2가 취합한 정보를 Backend로 전달한다.

```text
- 공정별 생산 결과
- 설비 상태 변경 정보
- 알람 발생 정보
- 알람 해제 정보
```

### 예시

```text
TYPE=RESULT;LOT=LOT-20260709-001;EQ=EQ-WIND-01;PROC=OP20;INPUT=10;OK=9;NG=1;STATUS=COMPLETED
TYPE=EQUIPMENT_STATUS;EQ=EQ-WIND-01;STATUS=RUNNING
TYPE=ALARM;EQ=EQ-WIND-01;ALARM=MOTOR_OVERLOAD;STATUS=STOPPED
TYPE=ALARM_CLEAR;EQ=EQ-WIND-01;ALARM=MOTOR_OVERLOAD;STATUS=IDLE
```

---

# 9. Backend 저장 흐름

## 9.1 생산 결과 저장

```text
L2 생산 결과 메시지 수신
→ TcpMessageService에서 메시지 파싱
→ ProductionResultRequestDto 생성
→ ProductionService 호출
→ production_results 저장
→ production_lots OK/NG/상태 갱신
→ 필요 시 defect_histories 저장
```

---

## 9.2 설비 상태 저장

```text
L2 설비 상태 메시지 수신
→ TcpMessageService에서 메시지 파싱
→ EquipmentService 호출
→ equipments.status 변경
→ equipment_status_histories 저장
```

---

## 9.3 알람 저장

```text
L2 알람 메시지 수신
→ TcpMessageService에서 메시지 파싱
→ EquipmentService 호출
→ equipment_alarm_histories 저장
→ equipments.status = ERROR 또는 STOPPED 변경
→ equipment_status_histories 저장
```

---

## 9.4 알람 해제 저장

```text
사용자가 Frontend에서 조치 완료 처리
→ Backend API 호출
→ equipment_alarm_histories.cleared_at 저장
→ equipment_alarm_histories.cleared_by 저장
→ equipments.status = IDLE 또는 RUNNING 변경
→ equipment_status_histories 저장
→ 필요 시 L2로 RESUME 명령 전송
```

---

# 10. Frontend 화면 반영 흐름

## 10.1 LOT 상세 화면

표시 항목 예시:

```text
- LOT 번호
- 작업지시 번호
- 생산 품목
- 목표 수량
- 현재 공정
- LOT 상태
- 공정별 투입 수량
- 공정별 OK 수량
- 공정별 NG 수량
- 최종 OK 수량
- 최종 NG 수량
```

---

## 10.2 생산 현황 대시보드

표시 항목 예시:

```text
- 오늘 작업지시 수
- 진행 중 LOT 수
- 완료 LOT 수
- 전체 목표 수량
- 최종 OK 수량
- 누적 NG 수량
- 불량률
- 최근 생산 결과
```

---

## 10.3 설비 상태 화면

표시 항목 예시:

```text
- 설비 코드
- 설비명
- 담당 공정
- 현재 상태
- 현재 작업 LOT
- 최근 상태 변경 시간
```

---

## 10.4 알람 화면

표시 항목 예시:

```text
- 설비 코드
- 알람 코드
- 알람명
- 알람 등급
- 발생 시간
- 해제 시간
- 조치자
- 메시지
```

---

# 11. 발표용 설명 문장

발표에서는 다음 문장으로 전체 흐름을 설명하면 된다.

```text
사용자가 Frontend에서 작업지시를 생성하면 Backend가 이를 DB에 저장하고 L2 수집기에 전달합니다.
L2는 각 L1 설비 시뮬레이터에 공정별 작업을 지시하고, L1 설비들은 생산 결과와 알람 로그를 L2로 전달합니다.
L2는 결과를 취합해 Backend로 전송하고, Backend는 공정별 OK/NG 수량, LOT 상태, 불량 이력, 설비 알람 이력을 DB에 저장합니다.
이후 Frontend는 API를 통해 저장된 데이터를 조회하여 LOT 진행 상태와 생산 현황을 대시보드에 출력합니다.
```

---

# 12. 최종 요약

이번 EV Relay MES 프로젝트의 전체 흐름은 다음과 같이 정리한다.

```text
관리자 사용자 등록
→ 사용자 로그인
→ 자재 입고
→ 작업지시 생성
→ Backend가 L2에 작업지시 전달
→ L2가 L1 설비에 공정별 작업지시 전달
→ L1 설비가 작업 수행
→ L1이 생산 결과/알람을 L2로 전달
→ L2가 결과 취합 후 Backend로 전달
→ Backend가 DB 저장
→ Frontend가 API 조회
→ 대시보드 출력
```

핵심 규칙은 다음과 같다.

```text
1. OK 수량만 다음 공정으로 전달한다.
2. 최종 OK 수량은 마지막 공정의 OK 수량이다.
3. 최종 NG 수량은 최초 작업지시 수량에서 마지막 공정 OK 수량을 뺀 값이다.
4. 병렬 선행 공정이 있는 경우 다음 공정 투입 수량은 선행 공정 OK 수량 중 최솟값이다.
5. 설비 이상 발생 시 해당 설비는 STOP 또는 ERROR 상태가 된다.
6. 조치 완료 후 알람을 해제하고 작업을 재개한다.
```

