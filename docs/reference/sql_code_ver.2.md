# EV Relay MES DB 설계 SQL

> 기존 `sql_code.md`에 `members` 테이블과 사용자 추적 컬럼을 반영한 버전이다.
> 실제 SQL 실행 시에는 FK 참조 순서 때문에 `members`, `items`, `processes` 같은 기준 테이블을 먼저 생성한 뒤, `boms`, `machines`, 생산/이력 테이블 순서로 생성하는 것이 안전하다.

---

# 1. 마스터 테이블

## members(사용자/권한) 테이블

### 역할

- MES 로그인 사용자 관리
- 관리자/일반 사용자 권한 관리
- 최초 관리자 1명은 하드코딩 또는 초기 SQL로 등록
- 이후 일반 사용자는 관리자가 등록하고 권한을 부여
- 생산 지시, LOT 생성, 자재 입고, 알람 해제, 불량 확인 등 사용자 작업 이력 추적

### role 값

| 값       | 내용             |
| -------- | ---------------- |
| ADMIN    | 관리자           |
| MANAGER  | 생산/품질 관리자 |
| OPERATOR | 현장 작업자      |
| VIEWER   | 조회 전용 사용자 |

### status 값

| 값      | 내용        |
| ------- | ----------- |
| ACTIVE  | 사용 가능   |
| LOCKED  | 잠김        |
| RETIRED | 퇴사/비활성 |

```sql
CREATE TABLE members (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    member_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'OPERATOR',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    department VARCHAR(50),
    position VARCHAR(50),
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,

    CONSTRAINT fk_members_created_by
        FOREIGN KEY (created_by)
        REFERENCES members(member_id)
);
```

---

- 초기 관리자 입력 데이터

> 실제 프로젝트에서는 비밀번호를 평문으로 저장하지 말고 Spring Security의 BCryptPasswordEncoder로 암호화한 값을 넣어야 한다.
> 아래 password 값은 예시 자리값이므로 실제 해시값으로 교체해서 사용한다.

```sql
INSERT INTO members (
    login_id,
    password,
    member_name,
    role,
    status,
    department,
    position,
    created_by
)
VALUES (
    'admin',
    '$2a$10$REPLACE_WITH_BCRYPT_HASH_VALUE',
    '시스템 관리자',
    'ADMIN',
    'ACTIVE',
    '관리팀',
    '관리자',
    NULL
);
```

---

## items(품목 마스터) 테이블

- item_type:
  - RM = 원자재
  - SA = 반제품
  - FG = 완제품

> 모든 품목 단위가 `EA`로 고정이므로 `unit` 컬럼은 제거했다.
> `use_yn`은 품목 삭제 대신 비활성화 처리를 위해 유지한다.

```sql
CREATE TABLE items (
    item_code VARCHAR(50) PRIMARY KEY,
    item_name VARCHAR(100) NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL
);
```

---

- 입력 데이터

```sql
INSERT INTO items (item_code, item_name, item_type, use_yn)
VALUES
-- 원자재
('RM-CU-001', '코일용 구리선', 'RM', 'Y'),
('RM-BOB-001', '보빈', 'RM', 'Y'),
('RM-CONTACT-F', '고정 접점', 'RM', 'Y'),
('RM-CONTACT-M', '가동 접점', 'RM', 'Y'),
('RM-CORE-001', '코어', 'RM', 'Y'),
('RM-YOKE-001', '요크', 'RM', 'Y'),
('RM-SPR-001', '리턴 스프링', 'RM', 'Y'),
('RM-TERM-HV', '고전압 단자', 'RM', 'Y'),
('RM-TERM-COIL', '코일 단자', 'RM', 'Y'),
('RM-CER-001', '세라믹 챔버', 'RM', 'Y'),
('RM-MAG-001', '아크 소호 자석', 'RM', 'Y'),
('RM-CASE-001', '하우징', 'RM', 'Y'),
('RM-EPOXY-001', '에폭시', 'RM', 'Y'),
('RM-PACK-001', '포장 자재', 'RM', 'Y'),

-- 반제품
('SA-COIL-001', '코일 어셈블리', 'SA', 'Y'),
('SA-CONTACT-001', '접점 어셈블리', 'SA', 'Y'),
('SA-BODY-001', '본체 어셈블리', 'SA', 'Y'),
('SA-SEALED-001', '실링 완료품', 'SA', 'Y'),
('SA-TESTED-001', '검사 완료품', 'SA', 'Y'),

-- 완제품
('FG-EVR-001', 'EV Relay 완제품', 'FG', 'Y');
```

---

## processes(공정 목록) 테이블

### L1-1 ~ L1-6 공정 정보를 저장하는 테이블

- OP20 = 코일 권선
- OP30 = 접점 가공/용접
- OP40_OP50 = 자동 조립
- OP60 = 실링/가스충전
- OP70 = 최종 검사
- OP80 = 마킹/포장

### 역할

- 공정 코드 관리
- 공정 순서 관리
- 공정명 관리

```sql
CREATE TABLE processes (
    process_code VARCHAR(30) PRIMARY KEY,
    process_name VARCHAR(100) NOT NULL,
    process_order INT NOT NULL,
    description VARCHAR(255),
    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL
);
```

---

- 입력 데이터

```sql
INSERT INTO processes (
    process_code,
    process_name,
    process_order,
    description,
    use_yn
)
VALUES
('OP20', '코일 권선', 1, '코일 어셈블리 제작 공정', 'Y'),
('OP30', '접점 가공/용접', 2, '접점 어셈블리 제작 공정', 'Y'),
('OP40_OP50', '자동 조립', 3, '코일, 접점, 코어, 챔버 등을 조립하는 공정', 'Y'),
('OP60', '실링/가스충전', 4, '제품 실링 및 가스 충전 공정', 'Y'),
('OP70', '최종 검사', 5, '전기 특성 및 품질 검사 공정', 'Y'),
('OP80', '마킹/포장', 6, '마킹, 라벨링, 포장 공정', 'Y');
```

---

## BOM 테이블

> 모든 소요 단위가 `EA`로 고정이므로 `unit` 컬럼은 제거했다.
> `use_yn`은 BOM 변경 시 기존 이력과 기준정보를 보존하기 위해 유지한다.

```sql
CREATE TABLE boms (
    bom_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_item_code VARCHAR(50) NOT NULL,
    child_item_code VARCHAR(50) NOT NULL,
    quantity DECIMAL(10, 3) NOT NULL DEFAULT 1,
    process_code VARCHAR(30),
    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,

    CONSTRAINT fk_boms_parent_item
        FOREIGN KEY (parent_item_code)
        REFERENCES items(item_code),

    CONSTRAINT fk_boms_child_item
        FOREIGN KEY (child_item_code)
        REFERENCES items(item_code),

    CONSTRAINT fk_boms_process
        FOREIGN KEY (process_code)
        REFERENCES processes(process_code)
);
```

---

- BOM 입력 데이터

```sql
INSERT INTO boms (parent_item_code, child_item_code, quantity, process_code, use_yn)
VALUES
-- 1단계 BOM: 코일 어셈블리 / OP20
('SA-COIL-001', 'RM-CU-001', 1, 'OP20', 'Y'),
('SA-COIL-001', 'RM-BOB-001', 1, 'OP20', 'Y'),

-- 2단계 BOM: 접점 어셈블리 / OP30
('SA-CONTACT-001', 'RM-CONTACT-F', 2, 'OP30', 'Y'),
('SA-CONTACT-001', 'RM-CONTACT-M', 1, 'OP30', 'Y'),

-- 3단계 BOM: 본체 어셈블리 / OP40_OP50
('SA-BODY-001', 'SA-COIL-001', 1, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'SA-CONTACT-001', 1, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-CORE-001', 1, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-YOKE-001', 1, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-SPR-001', 1, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-TERM-HV', 2, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-TERM-COIL', 2, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-CER-001', 1, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-MAG-001', 2, 'OP40_OP50', 'Y'),

-- 4단계 BOM: 실링 완료품 / OP60
('SA-SEALED-001', 'SA-BODY-001', 1, 'OP60', 'Y'),
('SA-SEALED-001', 'RM-CASE-001', 1, 'OP60', 'Y'),
('SA-SEALED-001', 'RM-EPOXY-001', 1, 'OP60', 'Y'),

-- 5단계 BOM: 검사 완료품 / OP70
('SA-TESTED-001', 'SA-SEALED-001', 1, 'OP70', 'Y'),

-- 6단계 BOM: EV Relay 완제품 / OP80
('FG-EVR-001', 'SA-TESTED-001', 1, 'OP80', 'Y'),
('FG-EVR-001', 'RM-PACK-001', 1, 'OP80', 'Y');
```

---

## defectCode(불량 코드) 테이블

```sql
CREATE TABLE defect_codes (
    defect_code VARCHAR(50) PRIMARY KEY,
    defect_name VARCHAR(100) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    description VARCHAR(255),
    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_defect_codes_process
        FOREIGN KEY (process_code)
        REFERENCES processes(process_code)
);
```

---

- 입력 데이터

```sql
INSERT INTO defect_codes (defect_code, defect_name, process_code, description, use_yn)
VALUES
('COIL_OPEN_NG', '코일 단선', 'OP20', '코일 권선 공정에서 단선이 발생한 불량', 'Y'),
('COIL_RESISTANCE_NG', '코일 저항 불량', 'OP20', '코일 저항 측정값이 기준 범위를 벗어난 불량', 'Y'),
('COIL_SHORT_NG', '코일 쇼트', 'OP20', '코일 내부 쇼트가 발생한 불량', 'Y'),

('WELD_STRENGTH_NG', '용접 강도 불량', 'OP30', '접점 용접 강도가 기준 미달인 불량', 'Y'),
('CONTACT_RESISTANCE_NG', '접촉저항 불량', 'OP30', '접점 접촉저항이 기준 범위를 벗어난 불량', 'Y'),
('CONTACT_POSITION_NG', '접점 위치 불량', 'OP30', '접점 위치가 기준 위치에서 벗어난 불량', 'Y'),

('ASSY_MISALIGN_NG', '조립 위치 불량', 'OP40_OP50', '자동 조립 공정에서 부품 정렬이 틀어진 불량', 'Y'),
('SPRING_MISSING_NG', '스프링 누락', 'OP40_OP50', '자동 조립 공정에서 스프링이 누락된 불량', 'Y'),
('CHAMBER_CRACK_NG', '챔버 균열', 'OP40_OP50', '챔버 조립 중 균열이 발생한 불량', 'Y'),

('GAS_PRESSURE_NG', '가스 압력 불량', 'OP60', '가스 충전 압력이 기준 범위를 벗어난 불량', 'Y'),
('SEAL_LEAK_NG', '실링 누설 불량', 'OP60', '실링 후 누설률이 기준을 초과한 불량', 'Y'),
('SEAL_WELD_NG', '실링 용접 불량', 'OP60', '실링 용접 상태가 불량한 경우', 'Y'),

('INSULATION_NG', '절연저항 불량', 'OP70', '절연저항 측정값이 기준 미달인 불량', 'Y'),
('WITHSTAND_VOLTAGE_NG', '내전압 불량', 'OP70', '내전압 검사에서 기준을 만족하지 못한 불량', 'Y'),
('OPERATION_VOLTAGE_NG', '동작 전압 불량', 'OP70', '동작 전압이 기준 범위를 벗어난 불량', 'Y'),
('CONTACT_BOUNCE_NG', '접점 바운스 불량', 'OP70', '접점 바운스 시간이 기준을 초과한 불량', 'Y'),

('MARKING_NG', '마킹 불량', 'OP80', '제품 마킹이 누락되거나 식별 불가능한 불량', 'Y'),
('PACKING_COUNT_NG', '포장 수량 불일치', 'OP80', '포장 수량이 기준 수량과 일치하지 않는 불량', 'Y');
```

---

## alarmCode(설비 알람) 테이블

```sql
CREATE TABLE alarm_codes (
    alarm_code VARCHAR(50) PRIMARY KEY,
    alarm_name VARCHAR(100) NOT NULL,
    machine_type VARCHAR(30) NOT NULL,
    description VARCHAR(255),
    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

- 입력 데이터

```sql
INSERT INTO alarm_codes (alarm_code, alarm_name, machine_type, description, use_yn)
VALUES
-- 공통 설비 알람
('COMM_DISCONNECTED', '통신 끊김', 'COMMON', 'L1 장비와 L2 수집 서버 간 연결이 끊긴 상태', 'Y'),
('COMM_TIMEOUT', '통신 시간 초과', 'COMMON', '일정 시간 동안 장비 응답 또는 데이터 수신이 없는 상태', 'Y'),
('EMERGENCY_STOP', '비상 정지', 'COMMON', '장비가 비상 정지 버튼 또는 안전 조건에 의해 정지된 상태', 'Y'),
('POWER_OFF', '전원 꺼짐', 'COMMON', '장비 전원이 꺼져 있거나 전원 상태를 확인할 수 없는 상태', 'Y'),
('DOOR_OPEN', '도어 열림', 'COMMON', '장비 안전 도어가 열린 상태', 'Y'),
('SENSOR_ERROR', '센서 오류', 'COMMON', '센서값이 비정상이거나 센서 응답이 없는 상태', 'Y'),
('MOTOR_OVERLOAD', '모터 과부하', 'COMMON', '장비 구동 모터의 부하가 기준치를 초과한 상태', 'Y'),
('MATERIAL_SHORTAGE', '자재 부족', 'COMMON', '공정 수행에 필요한 자재가 부족한 상태', 'Y'),
('JAM_DETECTED', '걸림 발생', 'COMMON', '제품 또는 자재가 장비 내부에서 걸린 상태', 'Y'),
('UNKNOWN_ERROR', '알 수 없는 오류', 'COMMON', '분류되지 않은 설비 이상이 발생한 상태', 'Y'),

-- L1-1 / EQ-WIND 코일 권선기 알람
('WIRE_SHORTAGE', '구리선 부족', 'EQ-WIND', '코일 권선에 필요한 구리선이 부족한 상태', 'Y'),
('WIRE_BREAK', '구리선 끊김', 'EQ-WIND', '권선 작업 중 구리선이 끊어진 상태', 'Y'),
('WINDING_MOTOR_ERROR', '권선 모터 오류', 'EQ-WIND', '코일 권선 모터에 이상이 발생한 상태', 'Y'),
('BOBBIN_FEED_ERROR', '보빈 공급 오류', 'EQ-WIND', '보빈이 정상적으로 공급되지 않는 상태', 'Y'),

-- L1-2 / EQ-WELD 접점 용접기 알람
('WELD_POWER_ERROR', '용접 전원 오류', 'EQ-WELD', '접점 용접기의 전원 또는 용접 출력에 이상이 발생한 상태', 'Y'),
('WELD_TIP_WORN', '용접 팁 마모', 'EQ-WELD', '용접 팁이 마모되어 교체가 필요한 상태', 'Y'),
('CONTACT_FEED_ERROR', '접점 공급 오류', 'EQ-WELD', '고정 접점 또는 가동 접점이 정상적으로 공급되지 않는 상태', 'Y'),
('WELD_OVERHEAT', '용접부 과열', 'EQ-WELD', '용접부 또는 용접 장치 온도가 기준치를 초과한 상태', 'Y'),

-- L1-3 / EQ-ASSY 자동 조립기 알람
('PART_FEED_ERROR', '부품 공급 오류', 'EQ-ASSY', '조립에 필요한 부품이 정상적으로 공급되지 않는 상태', 'Y'),
('PICK_PLACE_ERROR', '픽앤플레이스 오류', 'EQ-ASSY', '부품 집기 또는 배치 동작에 실패한 상태', 'Y'),
('ALIGN_SENSOR_ERROR', '정렬 센서 오류', 'EQ-ASSY', '부품 위치 정렬을 감지하는 센서에 이상이 발생한 상태', 'Y'),
('ASSEMBLY_JAM', '조립 걸림', 'EQ-ASSY', '자동 조립 중 부품 또는 제품이 걸린 상태', 'Y'),

-- L1-4 / EQ-SEAL 실링/가스충전기 알람
('VACUUM_PUMP_ERROR', '진공 펌프 오류', 'EQ-SEAL', '진공 형성을 위한 펌프에 이상이 발생한 상태', 'Y'),
('GAS_SUPPLY_ERROR', '가스 공급 오류', 'EQ-SEAL', '가스 공급 압력 또는 공급량에 이상이 발생한 상태', 'Y'),
('SEAL_HEAD_ERROR', '실링 헤드 오류', 'EQ-SEAL', '실링 헤드 동작에 이상이 발생한 상태', 'Y'),
('CHAMBER_PRESSURE_ERROR', '챔버 압력 오류', 'EQ-SEAL', '챔버 내부 압력값이 기준 범위를 벗어난 상태', 'Y'),

-- L1-5 / EQ-TEST 최종 검사기 알람
('TEST_PROBE_ERROR', '검사 프로브 오류', 'EQ-TEST', '검사 핀 접촉 불량 또는 프로브 이상이 발생한 상태', 'Y'),
('HV_TESTER_ERROR', '내전압 검사기 오류', 'EQ-TEST', '내전압 검사 장비에 이상이 발생한 상태', 'Y'),
('MEASURE_DEVICE_ERROR', '측정 장비 오류', 'EQ-TEST', '전기 특성 측정 장비에서 이상이 발생한 상태', 'Y'),
('TEST_SEQUENCE_ERROR', '검사 시퀀스 오류', 'EQ-TEST', '검사 순서 또는 검사 조건이 비정상인 상태', 'Y'),

-- L1-6 / EQ-PACK 포장기 알람
('LABEL_PRINTER_ERROR', '라벨 프린터 오류', 'EQ-PACK', '제품 라벨 또는 식별 라벨 출력에 실패한 상태', 'Y'),
('BARCODE_READER_ERROR', '바코드 리더 오류', 'EQ-PACK', '제품 또는 LOT 바코드 인식에 실패한 상태', 'Y'),
('PACK_MATERIAL_SHORTAGE', '포장 자재 부족', 'EQ-PACK', '박스, 라벨 등 포장 자재가 부족한 상태', 'Y'),
('PACK_COUNT_ERROR', '포장 카운트 오류', 'EQ-PACK', '포장 수량 카운트가 기준 수량과 일치하지 않는 상태', 'Y');
```

---

## machines(설비/장비 목록) 테이블

### L1 장비 6개를 관리하는 테이블

- EQ-WIND-01 = 코일 권선기
- EQ-WELD-01 = 접점 용접기
- EQ-ASSY-01 = 자동 조립기
- EQ-SEAL-01 = 실링/가스충전기
- EQ-TEST-01 = 최종 검사기
- EQ-PACK-01 = 포장기

### status 값

| 값      | 내용 |
| ------- | ---- |
| IDLE    | 대기 |
| RUNNING | 가동 |
| ERROR   | 이상 |
| STOPPED | 정지 |

```sql
CREATE TABLE machines (
    machine_id VARCHAR(50) PRIMARY KEY,
    machine_name VARCHAR(100) NOT NULL,
    machine_type VARCHAR(30) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    use_yn CHAR(1) NOT NULL DEFAULT 'Y',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,

    CONSTRAINT fk_machines_process
        FOREIGN KEY (process_code)
        REFERENCES processes(process_code)
);
```

---

- 입력 데이터

```sql
INSERT INTO machines (
    machine_id,
    machine_name,
    machine_type,
    process_code,
    status,
    use_yn
)
VALUES
('EQ-WIND-01', '코일 권선기', 'EQ-WIND', 'OP20', 'IDLE', 'Y'),
('EQ-WELD-01', '접점 용접기', 'EQ-WELD', 'OP30', 'IDLE', 'Y'),
('EQ-ASSY-01', '자동 조립기', 'EQ-ASSY', 'OP40_OP50', 'IDLE', 'Y'),
('EQ-SEAL-01', '실링/가스충전기', 'EQ-SEAL', 'OP60', 'IDLE', 'Y'),
('EQ-TEST-01', '최종 검사기', 'EQ-TEST', 'OP70', 'IDLE', 'Y'),
('EQ-PACK-01', '포장기', 'EQ-PACK', 'OP80', 'IDLE', 'Y');
```

---

# 2. 생산 흐름 테이블

## Material Lots

### 자재 입고 검사 후 생성되는 자재 LOT 테이블

```text
RM-CU-001 자재가 1000개 입고됨
↓
MAT-LOT-20260708-001 생성
```

### status 값

| 값        | 내용      |
| --------- | --------- |
| AVAILABLE | 사용 가능 |
| HOLD      | 보류      |
| USED      | 사용 완료 |
| DISCARDED | 폐기      |

```sql
CREATE TABLE material_lots (
    material_lot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_lot_no VARCHAR(50) NOT NULL UNIQUE,
    item_code VARCHAR(50) NOT NULL,
    received_qty INT NOT NULL,
    current_qty INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_by BIGINT,

    CONSTRAINT fk_material_lots_item
        FOREIGN KEY (item_code)
        REFERENCES items(item_code),

    CONSTRAINT fk_material_lots_received_by
        FOREIGN KEY (received_by)
        REFERENCES members(member_id)
);
```

---

## work_orders(작업지시/생산 지시) 테이블

### status 값

| 값        | 내용      |
| --------- | --------- |
| CREATED   | 생성      |
| RELEASED  | 생산 투입 |
| RUNNING   | 생산 중   |
| COMPLETED | 완료      |
| CANCELED  | 취소      |

```sql
CREATE TABLE work_orders (
    work_order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    item_code VARCHAR(50) NOT NULL,
    target_qty INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    planned_start_at DATETIME,
    planned_end_at DATETIME,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_work_orders_item
        FOREIGN KEY (item_code)
        REFERENCES items(item_code),

    CONSTRAINT fk_work_orders_created_by
        FOREIGN KEY (created_by)
        REFERENCES members(member_id)
);
```

---

## lots(생산 LOT) 테이블

### 실제로 생산이 시작되면 생성되는 LOT

```text
생산 지시: EV Relay 100개 생산
↓
생산 LOT: EVR-LOT-20260708-001
```

### status 값

| 값        | 내용    |
| --------- | ------- |
| WAITING   | 대기    |
| RUNNING   | 생산 중 |
| COMPLETED | 완료    |
| HOLD      | 보류    |
| SCRAPPED  | 폐기    |

```sql
CREATE TABLE lots (
    lot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL UNIQUE,
    work_order_id BIGINT NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    current_process_code VARCHAR(30),
    input_qty INT NOT NULL,
    ok_qty INT NOT NULL DEFAULT 0,
    ng_qty INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    started_at DATETIME,
    completed_at DATETIME,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_lots_order
        FOREIGN KEY (work_order_id)
        REFERENCES work_orders(work_order_id),

    CONSTRAINT fk_lots_item
        FOREIGN KEY (item_code)
        REFERENCES items(item_code),

    CONSTRAINT fk_lots_process
        FOREIGN KEY (current_process_code)
        REFERENCES processes(process_code),

    CONSTRAINT fk_lots_created_by
        FOREIGN KEY (created_by)
        REFERENCES members(member_id)
);
```

---

## production_logs(공정별 생산 실적 로그) 테이블

### L2가 Spring Boot MES로 보내는 설비 생산 데이터가 쌓이는 테이블

```text
EQ-WIND-01에서 OP20 공정을 수행
투입 100개
OK 97개
NG 3개
```

```sql
CREATE TABLE production_logs (
    production_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    input_qty INT NOT NULL DEFAULT 0,
    ok_qty INT NOT NULL DEFAULT 0,
    ng_qty INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    started_at DATETIME,
    ended_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_production_logs_lot
        FOREIGN KEY (lot_no)
        REFERENCES lots(lot_no),

    CONSTRAINT fk_production_logs_machine
        FOREIGN KEY (machine_id)
        REFERENCES machines(machine_id),

    CONSTRAINT fk_production_logs_process
        FOREIGN KEY (process_code)
        REFERENCES processes(process_code)
);
```

---

## inspections(공정별 검사 결과) 테이블

### 공정별 측정값/검사 결과를 저장하는 테이블

- 처음에는 모든 검사값을 컬럼으로 고정하지 말고, 항목명-측정값 구조로 가면 편함
- 생산 수량 단위는 전부 `EA`지만 검사 측정값 단위는 공정마다 다르므로 `unit` 컬럼 유지

```text
OP20 코일 권선:
coilResistance = 12.5 Ω

OP60 실링/가스충전:
gasPressure = 1.2 MPa
leakRate = 0.03 cc/min

OP70 최종 검사:
insulationResistance = 500 MΩ
withstandVoltage = 1500 V
operationVoltage = 12 V
```

### result

- OK
- NG

```sql
CREATE TABLE inspections (
    inspection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    inspection_item VARCHAR(100) NOT NULL,
    measured_value DECIMAL(12, 3),
    unit VARCHAR(20),
    lower_limit DECIMAL(12, 3),
    upper_limit DECIMAL(12, 3),
    result VARCHAR(10) NOT NULL,
    inspected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inspections_lot
        FOREIGN KEY (lot_no)
        REFERENCES lots(lot_no),

    CONSTRAINT fk_inspections_machine
        FOREIGN KEY (machine_id)
        REFERENCES machines(machine_id),

    CONSTRAINT fk_inspections_process
        FOREIGN KEY (process_code)
        REFERENCES processes(process_code)
);
```

---

# 3. 이벤트 / 이력 테이블

## defect_histories(제품 불량 발생 이력) 테이블

> 불량 발생은 설비/시뮬레이터에서 자동 등록될 수 있으므로 `member_id`를 필수로 두지 않는다.
> 사람이 불량을 확인한 경우만 `confirmed_by`에 사용자 ID를 저장한다.

```sql
CREATE TABLE defect_histories (
    defect_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    defect_code VARCHAR(50) NOT NULL,
    defect_qty INT NOT NULL DEFAULT 0,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message VARCHAR(255),
    confirmed_by BIGINT,

    CONSTRAINT fk_defect_histories_lot
        FOREIGN KEY (lot_no)
        REFERENCES lots(lot_no),

    CONSTRAINT fk_defect_histories_machine
        FOREIGN KEY (machine_id)
        REFERENCES machines(machine_id),

    CONSTRAINT fk_defect_histories_process
        FOREIGN KEY (process_code)
        REFERENCES processes(process_code),

    CONSTRAINT fk_defect_histories_defect_code
        FOREIGN KEY (defect_code)
        REFERENCES defect_codes(defect_code),

    CONSTRAINT fk_defect_histories_confirmed_by
        FOREIGN KEY (confirmed_by)
        REFERENCES members(member_id)
);
```

---

## machine_alarm_histories(설비 알람 발생 이력) 테이블

> 알람 발생은 설비가 자동 등록하고, 알람 해제는 사용자가 처리할 수 있으므로 `cleared_by`를 둔다.

```sql
CREATE TABLE machine_alarm_histories (
    machine_alarm_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    machine_id VARCHAR(50) NOT NULL,
    alarm_code VARCHAR(50) NOT NULL,
    alarm_level VARCHAR(20) NOT NULL DEFAULT 'ERROR',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cleared_at DATETIME NULL,
    cleared_by BIGINT,
    message VARCHAR(255),

    CONSTRAINT fk_machine_alarm_histories_machine
        FOREIGN KEY (machine_id)
        REFERENCES machines(machine_id),

    CONSTRAINT fk_machine_alarm_histories_alarm_code
        FOREIGN KEY (alarm_code)
        REFERENCES alarm_codes(alarm_code),

    CONSTRAINT fk_machine_alarm_histories_cleared_by
        FOREIGN KEY (cleared_by)
        REFERENCES members(member_id)
);
```

---

## machine_status_histories(설비 상태 이력) 테이블

### 설비 상태가 바뀔 때마다 기록하는 테이블

```text
IDLE → RUNNING → ERROR → IDLE
```

```sql
CREATE TABLE machine_status_histories (
    machine_status_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    machine_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    lot_no VARCHAR(50),
    process_code VARCHAR(30),
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message VARCHAR(255),

    CONSTRAINT fk_machine_status_histories_machine
        FOREIGN KEY (machine_id)
        REFERENCES machines(machine_id),

    CONSTRAINT fk_machine_status_histories_lot
        FOREIGN KEY (lot_no)
        REFERENCES lots(lot_no),

    CONSTRAINT fk_machine_status_histories_process
        FOREIGN KEY (process_code)
        REFERENCES processes(process_code)
);
```

---

# 4. DB 관계

```text
members 1 ─ N members
members 1 ─ N material_lots
members 1 ─ N work_orders
members 1 ─ N lots
members 1 ─ N defect_histories
members 1 ─ N machine_alarm_histories

items 1 ─ N boms
items 1 ─ N material_lots
items 1 ─ N work_orders
items 1 ─ N lots

work_orders 1 ─ N lots

lots 1 ─ N production_logs
lots 1 ─ N inspections
lots 1 ─ N defect_histories
lots 1 ─ N machine_status_histories

processes 1 ─ N boms
processes 1 ─ N defect_codes
processes 1 ─ N machines
processes 1 ─ N production_logs
processes 1 ─ N inspections
processes 1 ─ N defect_histories
processes 1 ─ N machine_status_histories

machines 1 ─ N production_logs
machines 1 ─ N inspections
machines 1 ─ N defect_histories
machines 1 ─ N machine_alarm_histories
machines 1 ─ N machine_status_histories

defect_codes 1 ─ N defect_histories
alarm_codes 1 ─ N machine_alarm_histories
```

---

# 4.1 members 관계 상세

```text
members.created_by ─ members.member_id
material_lots.received_by ─ members.member_id
work_orders.created_by ─ members.member_id
lots.created_by ─ members.member_id
defect_histories.confirmed_by ─ members.member_id
machine_alarm_histories.cleared_by ─ members.member_id
```

---

# 4.2 unit / use_yn 정리

## unit 제거 기준

- `items.unit`은 현재 모든 품목 단위가 `EA`이므로 제거
- `boms.unit`도 현재 모든 BOM 소요 단위가 `EA`이므로 제거
- `inspections.unit`은 검사 측정 단위가 공정별로 다르므로 유지

## use_yn 유지 기준

`use_yn`은 기준정보 테이블에서 삭제 대신 비활성화 처리를 위해 유지한다.

유지 대상:

```text
items
boms
defect_codes
alarm_codes
processes
machines
```

예를 들어 과거에 사용하던 불량코드를 더 이상 신규 등록에 쓰지 않더라도, 기존 `defect_histories` 이력과 FK 관계를 유지해야 하므로 삭제보다는 `use_yn = 'N'` 처리가 안전하다.

`members`는 `use_yn` 대신 `status`를 사용한다.

```text
ACTIVE
LOCKED
RETIRED
```

---

# 4.3 created_at / updated_at 정리

## created_at 기준

`created_at`은 데이터가 최초 생성된 시점을 저장하기 위한 컬럼이다.

주로 기준정보, 생산 지시, LOT, 생산 결과처럼 데이터가 생성되는 시점이 중요한 테이블에 사용한다.

현재 `created_at`을 사용하는 주요 테이블은 다음과 같다.

```text
members
items
processes
boms
defect_codes
alarm_codes
machines
work_orders
lots
production_logs
```

이벤트성 테이블은 업무 의미에 맞는 별도 시점 컬럼을 사용한다.

```text
material_lots.received_at
inspections.inspected_at
defect_histories.occurred_at
machine_alarm_histories.occurred_at
machine_alarm_histories.cleared_at
machine_status_histories.recorded_at
```

## updated_at 기준

`updated_at`은 데이터가 수정된 시점을 저장하기 위한 컬럼이다.

수정 가능성이 있는 기준정보 테이블에는 `updated_at`을 둔다.

현재 `updated_at`을 사용하는 테이블은 다음과 같다.

```text
members
items
processes
boms
machines
```

각 테이블별 의미는 다음과 같다.

| 테이블      | updated_at 사용 이유                                |
| ----------- | --------------------------------------------------- |
| `members`   | 사용자 정보, 권한, 상태 변경 시점 기록              |
| `items`     | 품목명, 품목 유형, 사용 여부 변경 시점 기록         |
| `processes` | 공정명, 공정 순서, 사용 여부 변경 시점 기록         |
| `boms`      | BOM 구성, 수량, 적용 공정, 사용 여부 변경 시점 기록 |
| `machines`  | 설비명, 설비 상태, 사용 여부 변경 시점 기록         |

## updated_at을 두지 않는 기준

생산 결과, 검사 결과, 불량 이력, 설비 알람 이력처럼 한 번 발생한 이력을 기록하는 테이블은 원칙적으로 수정하지 않는다.

따라서 아래 테이블은 `updated_at`을 두지 않는다.

```text
production_logs
inspections
defect_histories
machine_alarm_histories
machine_status_histories
```

이력 데이터의 상태가 바뀌는 경우에는 기존 행을 수정하기보다, 별도 상태 컬럼 또는 별도 이력 행으로 관리한다.

예를 들어 설비 알람은 발생 시 `occurred_at`을 저장하고, 조치 완료 시 `cleared_at`, `cleared_by`를 저장한다.

## Java Entity 처리 기준

`created_at`은 DB의 `DEFAULT CURRENT_TIMESTAMP` 또는 Entity의 `@PrePersist`로 처리할 수 있다.

`updated_at`은 수정 시점에 Java Entity의 `@PreUpdate`로 처리한다.

예시:

```java
@PrePersist
public void prePersist() {
    this.createdAt = LocalDateTime.now();
}

@PreUpdate
public void preUpdate() {
    this.updatedAt = LocalDateTime.now();
}
```

현재 프로젝트는 주요 사용자 작업 이력을 `created_by`, `received_by`, `confirmed_by`, `cleared_by` 같은 업무 컬럼으로 직접 관리하므로, `JpaAuditingConfig.java`는 필수로 두지 않는다.

---

# 5. 조회 코드

- 품목별 조회

```sql
SELECT
    b.parent_item_code,
    p.item_name AS parent_item_name,
    b.child_item_code,
    c.item_name AS child_item_name,
    b.quantity,
    b.process_code
FROM boms b
JOIN items p ON b.parent_item_code = p.item_code
JOIN items c ON b.child_item_code = c.item_code
WHERE b.parent_item_code = 'SA-BODY-001'
  AND b.use_yn = 'Y';
```

---
