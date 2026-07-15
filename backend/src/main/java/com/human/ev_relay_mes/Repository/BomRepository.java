package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Bom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BomRepository extends JpaRepository<Bom, Long> {
    // 제품별 BOM 상세 화면이나 자재 소요량 계산에서 구성 품목 목록을 가져올 때 사용한다.
    List<Bom> findByParentItem_ItemCode(String parentItemCode);

    List<Bom> findByParentItem_ItemCodeAndUseYnOrderByChildItem_ItemCodeAsc(
            String parentItemCode, String useYn);

    boolean existsByParentItem_ItemCodeAndChildItem_ItemCodeAndProcess_ProcessCode(
            String parentItemCode, String childItemCode, String processCode);

    boolean existsByParentItem_ItemCodeAndChildItem_ItemCodeAndProcess_ProcessCodeAndBomIdNot(
            String parentItemCode, String childItemCode, String processCode, Long bomId);
}
