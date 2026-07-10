package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.BomRequestDto;
import com.human.ev_relay_mes.Dto.Response.BomResponseDto;
import com.human.ev_relay_mes.Entity.Bom;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Repository.BomRepository;
import com.human.ev_relay_mes.Repository.ItemRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BomService {

    private final BomRepository bomRepository;
    private final ItemRepository itemRepository;
    private final ProcessRepository processRepository;

    // 전체 BOM 조회
    public List<BomResponseDto> getBoms(){
        return bomRepository.findAll()
                .stream()
                .map(BomResponseDto::fromEntity)
                .toList();
    }


    // 부모 품목 기준 BOM 조회
    public List<BomResponseDto> getBom(String itemCode){
        return bomRepository.findByParentItemCode(itemCode)
                .stream()
                .map(BomResponseDto::fromEntity)
                .toList();
    }

    // BOM 등록
    public Bom createBom(BomRequestDto dto){
        Item parent = itemRepository.findById(dto.getParentItemCode()).orElseThrow();
        Item child = itemRepository.findById(dto.getChildItemCode()).orElseThrow();
        Process process = processRepository.findById(dto.getProcessCode()).orElseThrow();
        Bom bom = Bom.builder()
                .parentItem(parent)
                .childItem(child)
                .quantity(dto.getQuantity())
                .process(process)
                .build();

        return bomRepository.save(bom);
    }

    // BOM 수정
    public Bom updateBom(Long id, BomRequestDto dto){
        Bom bom = bomRepository.findById(id).orElseThrow();
        bom.setQuantity(dto.getQuantity());
        bom.setParentItem(itemRepository.findById(dto.getParentItemCode()).orElseThrow());
        bom.setChildItem(itemRepository.findById(dto.getChildItemCode()).orElseThrow());
        bom.setProcess(processRepository.findById(dto.getProcessCode()).orElseThrow());
        return bomRepository.save(bom);
    }

    public void activateBom(Long id){
        Bom bom = bomRepository.findById(id).orElseThrow();
        bom.setUseYn("Y");
        bomRepository.save(bom);
    }

    public void inactivateBom(Long id){
        Bom bom = bomRepository.findById(id).orElseThrow();
        bom.setUseYn("N");
        bomRepository.save(bom);
    }

    // BOM 삭제
    public void deleteBom(Long bomId){
        bomRepository.deleteById(bomId);
    }

}
