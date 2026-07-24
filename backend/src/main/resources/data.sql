-- Initial master data derived from docs/reference/sql_code_ver.2.md.
-- With spring.jpa.hibernate.ddl-auto=create, this file runs after Hibernate
-- recreates the schema on every Backend startup.

-- JPA's generated schema declares created_at as NOT NULL but does not add the
-- database defaults present in sql_code_ver.2.md. Restore those defaults before
-- executing the original seed INSERT statements.
ALTER TABLE members MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE items MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE processes MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE boms MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE defect_codes MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE alarm_codes MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE machines MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE inspection_standards MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- Initial administrator: admin / admin1234!
-- Change this password immediately after the first login.
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
-- EQ-WIND: WARN 2 / ERROR 2
('WIRE_TENSION_WARN', '구리선 장력 주의', 'EQ-WIND', '권선 중 구리선 장력이 권장 범위에 가까워 점검이 필요한 상태'),
('WINDING_VIBRATION_WARN', '권선 진동 증가', 'EQ-WIND', '권선 축 진동이 평상시보다 증가했으나 작업은 계속 가능한 상태'),
('WIRE_BREAK', '구리선 단선', 'EQ-WIND', '권선 작업 중 구리선이 끊어져 작업을 계속할 수 없는 상태'),
('WINDING_MOTOR_ERROR', '권선 모터 이상', 'EQ-WIND', '권선 모터의 회전 또는 구동 상태에 이상이 발생한 상태'),

-- EQ-WELD: WARN 2 / ERROR 2
('WELD_TIP_WEAR_WARN', '용접 팁 마모 주의', 'EQ-WELD', '용접 팁 마모도가 관리 기준에 가까워 점검이 필요한 상태'),
('WELD_TEMPERATURE_WARN', '용접부 온도 상승', 'EQ-WELD', '용접부 또는 용접 장치 온도가 권장 범위보다 높아진 상태'),
('WELD_POWER_ERROR', '용접 출력 이상', 'EQ-WELD', '용접 전원 또는 출력이 허용 범위를 벗어나 작업을 중단해야 하는 상태'),
('ELECTRODE_ALIGNMENT_ERROR', '전극 정렬 오류', 'EQ-WELD', '용접 전극 위치가 기준에서 벗어나 정상 용접을 수행할 수 없는 상태'),

-- EQ-ASSY: WARN 2 / ERROR 2
('ALIGNMENT_DEVIATION_WARN', '조립 정렬 편차', 'EQ-ASSY', '부품 정렬 위치가 권장 범위에 가까워 보정이 필요한 상태'),
('PICK_CYCLE_DELAY_WARN', '픽앤플레이스 지연', 'EQ-ASSY', '부품 집기 및 배치 시간이 평상시보다 길어져 점검이 필요한 상태'),
('PICK_PLACE_ERROR', '픽앤플레이스 동작 오류', 'EQ-ASSY', '부품 집기 또는 배치 동작에 실패하여 조립을 계속할 수 없는 상태'),
('ASSEMBLY_JAM', '조립부 걸림', 'EQ-ASSY', '자동 조립 중 부품 또는 제품이 걸려 설비가 정지한 상태'),

-- EQ-SEAL: WARN 2 / ERROR 2
('VACUUM_LEVEL_WARN', '진공도 저하 주의', 'EQ-SEAL', '챔버 진공도가 권장 범위 하한에 가까워 점검이 필요한 상태'),
('CHAMBER_TEMPERATURE_WARN', '챔버 온도 상승', 'EQ-SEAL', '챔버 내부 온도가 평상시보다 높아진 상태'),
('VACUUM_PUMP_ERROR', '진공 펌프 이상', 'EQ-SEAL', '진공 펌프가 정상 압력을 형성하지 못해 작업을 계속할 수 없는 상태'),
('CHAMBER_PRESSURE_ERROR', '챔버 압력 이상', 'EQ-SEAL', '챔버 내부 압력이 허용 범위를 벗어나 실링 작업을 중단한 상태'),

-- EQ-TEST: WARN 2 / ERROR 2
('PROBE_CONTACT_WARN', '검사 프로브 접촉 주의', 'EQ-TEST', '검사 프로브 접촉 상태가 불안정해 점검이 필요한 상태'),
('MEASURE_DRIFT_WARN', '측정값 드리프트', 'EQ-TEST', '측정 장비 기준값이 서서히 변해 교정 점검이 필요한 상태'),
('HV_TESTER_ERROR', '내전압 검사기 이상', 'EQ-TEST', '내전압 검사 장비가 정상 시험 전압을 형성하지 못하는 상태'),
('TEST_SEQUENCE_ERROR', '검사 시퀀스 오류', 'EQ-TEST', '검사 순서 또는 조건이 정상적으로 실행되지 않아 검사를 중단한 상태'),

-- EQ-PACK: WARN 2 / ERROR 2
('LABEL_PRINT_QUALITY_WARN', '라벨 인쇄 품질 저하', 'EQ-PACK', '라벨 인쇄 선명도가 낮아져 프린터 점검이 필요한 상태'),
('BARCODE_READ_RATE_WARN', '바코드 인식률 저하', 'EQ-PACK', '바코드 인식 성공률이 평상시보다 낮아진 상태'),
('LABEL_PRINTER_ERROR', '라벨 프린터 동작 오류', 'EQ-PACK', '라벨 프린터가 정상적으로 동작하지 않아 포장 작업을 계속할 수 없는 상태'),
('BARCODE_READER_ERROR', '바코드 리더 동작 오류', 'EQ-PACK', '바코드 리더가 제품 또는 LOT 정보를 인식하지 못하는 상태');

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

-- Give every active raw material an initial available stock of 1,000 units.
-- Example LOT number: INIT-RM-CU-001-001
INSERT INTO material_lots (
    material_lot_no, item_code, received_qty, current_qty,
    status, received_at, received_by
)
SELECT
    CONCAT('INIT-', item_code, '-001'),
    item_code,
    1000,
    1000,
    'AVAILABLE',
    CURRENT_TIMESTAMP(6),
    (SELECT member_id FROM members WHERE login_id = 'admin')
FROM items
WHERE item_type = 'RM'
  AND use_yn = 'Y';
