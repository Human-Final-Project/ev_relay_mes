-- LOT 투입 시 실제 차감된 원자재 LOT 이력
-- 현재 application.properties의 ddl-auto=create 사용 시 Hibernate가 자동 생성한다.
-- 기존 DB를 유지하면서 수동 반영할 때만 실행한다.

CREATE TABLE IF NOT EXISTS lot_material_usages (
    lot_material_usage_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_id BIGINT NOT NULL,
    material_lot_id BIGINT NOT NULL,
    used_qty INT NOT NULL,
    used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_lot_material_usage
        UNIQUE (lot_id, material_lot_id),

    CONSTRAINT fk_lot_material_usages_lot
        FOREIGN KEY (lot_id)
        REFERENCES lots(lot_id),

    CONSTRAINT fk_lot_material_usages_material_lot
        FOREIGN KEY (material_lot_id)
        REFERENCES material_lots(material_lot_id)
);
