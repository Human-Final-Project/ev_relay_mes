package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MaterialLotRequestDto;
import com.human.ev_relay_mes.Dto.Response.MaterialLotResponseDto;
import com.human.ev_relay_mes.Entity.Bom;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.MaterialLot;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.BomRepository;
import com.human.ev_relay_mes.Repository.ItemRepository;
import com.human.ev_relay_mes.Repository.MaterialLotRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialLotService {

    private final MaterialLotRepository materialLotRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final BomRepository bomRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MaterialLotResponseDto createMaterialLot(MaterialLotRequestDto dto) {
        if (materialLotRepository.existsByMaterialLotNo(dto.getMaterialLotNo())) {
            throw new CustomException(ErrorCode.DUPLICATE_MATERIAL_LOT_NO);
        }
        Item item = itemRepository.findById(dto.getItemCode())
                .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
        if (!"Y".equalsIgnoreCase(item.getUseYn())) {
            throw new CustomException(ErrorCode.ITEM_NOT_USABLE);
        }
        if (item.getItemType() != Item.ItemType.RM && item.getItemType() != Item.ItemType.SA) {
            throw new CustomException(ErrorCode.INVALID_ITEM_TYPE,
                    "원자재 LOT에는 RM 또는 SA 품목만 등록할 수 있습니다.");
        }
        Member member = memberRepository.findById(dto.getReceivedBy())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
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
        Map<String, Integer> requiredQtyByItem = calculateRequiredQuantities(parentItemCode, productionQty);
        requiredQtyByItem.forEach((itemCode, requiredQty) -> {
            long availableQty = materialLotRepository.sumAvailableQty(
                    itemCode, MaterialLot.Status.AVAILABLE);
            if (availableQty < requiredQty) {
                throw insufficientMaterial(itemCode, requiredQty, availableQty);
            }
        });
    }

    /**
     * LOT 시작 직전에 자재 LOT을 잠그고 재고를 최종 확인한 뒤 FIFO로 차감한다.
     */
    @Transactional
    public void consumeMaterials(String parentItemCode, int productionQty) {
        consumeMaterialsInternal(parentItemCode, productionQty);
    }

    /**
     * 자동 LOT 투입용 비예외 재고 차감 API다. 자재 부족은 정상적인 대기 사유이므로
     * 트랜잭션을 rollback-only로 만들지 않고 false를 반환한다.
     */
    @Transactional
    public boolean tryConsumeMaterials(String parentItemCode, int productionQty) {
        try {
            consumeMaterialsInternal(parentItemCode, productionQty);
            return true;
        } catch (CustomException exception) {
            if (exception.getErrorCode() == ErrorCode.INSUFFICIENT_MATERIAL_QUANTITY) {
                return false;
            }
            throw exception;
        }
    }

    private void consumeMaterialsInternal(String parentItemCode, int productionQty) {
        Map<String, Integer> requiredQtyByItem = calculateRequiredQuantities(parentItemCode, productionQty);
        Map<String, List<MaterialLot>> availableLotsByItem = new LinkedHashMap<>();

        requiredQtyByItem.forEach((itemCode, requiredQty) -> {
            List<MaterialLot> lots = materialLotRepository.findAvailableLotsForUpdate(
                    itemCode, MaterialLot.Status.AVAILABLE);
            validateAvailableQuantity(itemCode, requiredQty, lots);
            availableLotsByItem.put(itemCode, lots);
        });

        requiredQtyByItem.forEach((itemCode, requiredQty) ->
                deductLots(availableLotsByItem.get(itemCode), requiredQty));
    }

    private Map<String, Integer> calculateRequiredQuantities(
            String parentItemCode, int productionQty) {
        if (productionQty <= 0) {
            throw new CustomException(ErrorCode.INVALID_LOT_QUANTITY);
        }

        Map<String, BigDecimal> requiredByItem = new LinkedHashMap<>();
        explodeBom(parentItemCode, BigDecimal.ONE, requiredByItem, new HashSet<>(), true);

        Map<String, Integer> requiredQtyByItem = new LinkedHashMap<>();
        requiredByItem.forEach((itemCode, quantityPerUnit) ->
                requiredQtyByItem.put(
                        itemCode,
                        calculateRequiredQty(quantityPerUnit, productionQty)));
        return requiredQtyByItem;
    }

    private void explodeBom(
            String parentItemCode,
            BigDecimal multiplier,
            Map<String, BigDecimal> requiredByItem,
            Set<String> path,
            boolean root) {
        if (!path.add(parentItemCode)) {
            throw new CustomException(ErrorCode.INVALID_BOM_ITEM_RELATION,
                    "BOM에 순환 참조가 존재합니다: " + parentItemCode);
        }

        List<Bom> boms = bomRepository
                .findByParentItem_ItemCodeAndUseYnOrderByChildItem_ItemCodeAsc(parentItemCode, "Y");
        if (boms.isEmpty()) {
            path.remove(parentItemCode);
            if (root) {
                throw new CustomException(ErrorCode.BOM_NOT_FOUND,
                        "생산 품목에 사용 가능한 BOM이 없습니다.");
            }
            requiredByItem.merge(parentItemCode, multiplier, BigDecimal::add);
            return;
        }

        for (Bom bom : boms) {
            Item child = bom.getChildItem();
            BigDecimal requiredMultiplier = multiplier.multiply(bom.getQuantity());
            if (child.getItemType() == Item.ItemType.RM) {
                requiredByItem.merge(child.getItemCode(), requiredMultiplier, BigDecimal::add);
            } else {
                explodeBom(child.getItemCode(), requiredMultiplier, requiredByItem, path, false);
            }
        }
        path.remove(parentItemCode);
    }

    public List<MaterialLotResponseDto> getMaterialLots() {
        return materialLotRepository.findAll().stream().map(MaterialLotResponseDto::fromEntity).toList();
    }

    public MaterialLotResponseDto getMaterialLot(Long id) {
        return MaterialLotResponseDto.fromEntity(findMaterialLot(id));
    }

    @Transactional
    public void issueMaterial(String itemCode, int quantity) {
        if (quantity <= 0) {
            throw new CustomException(ErrorCode.INVALID_MATERIAL_LOT_QUANTITY);
        }
        consumeItem(itemCode, quantity);
    }

    @Transactional
    public MaterialLotResponseDto updateStatus(Long id, MaterialLot.Status status) {
        MaterialLot materialLot = findMaterialLot(id);
        materialLot.setStatus(status);
        return MaterialLotResponseDto.fromEntity(materialLot);
    }

    @Transactional
    public void deleteMaterialLot(Long id) {
        materialLotRepository.delete(findMaterialLot(id));
    }

    private MaterialLot findMaterialLot(Long id) {
        return materialLotRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MATERIAL_LOT_NOT_FOUND));
    }

    private int calculateRequiredQty(BigDecimal quantityPerUnit, int productionQty) {
        try {
            return quantityPerUnit.multiply(BigDecimal.valueOf(productionQty))
                    .setScale(0, RoundingMode.CEILING)
                    .intValueExact();
        } catch (ArithmeticException exception) {
            throw new CustomException(ErrorCode.INVALID_BOM_QUANTITY);
        }
    }

    private void consumeItem(String itemCode, int requiredQty) {
        List<MaterialLot> lots = materialLotRepository.findAvailableLotsForUpdate(
                itemCode, MaterialLot.Status.AVAILABLE);
        validateAvailableQuantity(itemCode, requiredQty, lots);
        deductLots(lots, requiredQty);
    }

    private void validateAvailableQuantity(String itemCode, int requiredQty, List<MaterialLot> lots) {
        long availableQty = lots.stream().mapToLong(MaterialLot::getCurrentQty).sum();
        if (availableQty < requiredQty) {
            throw insufficientMaterial(itemCode, requiredQty, availableQty);
        }
    }

    private CustomException insufficientMaterial(String itemCode, int requiredQty, long availableQty) {
        return new CustomException(ErrorCode.INSUFFICIENT_MATERIAL_QUANTITY,
                itemCode + " 재고가 부족합니다. 필요: " + requiredQty + ", 가용: " + availableQty);
    }

    private void deductLots(List<MaterialLot> lots, int requiredQty) {
        int remainingQty = requiredQty;
        for (MaterialLot lot : lots) {
            if (remainingQty == 0) {
                break;
            }
            int consumedQty = Math.min(lot.getCurrentQty(), remainingQty);
            lot.setCurrentQty(lot.getCurrentQty() - consumedQty);
            remainingQty -= consumedQty;
            if (lot.getCurrentQty() == 0) {
                lot.setStatus(MaterialLot.Status.USED);
            }
        }
    }

    public record MaterialStockChangedEvent(String itemCode) {
    }
}
