# EV Relay MES 파일 구조 정리 v2.2

> 기준: 학원 수업에서 배운 단순 구조를 유지하되, DB/Java/화면 용어가 서로 충돌하지 않도록 정리한다.  
> 품목은 `items` 테이블 하나로 통합하고, 그 외 용어는 Java 파일명 기준으로 맞춘다.

---

## 1. React

```text
src
│
├── api
│      MesApi.js
│
├── layouts
│      MesLayout.js
│
├── pages
│      LoginPage.js
│      DashboardPage.js
│      WorkOrderPage.js
│      ItemPage.js
│      MaterialLotPage.js
│      BomPage.js
│      ProductionPage.js
│      InspectionPage.js
│      MachinePage.js
│      LotPage.js
│      DefectPage.js
│      MachineAlarmPage.js
│      MemberPage.js
│
├── style
│      GlobalStyle.js
│
├── App.js
├── App.css
├── index.js
├── index.css
├── reportWebVitals.js
└── setupTests.js
```

### React 정리 기준

- 수업에서 배운 단순 구조를 기준으로 `MesApi.js` 하나에서 API 호출을 관리한다.
- 화면 단위는 MES 주요 기능 기준으로 나눈다.
- `ItemPage.js`는 품목 마스터 화면으로 사용한다.
  - 원자재: `item_type = RM`
  - 반제품: `item_type = SA`
  - 완제품: `item_type = FG`
- `MaterialLotPage.js`는 자재 입고 LOT 조회/등록 화면으로 사용한다.
- `MachinePage.js`는 설비 상태 조회 화면으로 사용한다.
- `DefectPage.js`는 제품 불량 이력 조회 화면으로 사용한다.
- `MachineAlarmPage.js`는 설비 알람 이력 조회 화면으로 사용한다.

| 화면명                | 실제 의미                  |
| --------------------- | -------------------------- |
| `ItemPage.js`         | 품목 마스터 관리 화면      |
| `MaterialLotPage.js`  | 자재 입고 LOT 관리 화면    |
| `WorkOrderPage.js`    | 작업지시 관리 화면         |
| `LotPage.js`          | 생산 LOT 관리 화면         |
| `ProductionPage.js`   | 공정별 생산 실적 조회 화면 |
| `InspectionPage.js`   | 검사 결과 조회 화면        |
| `MachinePage.js`      | 설비 상태 조회 화면        |
| `DefectPage.js`       | 제품 불량 이력 조회 화면   |
| `MachineAlarmPage.js` | 설비 알람 이력 조회 화면   |

---

## 2. Spring Boot

```text
src/main/java/com/mes
│
├── config
│      WebConfig.java
│      CorsConfig.java
│
├── controller
│      LoginController.java
│      DashboardController.java
│      WorkOrderController.java
│      ItemController.java
│      MaterialLotController.java
│      BomController.java
│      ProductionController.java
│      InspectionController.java
│      MachineController.java
│      LotController.java
│      DefectController.java
│      MachineAlarmController.java
│      MemberController.java
│
├── service
│      LoginService.java
│      DashboardService.java
│      WorkOrderService.java
│      ItemService.java
│      MaterialLotService.java
│      BomService.java
│      ProductionService.java
│      InspectionService.java
│      MachineService.java
│      LotService.java
│      DefectService.java
│      MachineAlarmService.java
│      MemberService.java
│
├── repository
│      MemberRepository.java
│      WorkOrderRepository.java
│      ItemRepository.java
│      MaterialLotRepository.java
│      BomRepository.java
│      ProductionLogRepository.java
│      InspectionRepository.java
│      MachineRepository.java
│      LotRepository.java
│      DefectHistoryRepository.java
│      MachineAlarmHistoryRepository.java
│      MachineStatusHistoryRepository.java
│      ProcessRepository.java
│      DefectCodeRepository.java
│      AlarmCodeRepository.java
│
├── entity
│      Member.java
│      WorkOrder.java
│      Item.java
│      MaterialLot.java
│      Bom.java
│      ProductionLog.java
│      Inspection.java
│      Machine.java
│      Lot.java
│      DefectHistory.java
│      MachineAlarmHistory.java
│      MachineStatusHistory.java
│      Process.java
│      DefectCode.java
│      AlarmCode.java
│
├── dto
│      request
│      response
│
├── exception
│      CustomException.java
│      ErrorCode.java
│
├── tcp
│      TcpClient.java
│      TcpService.java
│      TcpMessageParser.java
│
└── MesApplication.java
```

---

## 3. Spring Boot 정리 기준

### 3.1 품목은 `Item`으로 통합

기존 수업 구조에서는 `Product`, `Material`을 나누는 방식도 가능하지만, 현재 DB는 `items` 테이블 하나로 품목을 통합한다.

따라서 Java Entity도 아래처럼 통합한다.

```text
Product.java   사용하지 않음
Material.java  사용하지 않음
Item.java      사용
```

`Item.java` 하나에서 `item_type`으로 품목 종류를 구분한다.

| item_type | 의미   |
| --------- | ------ |
| `RM`      | 원자재 |
| `SA`      | 반제품 |
| `FG`      | 완제품 |

관련 파일은 아래처럼 둔다.

```text
controller
│      ItemController.java

service
│      ItemService.java

repository
│      ItemRepository.java

entity
│      Item.java

pages
│      ItemPage.js
```

---

### 3.2 자재 입고 LOT는 `MaterialLot`으로 분리

`items`는 품목 마스터이고, `material_lots`는 실제 입고된 자재 LOT 이력이다.

예를 들어 `RM-CU-001`이라는 품목이 있고, 실제로 1000개 입고되면 `MAT-LOT-20260708-001` 같은 자재 LOT가 생성된다.

따라서 `Material.java`는 사용하지 않지만, `MaterialLot.java`는 사용한다.

| 구분               | 역할                             |
| ------------------ | -------------------------------- |
| `Item.java`        | 원자재/반제품/완제품 품목 마스터 |
| `MaterialLot.java` | 실제 입고된 자재 LOT             |

관련 파일은 아래처럼 둔다.

```text
controller
│      MaterialLotController.java

service
│      MaterialLotService.java

repository
│      MaterialLotRepository.java

entity
│      MaterialLot.java

pages
│      MaterialLotPage.js
```

---

### 3.3 용어 통일 기준

`items` 통합을 제외한 나머지는 Java 파일명 기준으로 통일한다.

| Java 파일명 기준            | DB 테이블명                | 의미                  |
| --------------------------- | -------------------------- | --------------------- |
| `Member.java`               | `members`                  | 사용자/권한           |
| `Item.java`                 | `items`                    | 품목 마스터           |
| `MaterialLot.java`          | `material_lots`            | 자재 입고 LOT         |
| `Bom.java`                  | `boms`                     | BOM 구성              |
| `WorkOrder.java`            | `work_orders`              | 작업지시/생산지시     |
| `Lot.java`                  | `lots`                     | 생산 LOT              |
| `ProductionLog.java`        | `production_logs`          | 공정별 생산 실적 로그 |
| `Inspection.java`           | `inspections`              | 검사 결과             |
| `Machine.java`              | `machines`                 | 설비 마스터/설비 상태 |
| `DefectHistory.java`        | `defect_histories`         | 제품 불량 이력        |
| `MachineAlarmHistory.java`  | `machine_alarm_histories`  | 설비 알람 이력        |
| `MachineStatusHistory.java` | `machine_status_histories` | 설비 상태 변경 이력   |

정리하면 아래 기준으로 간다.

```text
Equipment 용어 사용하지 않음 → Machine으로 통일
ProductionResult 용어 사용하지 않음 → ProductionLog로 통일
ProductionOrder 용어 사용하지 않음 → WorkOrder로 통일
ProductionLot 용어 사용하지 않음 → Lot로 통일
InspectionResult 용어 사용하지 않음 → Inspection으로 통일
```

---

### 3.4 Machine 용어 기준

현재 프로젝트에서는 `Machine`을 설비 의미로 사용한다.

| 파일명                   | 역할                          |
| ------------------------ | ----------------------------- |
| `Machine.java`           | 설비 마스터 정보 및 현재 상태 |
| `MachineController.java` | 설비 상태 조회 API            |
| `MachineService.java`    | 설비 상태 관리 로직           |
| `MachineRepository.java` | 설비 데이터 조회              |
| `MachinePage.js`         | 설비 상태 화면                |

DB 테이블도 Java 기준에 맞춰 `machines`로 통일한다.

---

### 3.5 제품 불량 이력과 설비 알람 이력 분리 기준

혼동을 줄이기 위해 `ErrorHistory`라는 통합 이름은 사용하지 않는다.
제품에서 발생한 문제는 `Defect`, 설비에서 발생한 문제는 `MachineAlarm`으로 분리한다.

| 구분                  | 의미           | 관련 대상                 |
| --------------------- | -------------- | ------------------------- |
| `DefectHistory`       | 제품 불량 이력 | LOT, 생산 실적, 검사 결과 |
| `MachineAlarmHistory` | 설비 알람 이력 | 설비, 통신, 센서, 모터 등 |

### 제품 불량 관련 파일

```text
controller
│      DefectController.java

service
│      DefectService.java

repository
│      DefectHistoryRepository.java

entity
│      DefectHistory.java

pages
│      DefectPage.js
```

| 파일명                         | 역할                             |
| ------------------------------ | -------------------------------- |
| `DefectController.java`        | 제품 불량 이력 조회 API          |
| `DefectService.java`           | 제품 불량 이력 저장 및 조회 로직 |
| `DefectHistoryRepository.java` | 제품 불량 이력 DB 조회           |
| `DefectHistory.java`           | 제품 불량 이력 Entity            |
| `DefectPage.js`                | 제품 불량 이력 화면              |

### 설비 알람 관련 파일

```text
controller
│      MachineAlarmController.java

service
│      MachineAlarmService.java

repository
│      MachineAlarmHistoryRepository.java

entity
│      MachineAlarmHistory.java

pages
│      MachineAlarmPage.js
```

| 파일명                               | 역할                             |
| ------------------------------------ | -------------------------------- |
| `MachineAlarmController.java`        | 설비 알람 이력 조회 API          |
| `MachineAlarmService.java`           | 설비 알람 이력 저장 및 조회 로직 |
| `MachineAlarmHistoryRepository.java` | 설비 알람 이력 DB 조회           |
| `MachineAlarmHistory.java`           | 설비 알람 이력 Entity            |
| `MachineAlarmPage.js`                | 설비 알람 이력 화면              |

### DB 테이블 기준

DB도 통합하지 않고 아래처럼 분리한다.

```text
defect_histories
machine_alarm_histories
```

| 테이블명                  | 역할                |
| ------------------------- | ------------------- |
| `defect_histories`        | 제품 불량 이력 저장 |
| `machine_alarm_histories` | 설비 알람 이력 저장 |

`defect_histories`는 LOT, 생산 실적, 검사 결과와 연결하고, `machine_alarm_histories`는 설비와 연결한다.
이렇게 분리하면 NULL 컬럼이 줄어들고 ERD 설명도 쉬워진다.

---

## 4. C

```text
L1 machines
│
├── common
│      device.c
│      device.h
│      protocol.c
│      protocol.h
│      net.c
│      net.h
│
├── winding
│      main.c
│      config.h
│
├── welding
│      main.c
│      config.h
│
├── assembly
│      main.c
│      config.h
│
├── sealing
│      main.c
│      config.h
│
├── inspection
│      main.c
│      config.h
│
└── packing
       main.c
       config.h


L2 mes_collector
│
├── main.c
│
├── collector.c
├── collector.h
│
├── scheduler.c
├── scheduler.h
│
├── api_client.c
├── api_client.h
│
├── protocol.c
├── protocol.h
│
├── net.c
├── net.h
│
├── config.h
│
├── cJSON.c
├── cJSON.h
│
└── Makefile
```

---

## 5. C 정리 기준

- `L1 machines`는 각 설비 시뮬레이터 역할을 담당한다.
- `L2 mes_collector`는 L1 설비 데이터를 수집하고 MES로 전달하는 중간 수집기 역할을 담당한다.
- `winding`, `welding`, `assembly`, `sealing`, `inspection`, `packing`은 발표 시 공정 흐름을 설명하기 좋은 이름이다.
- 실제 공정명이 바뀔 가능성이 있으면 내부 설정값으로 설비 코드를 따로 관리한다.

예시:

```text
winding    -> MACHINE_01 / EQ-WIND-01
welding    -> MACHINE_02 / EQ-WELD-01
assembly   -> MACHINE_03 / EQ-ASSY-01
sealing    -> MACHINE_04 / EQ-SEAL-01
inspection -> MACHINE_05 / EQ-TEST-01
packing    -> MACHINE_06 / EQ-PACK-01
```

---

## 6. 최종 정리

이 구조는 학원 수업에서 배운 방식에 맞춘 단순한 MES 구조로 사용 가능하다.
다만 원래 EV Relay MES 설계 의도를 유지하기 위해 아래 기준은 반드시 지킨다.

```text
1. 품목은 Product/Material로 나누지 않고 Item으로 통합한다.
2. DB의 품목 테이블은 items 하나만 사용한다.
3. 자재 입고 LOT는 MaterialLot으로 별도 관리한다.
4. Machine은 설비 의미로 사용한다.
5. 제품 불량 이력은 DefectHistory로 관리한다.
6. 설비 알람 이력은 MachineAlarmHistory로 관리한다.
7. DB 테이블명도 Java 파일명 기준에 맞춰 work_orders, lots, production_logs, inspections, machines, machine_alarm_histories로 통일한다.
8. C 시뮬레이터는 L1 설비 + L2 수집기 구조로 유지한다.
```
