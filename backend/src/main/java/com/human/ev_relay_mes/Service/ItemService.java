package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.ItemRequestDto;
import com.human.ev_relay_mes.Dto.Response.ItemResponseDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;

    @Transactional
    public ItemResponseDto createItem(ItemRequestDto dto) {
        if (itemRepository.existsById(dto.getItemCode())) {
            throw new CustomException(ErrorCode.DUPLICATE_ITEM_CODE);
        }
        Item item = Item.builder()
                .itemCode(dto.getItemCode())
                .itemName(dto.getItemName())
                .itemType(parseItemType(dto.getItemType()))
                .build();
        return ItemResponseDto.fromEntity(itemRepository.save(item));
    }

    public List<ItemResponseDto> getItems() {
        return itemRepository.findAll().stream().map(ItemResponseDto::fromEntity).toList();
    }

    public ItemResponseDto getItem(String code) {
        return ItemResponseDto.fromEntity(findItem(code));
    }

    @Transactional
    public ItemResponseDto updateItem(String code, ItemRequestDto dto) {
        if (!code.equals(dto.getItemCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "경로와 요청 본문의 품목 코드가 다릅니다.");
        }
        Item item = findItem(code);
        item.setItemName(dto.getItemName());
        item.setItemType(parseItemType(dto.getItemType()));
        return ItemResponseDto.fromEntity(item);
    }

    @Transactional
    public ItemResponseDto updateUseYn(String code, boolean active) {
        Item item = findItem(code);
        item.setUseYn(active ? "Y" : "N");
        return ItemResponseDto.fromEntity(item);
    }

    @Transactional
    public void deleteItem(String code) {
        itemRepository.delete(findItem(code));
    }

    private Item findItem(String code) {
        return itemRepository.findById(code)
                .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    }

    private Item.ItemType parseItemType(String itemType) {
        try {
            return Item.ItemType.valueOf(itemType.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_ITEM_TYPE);
        }
    }
}
