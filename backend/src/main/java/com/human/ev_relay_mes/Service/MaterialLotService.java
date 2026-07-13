package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MaterialLotRequestDto;
import com.human.ev_relay_mes.Dto.Response.MaterialLotResponseDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.MaterialLot;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Repository.ItemRepository;
import com.human.ev_relay_mes.Repository.MaterialLotRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialLotService {

    private final MaterialLotRepository materialLotRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;

    // 등록
    public MaterialLot createMaterialLot(MaterialLotRequestDto dto){
        Item item = itemRepository.findById(dto.getItemCode()).orElseThrow();
        Member member = memberRepository.findById(dto.getReceivedBy()).orElseThrow();

        MaterialLot lot = MaterialLot.builder()
                .materialLotNo(dto.getMaterialLotNo())
                .item(item)
                .receivedQty(dto.getReceivedQty())
                .currentQty(dto.getReceivedQty())
                .status(MaterialLot.Status.AVAILABLE)
                .receivedBy(member)
                .build();
        return materialLotRepository.save(lot);
    }

    // 전체 조회
    public List<MaterialLotResponseDto> getMaterialLots(){
        return materialLotRepository.findAll()
                .stream()
                .map(MaterialLotResponseDto::fromEntity)
                .toList();
    }

    // 상세 조회
    public MaterialLot getMaterialLot(Long id){
        return materialLotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("자재 LOT가 존재하지 않습니다."));
    }

    // 수정
    public MaterialLot updateMaterialLot(MaterialLot materialLot){
        return materialLotRepository.save(materialLot);
    }

    // 삭제
    public void deleteMaterialLot(Long id){
        materialLotRepository.deleteById(id);
    }

    // 상태 변경
    public MaterialLot updateStatus(Long id, MaterialLot.Status status){
        MaterialLot materialLot = materialLotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("자재 LOT가 존재하지 않습니다."));
        materialLot.setStatus(status);

        return materialLotRepository.save(materialLot);
    }

}
