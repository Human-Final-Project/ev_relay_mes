package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.BomRequestDto;
import com.human.ev_relay_mes.Dto.Response.BomResponseDto;
import com.human.ev_relay_mes.Entity.Bom;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.BomRepository;
import com.human.ev_relay_mes.Repository.ItemRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BomService {

    private final BomRepository bomRepository;
    private final ItemRepository itemRepository;
    private final ProcessRepository processRepository;

    public List<BomResponseDto> getBoms() {
        return bomRepository.findAll().stream().map(BomResponseDto::fromEntity).toList();
    }

    public List<BomResponseDto> getBom(String itemCode) {
        return bomRepository.findByParentItem_ItemCode(itemCode).stream()
                .map(BomResponseDto::fromEntity).toList();
    }

    @Transactional
    public BomResponseDto createBom(BomRequestDto dto) {
        validateItemRelation(dto);
        if (bomRepository.existsByParentItem_ItemCodeAndChildItem_ItemCodeAndProcess_ProcessCode(
                dto.getParentItemCode(), dto.getChildItemCode(), dto.getProcessCode())) {
            throw new CustomException(ErrorCode.DUPLICATE_BOM);
        }
        Bom bom = Bom.builder()
                .parentItem(findItem(dto.getParentItemCode()))
                .childItem(findItem(dto.getChildItemCode()))
                .quantity(dto.getQuantity())
                .process(findProcess(dto.getProcessCode()))
                .build();
        return BomResponseDto.fromEntity(bomRepository.save(bom));
    }

    @Transactional
    public BomResponseDto updateBom(Long id, BomRequestDto dto) {
        validateItemRelation(dto);
        Bom bom = findBom(id);
        if (bomRepository.existsByParentItem_ItemCodeAndChildItem_ItemCodeAndProcess_ProcessCodeAndBomIdNot(
                dto.getParentItemCode(), dto.getChildItemCode(), dto.getProcessCode(), id)) {
            throw new CustomException(ErrorCode.DUPLICATE_BOM);
        }
        bom.setParentItem(findItem(dto.getParentItemCode()));
        bom.setChildItem(findItem(dto.getChildItemCode()));
        bom.setQuantity(dto.getQuantity());
        bom.setProcess(findProcess(dto.getProcessCode()));
        return BomResponseDto.fromEntity(bom);
    }

    @Transactional
    public BomResponseDto updateUseYn(Long id, boolean active) {
        Bom bom = findBom(id);
        bom.setUseYn(active ? "Y" : "N");
        return BomResponseDto.fromEntity(bom);
    }

    @Transactional
    public void deleteBom(Long id) {
        bomRepository.delete(findBom(id));
    }

    private Bom findBom(Long id) {
        return bomRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.BOM_NOT_FOUND));
    }

    private Item findItem(String itemCode) {
        return itemRepository.findById(itemCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    }

    private Process findProcess(String processCode) {
        return processRepository.findById(processCode)
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
    }

    private void validateItemRelation(BomRequestDto dto) {
        if (dto.getParentItemCode().equals(dto.getChildItemCode())) {
            throw new CustomException(ErrorCode.INVALID_BOM_ITEM_RELATION);
        }
    }
}
