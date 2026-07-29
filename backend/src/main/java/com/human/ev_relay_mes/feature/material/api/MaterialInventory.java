package com.human.ev_relay_mes.feature.material.api;

import com.human.ev_relay_mes.feature.production.api.Lot;

import java.util.List;

public interface MaterialInventory {

    MaterialLotResponseDto createMaterialLot(MaterialLotRequestDto dto);

    List<MaterialLotResponseDto> getMaterialLots();

    MaterialLotResponseDto getMaterialLot(Long id);

    MaterialLotResponseDto updateStatus(Long id, MaterialLot.Status status);

    void deleteMaterialLot(Long id);

    void issueMaterial(String itemCode, int quantity);

    void validateMaterialAvailability(String parentItemCode, int productionQty);

    boolean tryConsumeMaterials(Lot productionLot);

    List<MaterialLot> getMaterialLotEntities();
}
