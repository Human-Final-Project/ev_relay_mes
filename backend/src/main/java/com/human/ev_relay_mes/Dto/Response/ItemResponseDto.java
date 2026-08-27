package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.Item;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemResponseDto {
    private String itemCode;
    private String itemName;
    private String itemType;
    private String useYn;

    public static ItemResponseDto fromEntity(Item item){

        return ItemResponseDto.builder()
                .itemCode(item.getItemCode())
                .itemName(item.getItemName())
                .itemType(item.getItemType().name())
                .useYn(item.getUseYn())
                .build();
    }

}
