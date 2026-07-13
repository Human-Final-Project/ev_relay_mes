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
    // 자재 입고 화면에서 입고 품목·수량·담당자를 기준으로 신규 자재 LOT를 등록할 때 사용한다.
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
    // 자재 LOT 관리 화면에 전체 입고 이력과 현재 재고를 표시할 때 사용한다.
    public List<MaterialLotResponseDto> getMaterialLots(){
        return materialLotRepository.findAll()
                .stream()
                .map(MaterialLotResponseDto::fromEntity)
                .toList();
    }

    // 상세 조회
    // 자재 LOT 상세 화면이나 후속 업무에서 특정 LOT 정보를 확인할 때 사용한다.
    public MaterialLot getMaterialLot(Long id){
        return materialLotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("자재 LOT가 존재하지 않습니다."));
    }

    // 수정
    // 자재 LOT의 수량이나 기본 정보를 수정한 뒤 저장할 때 사용한다.
    public MaterialLot updateMaterialLot(MaterialLot materialLot){
        return materialLotRepository.save(materialLot);
    }

    // 삭제
    // 잘못 등록된 자재 LOT를 관리자 기능에서 삭제할 때 사용한다.
    public void deleteMaterialLot(Long id){
        materialLotRepository.deleteById(id);
    }

    // 상태 변경
    // 자재 LOT를 사용 가능·소진 등의 업무 상태로 변경할 때 사용한다.
    public MaterialLot updateStatus(Long id, MaterialLot.Status status){
        MaterialLot materialLot = materialLotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("자재 LOT가 존재하지 않습니다."));
        materialLot.setStatus(status);

        return materialLotRepository.save(materialLot);
    }

}
