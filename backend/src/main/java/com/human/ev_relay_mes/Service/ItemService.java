package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.ItemRequestDto;
import com.human.ev_relay_mes.Dto.Response.ItemResponseDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;

    // 등록
    public ItemResponseDto createItem(ItemRequestDto dto){
        Item item = Item.builder()
                .itemCode(dto.getItemCode())
                .itemName(dto.getItemName())
                .itemType(Item.ItemType.valueOf(dto.getItemType()))
                .build();
        itemRepository.save(item);
        return ItemResponseDto.fromEntity(item);
    }

    // 전체조회
    public List<ItemResponseDto> getItems(){
        return itemRepository.findAll()
                .stream()
                .map(ItemResponseDto::fromEntity)
                .toList();
    }

    // 상세조회
    public ItemResponseDto getItem(String code){
        Item item = itemRepository.findById(code)
                .orElseThrow(() -> new RuntimeException("품목이 없습니다."));
        return ItemResponseDto.fromEntity(item);
    }

    // 수정
    public ItemResponseDto updateItem(ItemRequestDto dto){
        Item item = itemRepository.findById(dto.getItemCode())
                .orElseThrow(() -> new RuntimeException("품목이 없습니다."));
        item.setItemName(dto.getItemName());
        item.setItemType(Item.ItemType.valueOf(dto.getItemType()));
        itemRepository.save(item);
        return ItemResponseDto.fromEntity(item);
    }

    // 삭제
    public void deleteItem(String code){
        itemRepository.deleteById(code);
    }

}

