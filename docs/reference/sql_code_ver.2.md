# EV Relay MES DB 초기화 SQL

> 기준: `backend/src/main/java/com/human/ev_relay_mes/Entity`의 현재 JPA 엔티티 22개
>
> 대상: MySQL 8.x / MySQL Workbench
>
> 최종 동기화: 2026-07-21

이 문서의 SQL 블록은 위에서 아래까지 한 번에 실행할 수 있다. 기존 MES 테이블을 삭제한 뒤 다시 생성하므로 운영 데이터가 있는 DB에는 그대로 실행하면 안 된다.

현재 `application.properties`의 `spring.jpa.hibernate.ddl-auto=create`는 Backend를 시작할 때 테이블을 다시 생성한다. Workbench에서 만든 스키마와 데이터를 유지하려면 개발 환경에 맞게 `validate` 또는 `none`으로 변경해야 한다.

## 이전 문서와 달라진 핵심 사항

- 현재 엔티티에 없는 `processes.use_yn`, `defect_codes.use_yn`, `alarm_codes.use_yn`, `machines.use_yn`을 제거했다.
- 현재 엔티티에 없는 `defect_histories.confirmed_by`를 제거했다.
- `lots.start_requested_at`과 이벤트 중복 방지용 `event_id` 컬럼을 반영했다.
- 검사 기준, LOT별 검사 기준 스냅샷, 개별 제품 검사 결과 구조를 반영했다.
- 작업자, 설비 작업자 배치, LOT 공정별 책임자 테이블을 반영했다.
- L2 명령 Polling에 사용하는 `work_commands` 테이블을 반영했다.
- JPA의 모든 UNIQUE 제약조건과 FK를 현재 컬럼 기준으로 맞췄다.

## 전체 초기화 및 생성 SQL

```sql
CREATE DATABASE IF NOT EXISTS mes_hm_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE mes_hm_db;
SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS inspection_unit_results;
DROP TABLE IF EXISTS inspections;
DROP TABLE IF EXISTS work_commands;
DROP TABLE IF EXISTS lot_inspection_standard_snapshots;
DROP TABLE IF EXISTS lot_process_responsibles;
DROP TABLE IF EXISTS production_logs;
DROP TABLE IF EXISTS defect_histories;
DROP TABLE IF EXISTS machine_alarm_histories;
DROP TABLE IF EXISTS machine_status_histories;
DROP TABLE IF EXISTS lots;
DROP TABLE IF EXISTS work_orders;
DROP TABLE IF EXISTS material_lots;
DROP TABLE IF EXISTS machine_worker_assignments;
DROP TABLE IF EXISTS inspection_standards;
DROP TABLE IF EXISTS machines;
DROP TABLE IF EXISTS defect_codes;
DROP TABLE IF EXISTS alarm_codes;
DROP TABLE IF EXISTS boms;
DROP TABLE IF EXISTS workers;
DROP TABLE IF EXISTS processes;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS members;

SET FOREIGN_KEY_CHECKS = 1;

-- --------------------------------------------------------------------------
-- 1. 사용자와 기준정보
-- --------------------------------------------------------------------------

CREATE TABLE members (
    member_id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    member_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'OPERATOR',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    department VARCHAR(50) NULL,
    position VARCHAR(50) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    PRIMARY KEY (member_id),
    UNIQUE KEY uk_members_login_id (login_id),
    CONSTRAINT fk_members_created_by
        FOREIGN KEY (created_by) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE items (
    item_code VARCHAR(50) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    use_yn VARCHAR(1) NOT NULL DEFAULT 'Y',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    PRIMARY KEY (item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE processes (
    process_code VARCHAR(30) NOT NULL,
    process_name VARCHAR(100) NOT NULL,
    process_order INT NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    PRIMARY KEY (process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workers (
    worker_id BIGINT NOT NULL AUTO_INCREMENT,
    worker_no VARCHAR(30) NOT NULL,
    worker_name VARCHAR(100) NOT NULL,
    department VARCHAR(50) NULL,
    position VARCHAR(50) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    PRIMARY KEY (worker_id),
    UNIQUE KEY uk_workers_worker_no (worker_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE boms (
    bom_id BIGINT NOT NULL AUTO_INCREMENT,
    parent_item_code VARCHAR(50) NOT NULL,
    child_item_code VARCHAR(50) NOT NULL,
    quantity DECIMAL(10, 3) NOT NULL DEFAULT 1.000,
    process_code VARCHAR(30) NULL,
    use_yn VARCHAR(1) NOT NULL DEFAULT 'Y',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    PRIMARY KEY (bom_id),
    CONSTRAINT fk_boms_parent_item
        FOREIGN KEY (parent_item_code) REFERENCES items (item_code),
    CONSTRAINT fk_boms_child_item
        FOREIGN KEY (child_item_code) REFERENCES items (item_code),
    CONSTRAINT fk_boms_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE defect_codes (
    defect_code VARCHAR(50) NOT NULL,
    defect_name VARCHAR(100) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (defect_code),
    CONSTRAINT fk_defect_codes_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE alarm_codes (
    alarm_code VARCHAR(50) NOT NULL,
    alarm_name VARCHAR(100) NOT NULL,
    machine_type VARCHAR(30) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (alarm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE machines (
    machine_id VARCHAR(50) NOT NULL,
    machine_name VARCHAR(100) NOT NULL,
    machine_type VARCHAR(30) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    PRIMARY KEY (machine_id),
    CONSTRAINT fk_machines_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_standards (
    standard_id BIGINT NOT NULL AUTO_INCREMENT,
    process_code VARCHAR(30) NOT NULL,
    inspection_item VARCHAR(100) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    lower_limit DECIMAL(12, 3) NULL,
    upper_limit DECIMAL(12, 3) NULL,
    standard_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    PRIMARY KEY (standard_id),
    UNIQUE KEY uk_inspection_standard_process_item
        (process_code, inspection_item),
    CONSTRAINT fk_inspection_standards_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE machine_worker_assignments (
    assignment_id BIGINT NOT NULL AUTO_INCREMENT,
    machine_id VARCHAR(50) NOT NULL,
    worker_id BIGINT NOT NULL,
    assignment_role VARCHAR(20) NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (assignment_id),
    UNIQUE KEY uk_machine_worker_assignment (machine_id, worker_id),
    CONSTRAINT fk_machine_worker_assignments_machine
        FOREIGN KEY (machine_id) REFERENCES machines (machine_id),
    CONSTRAINT fk_machine_worker_assignments_worker
        FOREIGN KEY (worker_id) REFERENCES workers (worker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------
-- 2. 자재, 작업지시와 LOT
-- --------------------------------------------------------------------------

CREATE TABLE material_lots (
    material_lot_id BIGINT NOT NULL AUTO_INCREMENT,
    material_lot_no VARCHAR(50) NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    received_qty INT NOT NULL,
    current_qty INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_by BIGINT NULL,
    PRIMARY KEY (material_lot_id),
    UNIQUE KEY uk_material_lots_lot_no (material_lot_no),
    CONSTRAINT fk_material_lots_item
        FOREIGN KEY (item_code) REFERENCES items (item_code),
    CONSTRAINT fk_material_lots_received_by
        FOREIGN KEY (received_by) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE work_orders (
    work_order_id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    target_qty INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    planned_start_at DATETIME NULL,
    planned_end_at DATETIME NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (work_order_id),
    UNIQUE KEY uk_work_orders_order_no (order_no),
    CONSTRAINT fk_work_orders_item
        FOREIGN KEY (item_code) REFERENCES items (item_code),
    CONSTRAINT fk_work_orders_created_by
        FOREIGN KEY (created_by) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lots (
    lot_id BIGINT NOT NULL AUTO_INCREMENT,
    lot_no VARCHAR(50) NOT NULL,
    work_order_id BIGINT NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    current_process_code VARCHAR(30) NULL,
    input_qty INT NOT NULL,
    ok_qty INT NOT NULL DEFAULT 0,
    ng_qty INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    started_at DATETIME NULL,
    start_requested_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (lot_id),
    UNIQUE KEY uk_lots_lot_no (lot_no),
    CONSTRAINT fk_lots_work_order
        FOREIGN KEY (work_order_id) REFERENCES work_orders (work_order_id),
    CONSTRAINT fk_lots_item
        FOREIGN KEY (item_code) REFERENCES items (item_code),
    CONSTRAINT fk_lots_current_process
        FOREIGN KEY (current_process_code) REFERENCES processes (process_code),
    CONSTRAINT fk_lots_created_by
        FOREIGN KEY (created_by) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lot_process_responsibles (
    lot_process_responsible_id BIGINT NOT NULL AUTO_INCREMENT,
    lot_id BIGINT NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    worker_id BIGINT NOT NULL,
    captured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (lot_process_responsible_id),
    UNIQUE KEY uk_lot_process_responsible (lot_id, process_code),
    CONSTRAINT fk_lot_process_responsibles_lot
        FOREIGN KEY (lot_id) REFERENCES lots (lot_id),
    CONSTRAINT fk_lot_process_responsibles_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code),
    CONSTRAINT fk_lot_process_responsibles_machine
        FOREIGN KEY (machine_id) REFERENCES machines (machine_id),
    CONSTRAINT fk_lot_process_responsibles_worker
        FOREIGN KEY (worker_id) REFERENCES workers (worker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lot_inspection_standard_snapshots (
    snapshot_id BIGINT NOT NULL AUTO_INCREMENT,
    lot_no VARCHAR(50) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    inspection_item VARCHAR(100) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    lower_limit DECIMAL(12, 3) NULL,
    upper_limit DECIMAL(12, 3) NULL,
    standard_version INT NOT NULL,
    captured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_lot_inspection_snapshot
        (lot_no, process_code, inspection_item),
    CONSTRAINT fk_lot_inspection_snapshots_lot
        FOREIGN KEY (lot_no) REFERENCES lots (lot_no),
    CONSTRAINT fk_lot_inspection_snapshots_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------
-- 3. L2 명령과 생산/품질/설비 이력
-- --------------------------------------------------------------------------

CREATE TABLE work_commands (
    command_id BIGINT NOT NULL AUTO_INCREMENT,
    command_type VARCHAR(20) NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    lot_no VARCHAR(50) NOT NULL,
    input_qty INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ack_message VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at DATETIME NULL,
    acknowledged_at DATETIME NULL,
    completed_at DATETIME NULL,
    PRIMARY KEY (command_id),
    CONSTRAINT fk_work_commands_machine
        FOREIGN KEY (machine_id) REFERENCES machines (machine_id),
    CONSTRAINT fk_work_commands_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code),
    CONSTRAINT fk_work_commands_lot
        FOREIGN KEY (lot_no) REFERENCES lots (lot_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE production_logs (
    production_log_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NULL,
    lot_no VARCHAR(50) NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    input_qty INT NOT NULL DEFAULT 0,
    ok_qty INT NOT NULL DEFAULT 0,
    ng_qty INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (production_log_id),
    UNIQUE KEY uk_production_logs_event_id (event_id),
    CONSTRAINT fk_production_logs_lot
        FOREIGN KEY (lot_no) REFERENCES lots (lot_no),
    CONSTRAINT fk_production_logs_machine
        FOREIGN KEY (machine_id) REFERENCES machines (machine_id),
    CONSTRAINT fk_production_logs_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspections (
    inspection_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NULL,
    lot_no VARCHAR(50) NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    snapshot_id BIGINT NOT NULL,
    unit_seq INT NOT NULL,
    inspection_item VARCHAR(100) NOT NULL,
    measured_value DECIMAL(12, 3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    result VARCHAR(10) NOT NULL,
    inspected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (inspection_id),
    UNIQUE KEY uk_inspection_event_id (event_id),
    UNIQUE KEY uk_inspection_unit_item
        (lot_no, process_code, unit_seq, inspection_item),
    CONSTRAINT fk_inspections_lot
        FOREIGN KEY (lot_no) REFERENCES lots (lot_no),
    CONSTRAINT fk_inspections_machine
        FOREIGN KEY (machine_id) REFERENCES machines (machine_id),
    CONSTRAINT fk_inspections_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code),
    CONSTRAINT fk_inspections_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES lot_inspection_standard_snapshots (snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_unit_results (
    unit_result_id BIGINT NOT NULL AUTO_INCREMENT,
    lot_no VARCHAR(50) NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    unit_seq INT NOT NULL,
    l1_result VARCHAR(10) NULL,
    measurement_result VARCHAR(10) NULL,
    result VARCHAR(10) NULL,
    evaluation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    evaluated_at DATETIME NULL,
    PRIMARY KEY (unit_result_id),
    UNIQUE KEY uk_inspection_unit_result (lot_no, process_code, unit_seq),
    CONSTRAINT fk_inspection_unit_results_lot
        FOREIGN KEY (lot_no) REFERENCES lots (lot_no),
    CONSTRAINT fk_inspection_unit_results_machine
        FOREIGN KEY (machine_id) REFERENCES machines (machine_id),
    CONSTRAINT fk_inspection_unit_results_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE defect_histories (
    defect_history_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NULL,
    lot_no VARCHAR(50) NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    process_code VARCHAR(30) NOT NULL,
    defect_code VARCHAR(50) NOT NULL,
    defect_qty INT NOT NULL DEFAULT 0,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message VARCHAR(255) NULL,
    PRIMARY KEY (defect_history_id),
    UNIQUE KEY uk_defect_histories_event_id (event_id),
    CONSTRAINT fk_defect_histories_lot
        FOREIGN KEY (lot_no) REFERENCES lots (lot_no),
    CONSTRAINT fk_defect_histories_machine
        FOREIGN KEY (machine_id) REFERENCES machines (machine_id),
    CONSTRAINT fk_defect_histories_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code),
    CONSTRAINT fk_defect_histories_defect_code
        FOREIGN KEY (defect_code) REFERENCES defect_codes (defect_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE machine_alarm_histories (
    machine_alarm_history_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NULL,
    machine_id VARCHAR(50) NOT NULL,
    alarm_code VARCHAR(50) NOT NULL,
    alarm_level VARCHAR(20) NOT NULL DEFAULT 'ERROR',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cleared_at DATETIME NULL,
    cleared_by BIGINT NULL,
    message VARCHAR(255) NULL,
    PRIMARY KEY (machine_alarm_history_id),
    UNIQUE KEY uk_machine_alarm_histories_event_id (event_id),
    CONSTRAINT fk_machine_alarm_histories_machine
        FOREIGN KEY (machine_id) REFERENCES machines (machine_id),
    CONSTRAINT fk_machine_alarm_histories_alarm_code
        FOREIGN KEY (alarm_code) REFERENCES alarm_codes (alarm_code),
    CONSTRAINT fk_machine_alarm_histories_cleared_by
        FOREIGN KEY (cleared_by) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE machine_status_histories (
    machine_status_history_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NULL,
    machine_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    lot_no VARCHAR(50) NULL,
    process_code VARCHAR(30) NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message VARCHAR(255) NULL,
    PRIMARY KEY (machine_status_history_id),
    UNIQUE KEY uk_machine_status_histories_event_id (event_id),
    CONSTRAINT fk_machine_status_histories_machine
        FOREIGN KEY (machine_id) REFERENCES machines (machine_id),
    CONSTRAINT fk_machine_status_histories_lot
        FOREIGN KEY (lot_no) REFERENCES lots (lot_no),
    CONSTRAINT fk_machine_status_histories_process
        FOREIGN KEY (process_code) REFERENCES processes (process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------
-- 4. 초기 기준 데이터
-- --------------------------------------------------------------------------

-- 초기 관리자: admin / admin1234!
-- 애플리케이션 로그인 후 즉시 비밀번호를 변경한다.
INSERT INTO members (
    login_id, password, member_name, role, status, department, position, created_by
) VALUES (
    'admin',
    '$2a$10$DWXQRR3CGiJdJrJhGlH1Yu4iw6mujOmc5Zl9GSDn7rEcvKP/XSXEi',
    '시스템 관리자',
    'ADMIN',
    'ACTIVE',
    '관리팀',
    '관리자',
    NULL
);

INSERT INTO items (item_code, item_name, item_type, use_yn) VALUES
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
('SA-COIL-001', '코일 어셈블리', 'SA', 'Y'),
('SA-CONTACT-001', '접점 어셈블리', 'SA', 'Y'),
('SA-BODY-001', '본체 어셈블리', 'SA', 'Y'),
('SA-SEALED-001', '실링 완료품', 'SA', 'Y'),
('SA-TESTED-001', '검사 완료품', 'SA', 'Y'),
('FG-EVR-001', 'EV Relay 완제품', 'FG', 'Y');

INSERT INTO processes (
    process_code, process_name, process_order, description
) VALUES
('OP20', '코일 권선', 1, '코일 어셈블리 제작 공정'),
('OP30', '접점 가공/용접', 2, '접점 어셈블리 제작 공정'),
('OP40_OP50', '자동 조립', 3, '코일, 접점 및 내부 부품 자동 조립 공정'),
('OP60', '실링/가스 충전', 4, '제품 실링 및 가스 충전 공정'),
('OP70', '최종 검사', 5, '전기 특성 최종 검사 공정'),
('OP80', '마킹/포장', 6, '제품 마킹, 라벨링 및 포장 공정');

INSERT INTO boms (
    parent_item_code, child_item_code, quantity, process_code, use_yn
) VALUES
('SA-COIL-001', 'RM-CU-001', 1.000, 'OP20', 'Y'),
('SA-COIL-001', 'RM-BOB-001', 1.000, 'OP20', 'Y'),
('SA-CONTACT-001', 'RM-CONTACT-F', 2.000, 'OP30', 'Y'),
('SA-CONTACT-001', 'RM-CONTACT-M', 1.000, 'OP30', 'Y'),
('SA-BODY-001', 'SA-COIL-001', 1.000, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'SA-CONTACT-001', 1.000, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-CORE-001', 1.000, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-YOKE-001', 1.000, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-SPR-001', 1.000, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-TERM-HV', 2.000, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-TERM-COIL', 2.000, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-CER-001', 1.000, 'OP40_OP50', 'Y'),
('SA-BODY-001', 'RM-MAG-001', 2.000, 'OP40_OP50', 'Y'),
('SA-SEALED-001', 'SA-BODY-001', 1.000, 'OP60', 'Y'),
('SA-SEALED-001', 'RM-CASE-001', 1.000, 'OP60', 'Y'),
('SA-SEALED-001', 'RM-EPOXY-001', 1.000, 'OP60', 'Y'),
('SA-TESTED-001', 'SA-SEALED-001', 1.000, 'OP70', 'Y'),
('FG-EVR-001', 'SA-TESTED-001', 1.000, 'OP80', 'Y'),
('FG-EVR-001', 'RM-PACK-001', 1.000, 'OP80', 'Y');

INSERT INTO defect_codes (
    defect_code, defect_name, process_code, description
) VALUES
('COIL_OPEN_NG', '코일 단선', 'OP20', '코일 권선 중 단선 발생'),
('COIL_RESISTANCE_NG', '코일 저항 불량', 'OP20', '코일 저항 측정값이 기준 범위를 벗어남'),
('COIL_SHORT_NG', '코일 쇼트', 'OP20', '코일 내부 쇼트 발생'),
('WELD_STRENGTH_NG', '용접 강도 불량', 'OP30', '접점 용접 강도가 기준 미달'),
('CONTACT_RESISTANCE_NG', '접촉 저항 불량', 'OP30', '접촉 저항이 기준 범위를 벗어남'),
('CONTACT_POSITION_NG', '접점 위치 불량', 'OP30', '접점 위치가 기준 위치를 벗어남'),
('ASSY_MISALIGN_NG', '조립 위치 불량', 'OP40_OP50', '자동 조립 중 부품 정렬 불량'),
('SPRING_MISSING_NG', '스프링 누락', 'OP40_OP50', '자동 조립 중 스프링 누락'),
('CHAMBER_CRACK_NG', '챔버 균열', 'OP40_OP50', '챔버 조립 중 균열 발생'),
('GAS_PRESSURE_NG', '가스 압력 불량', 'OP60', '가스 충전 압력이 기준 범위를 벗어남'),
('SEAL_LEAK_NG', '실링 누설 불량', 'OP60', '실링 후 누설률이 기준 초과'),
('SEAL_WELD_NG', '실링 용접 불량', 'OP60', '실링 용접 상태 불량'),
('INSULATION_NG', '절연 저항 불량', 'OP70', '절연 저항 측정값이 기준 미달'),
('WITHSTAND_VOLTAGE_NG', '내전압 불량', 'OP70', '내전압 검사 기준 미달'),
('OPERATION_VOLTAGE_NG', '동작 전압 불량', 'OP70', '동작 전압이 기준 범위를 벗어남'),
('CONTACT_BOUNCE_NG', '접점 바운스 불량', 'OP70', '접점 바운스 시간이 기준 초과'),
('MARKING_NG', '마킹 불량', 'OP80', '제품 마킹 누락 또는 식별 불가'),
('PACKING_COUNT_NG', '포장 수량 불일치', 'OP80', '포장 수량이 기준 수량과 불일치');

INSERT INTO alarm_codes (
    alarm_code, alarm_name, machine_type, description
) VALUES
('COMM_DISCONNECTED', '통신 끊김', 'COMMON', 'L1과 L2의 TCP 연결이 끊어진 상태'),
('COMM_TIMEOUT', '통신 시간 초과', 'COMMON', '일정 시간 동안 L1 응답이 없는 상태'),
('EMERGENCY_STOP', '비상 정지', 'COMMON', '비상 정지 또는 안전 조건으로 정지된 상태'),
('POWER_OFF', '전원 꺼짐', 'COMMON', '장비 전원이 꺼졌거나 확인할 수 없는 상태'),
('DOOR_OPEN', '도어 열림', 'COMMON', '장비 안전 도어가 열린 상태'),
('SENSOR_ERROR', '센서 오류', 'COMMON', '센서 값 또는 응답이 비정상인 상태'),
('MOTOR_OVERLOAD', '모터 과부하', 'COMMON', '구동 모터 부하가 기준치를 초과한 상태'),
('MATERIAL_SHORTAGE', '자재 부족', 'COMMON', '공정 수행에 필요한 자재가 부족한 상태'),
('JAM_DETECTED', '걸림 발생', 'COMMON', '제품 또는 자재가 장비 내부에서 걸린 상태'),
('UNKNOWN_ERROR', '알 수 없는 오류', 'COMMON', '분류되지 않은 장비 이상'),
('WIRE_SHORTAGE', '구리선 부족', 'EQ-WIND', '권선용 구리선 부족'),
('WIRE_BREAK', '구리선 끊김', 'EQ-WIND', '권선 중 구리선 끊김'),
('WINDING_MOTOR_ERROR', '권선 모터 오류', 'EQ-WIND', '권선 모터 이상'),
('BOBBIN_FEED_ERROR', '보빈 공급 오류', 'EQ-WIND', '보빈 공급 실패'),
('WELD_POWER_ERROR', '용접 전원 오류', 'EQ-WELD', '용접 전원 또는 출력 이상'),
('WELD_TIP_WORN', '용접 팁 마모', 'EQ-WELD', '용접 팁 교체 필요'),
('CONTACT_FEED_ERROR', '접점 공급 오류', 'EQ-WELD', '접점 공급 실패'),
('WELD_OVERHEAT', '용접부 과열', 'EQ-WELD', '용접부 또는 장치 온도 초과'),
('PART_FEED_ERROR', '부품 공급 오류', 'EQ-ASSY', '조립 부품 공급 실패'),
('PICK_PLACE_ERROR', '픽앤플레이스 오류', 'EQ-ASSY', '부품 집기 또는 배치 실패'),
('ALIGN_SENSOR_ERROR', '정렬 센서 오류', 'EQ-ASSY', '부품 정렬 센서 이상'),
('ASSEMBLY_JAM', '조립 걸림', 'EQ-ASSY', '자동 조립 중 제품 걸림'),
('VACUUM_PUMP_ERROR', '진공 펌프 오류', 'EQ-SEAL', '진공 펌프 이상'),
('GAS_SUPPLY_ERROR', '가스 공급 오류', 'EQ-SEAL', '가스 공급 압력 또는 공급량 이상'),
('SEAL_HEAD_ERROR', '실링 헤드 오류', 'EQ-SEAL', '실링 헤드 동작 이상'),
('CHAMBER_PRESSURE_ERROR', '챔버 압력 오류', 'EQ-SEAL', '챔버 압력이 기준 범위를 벗어남'),
('TEST_PROBE_ERROR', '검사 프로브 오류', 'EQ-TEST', '검사 핀 접촉 또는 프로브 이상'),
('HV_TESTER_ERROR', '내전압 검사기 오류', 'EQ-TEST', '내전압 검사 장비 이상'),
('MEASURE_DEVICE_ERROR', '측정 장비 오류', 'EQ-TEST', '전기 특성 측정 장비 이상'),
('TEST_SEQUENCE_ERROR', '검사 시퀀스 오류', 'EQ-TEST', '검사 순서 또는 조건 이상'),
('LABEL_PRINTER_ERROR', '라벨 프린터 오류', 'EQ-PACK', '제품 라벨 출력 실패'),
('BARCODE_READER_ERROR', '바코드 리더 오류', 'EQ-PACK', '제품 또는 LOT 바코드 인식 실패'),
('PACK_MATERIAL_SHORTAGE', '포장 자재 부족', 'EQ-PACK', '박스 또는 라벨 부족'),
('PACK_COUNT_ERROR', '포장 카운트 오류', 'EQ-PACK', '포장 수량 카운트 불일치');

INSERT INTO machines (
    machine_id, machine_name, machine_type, process_code, status
) VALUES
('EQ-WIND-01', '코일 권선기', 'EQ-WIND', 'OP20', 'IDLE'),
('EQ-WELD-01', '접점 용접기', 'EQ-WELD', 'OP30', 'IDLE'),
('EQ-ASSY-01', '자동 조립기', 'EQ-ASSY', 'OP40_OP50', 'IDLE'),
('EQ-SEAL-01', '실링/가스 충전기', 'EQ-SEAL', 'OP60', 'IDLE'),
('EQ-TEST-01', '최종 검사기', 'EQ-TEST', 'OP70', 'IDLE'),
('EQ-PACK-01', '포장기', 'EQ-PACK', 'OP80', 'IDLE');

INSERT INTO inspection_standards (
    process_code, inspection_item, item_name, unit,
    lower_limit, upper_limit, standard_version
) VALUES
('OP20', 'COIL_RESISTANCE', '코일 저항', 'OHM', 80.000, 120.000, 1),
('OP30', 'WELD_STRENGTH', '용접 강도', 'N', 40.000, 80.000, 1),
('OP30', 'CONTACT_RESISTANCE', '접촉 저항', 'mOHM', 0.000, 50.000, 1),
('OP30', 'CONTACT_POSITION', '접점 위치 편차', 'MM', 0.000, 0.200, 1),
('OP60', 'GAS_PRESSURE', '가스 압력', 'BAR', 2.500, 3.500, 1),
('OP60', 'LEAK_RATE', '누설률', 'SCCM', 0.000, 0.500, 1),
('OP70', 'INSULATION_RESISTANCE', '절연 저항', 'MOHM', 100.000, 1000.000, 1),
('OP70', 'WITHSTAND_VOLTAGE', '내전압', 'V', 1500.000, 2000.000, 1),
('OP70', 'OPERATION_VOLTAGE', '동작 전압', 'V', 10.000, 14.000, 1),
('OP70', 'CONTACT_BOUNCE', '접점 바운스 시간', 'MS', 0.000, 5.000, 1);
```

## Backend Enum 값

SQL은 JPA의 `EnumType.STRING`과 호환되도록 다음 문자열을 사용한다.

| 테이블/컬럼 | 허용 값 |
|---|---|
| `members.role` | `ADMIN`, `MANAGER`, `OPERATOR`, `VIEWER` |
| `members.status` | `ACTIVE`, `LOCKED`, `RETIRED` |
| `items.item_type` | `RM`, `SA`, `FG` |
| `workers.status` | `ACTIVE`, `INACTIVE` |
| `machine_worker_assignments.assignment_role` | `RESPONSIBLE`, `WORKER` |
| `machines.status` | `IDLE`, `RUNNING`, `ERROR`, `STOPPED` |
| `material_lots.status` | `AVAILABLE`, `HOLD`, `USED`, `DISCARDED` |
| `work_orders.status` | `CREATED`, `RELEASED`, `RUNNING`, `COMPLETED`, `CANCELED` |
| `lots.status` | `WAITING`, `RUNNING`, `COMPLETED`, `HOLD`, `SCRAPPED` |
| `work_commands.command_type` | `START`, `STOP`, `RESUME` |
| `work_commands.status` | `PENDING`, `DISPATCHED`, `ACCEPTED`, `REJECTED`, `COMPLETED`, `CANCELED` |
| `inspections.result` | `OK`, `NG` |
| `inspection_unit_results.result` | `OK`, `NG` |

`production_logs.status`와 `machine_alarm_histories.alarm_level`은 현재 Entity에서 문자열로 관리한다. 서비스와 TCP 프로토콜이 사용하는 값은 각각 `RUNNING`/`COMPLETED`, `ERROR`이다.

## 핵심 관계

```text
members
 ├─ members.created_by
 ├─ material_lots.received_by
 ├─ work_orders.created_by
 ├─ lots.created_by
 └─ machine_alarm_histories.cleared_by

items ─ boms / material_lots / work_orders / lots
processes ─ boms / defect_codes / machines / inspection_standards
workers ─ machine_worker_assignments ─ machines
lots ─ lot_process_responsibles ─ workers
lots ─ lot_inspection_standard_snapshots ─ inspections
lots ─ work_commands / production_logs / inspections / defect_histories
machines ─ work_commands / 생산·검사·불량·알람·상태 이력
```

## 생성 결과 확인 SQL

```sql
USE mes_hm_db;

SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'mes_hm_db'
  AND table_type = 'BASE TABLE';

-- 현재 Entity 수와 동일하게 22가 나와야 한다.

SELECT machine_id, process_code, status
FROM machines
ORDER BY process_code;

SELECT process_code, inspection_item, lower_limit, upper_limit, unit
FROM inspection_standards
ORDER BY standard_id;

SELECT
    b.parent_item_code,
    parent.item_name AS parent_item_name,
    b.child_item_code,
    child.item_name AS child_item_name,
    b.quantity,
    b.process_code
FROM boms b
JOIN items parent ON parent.item_code = b.parent_item_code
JOIN items child ON child.item_code = b.child_item_code
WHERE b.use_yn = 'Y'
ORDER BY b.bom_id;
```
