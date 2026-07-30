package com.human.ev_relay_mes.feature.material.internal.service;

import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.auth.api.MemberLookup;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.material.api.MaterialInventory;
import com.human.ev_relay_mes.feature.material.api.MaterialLot;
import com.human.ev_relay_mes.feature.material.api.MaterialLotRequestDto;
import com.human.ev_relay_mes.feature.material.api.MaterialLotResponseDto;
import com.human.ev_relay_mes.feature.material.api.MaterialStockChangedEvent;
import com.human.ev_relay_mes.feature.material.internal.repository.MaterialLotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialLotService implements MaterialInventory {

    private final MaterialLotRepository materialLotRepository;
    private final MasterDataLookup masterDataLookup;
    private final MemberLookup memberLookup;
    private final ApplicationEventPublisher eventPublisher;
    private final BomRequirementCalculator requirementCalculator;
    private final MaterialStockAllocator stockAllocator;

    @Transactional
    public MaterialLotResponseDto createMaterialLot(MaterialLotRequestDto dto) {
        if (materialLotRepository.existsByMaterialLotNo(dto.getMaterialLotNo())) {
            throw new CustomException(ErrorCode.DUPLICATE_MATERIAL_LOT_NO);
        }
        Item item = masterDataLookup.getRequiredItem(dto.getItemCode());
        if (!"Y".equalsIgnoreCase(item.getUseYn())) {
            throw new CustomException(ErrorCode.ITEM_NOT_USABLE);
        }
        if (item.getItemType() != Item.ItemType.RM && item.getItemType() != Item.ItemType.SA) {
            throw new CustomException(ErrorCode.INVALID_ITEM_TYPE,
                    "원자재 LOT에는 RM 또는 SA 품목만 등록할 수 있습니다.");
        }
        Member member = memberLookup.getRequiredById(dto.getReceivedBy());
        MaterialLot lot = MaterialLot.builder()
                .materialLotNo(dto.getMaterialLotNo())
                .item(item)
                .receivedQty(dto.getReceivedQty())
                .currentQty(dto.getReceivedQty())
                .status(MaterialLot.Status.AVAILABLE)
                .receivedBy(member)
                .build();
        MaterialLotResponseDto response = MaterialLotResponseDto.fromEntity(materialLotRepository.save(lot));
        eventPublisher.publishEvent(new MaterialStockChangedEvent(item.getItemCode()));
        return response;
    }

    /**
     * 작업지시/LOT 생성 전에 현재 재고로 생산 가능한지만 확인한다.
     * 실제 재고 차감은 LOT 시작 시 consumeMaterials()에서 다시 검증한 뒤 수행한다.
     */
    public void validateMaterialAvailability(String parentItemCode, int productionQty) {
        stockAllocator.validateAvailableStock(
                requirementCalculator.calculate(parentItemCode, productionQty));
    }

    /**
     * LOT 시작 직전에 자재 LOT을 잠그고 재고를 최종 확인한 뒤 FIFO로 차감한다.
     */
    @Transactional
    public void consumeMaterials(String parentItemCode, int productionQty) {
        consumeMaterialsInternal(parentItemCode, productionQty, null);
    }

    @Transactional
    public void consumeMaterials(Lot productionLot) {
        consumeMaterialsInternal(
                productionLot.getItem().getItemCode(),
                productionLot.getInputQty(),
                productionLot);
    }

    /**
     * 자동 LOT 투입용 비예외 재고 차감 API다. 자재 부족은 정상적인 대기 사유이므로
     * 트랜잭션을 rollback-only로 만들지 않고 false를 반환한다.
     */
    @Transactional
    public boolean tryConsumeMaterials(String parentItemCode, int productionQty) {
        try {
            consumeMaterialsInternal(parentItemCode, productionQty, null);
            return true;
        } catch (CustomException exception) {
            if (exception.getErrorCode() == ErrorCode.INSUFFICIENT_MATERIAL_QUANTITY) {
                return false;
            }
            throw exception;
        }
    }

    @Transactional
    public boolean tryConsumeMaterials(Lot productionLot) {
        try {
            consumeMaterialsInternal(
                    productionLot.getItem().getItemCode(),
                    productionLot.getInputQty(),
                    productionLot);
            return true;
        } catch (CustomException exception) {
            if (exception.getErrorCode() == ErrorCode.INSUFFICIENT_MATERIAL_QUANTITY) {
                return false;
            }
            throw exception;
        }
    }

    private void consumeMaterialsInternal(String parentItemCode, int productionQty, Lot productionLot) {
        Map<String, Integer> requiredQtyByItem = requirementCalculator.calculate(
                parentItemCode, productionQty);
        stockAllocator.allocate(requiredQtyByItem, productionLot);
        requiredQtyByItem.keySet().forEach(itemCode ->
                eventPublisher.publishEvent(new MaterialStockChangedEvent(itemCode)));
    }

    public List<MaterialLotResponseDto> getMaterialLots() {
        return materialLotRepository.findAll().stream().map(MaterialLotResponseDto::fromEntity).toList();
    }

    @Override
    public List<MaterialLot> getMaterialLotEntities() {
        return materialLotRepository.findAll();
    }

    @Override
    public long getAvailableQuantity(String itemCode) {
        return materialLotRepository.sumAvailableQty(
                itemCode, MaterialLot.Status.AVAILABLE);
    }

    public MaterialLotResponseDto getMaterialLot(Long id) {
        return MaterialLotResponseDto.fromEntity(findMaterialLot(id));
    }

    @Transactional
    public void issueMaterial(String itemCode, int quantity) {
        if (quantity <= 0) {
            throw new CustomException(ErrorCode.INVALID_MATERIAL_LOT_QUANTITY);
        }
        stockAllocator.allocateSingleItem(itemCode, quantity);
        eventPublisher.publishEvent(new MaterialStockChangedEvent(itemCode));
    }

    @Transactional
    public MaterialLotResponseDto updateStatus(Long id, MaterialLot.Status status) {
        MaterialLot materialLot = findMaterialLot(id);
        materialLot.setStatus(status);
        eventPublisher.publishEvent(new MaterialStockChangedEvent(
                materialLot.getItem().getItemCode()));
        return MaterialLotResponseDto.fromEntity(materialLot);
    }

    @Transactional
    public void deleteMaterialLot(Long id) {
        MaterialLot materialLot = findMaterialLot(id);
        String itemCode = materialLot.getItem().getItemCode();
        materialLotRepository.delete(materialLot);
        eventPublisher.publishEvent(new MaterialStockChangedEvent(itemCode));
    }

    private MaterialLot findMaterialLot(Long id) {
        return materialLotRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MATERIAL_LOT_NOT_FOUND));
    }

}
