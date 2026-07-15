package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItemRequestDto {

    @NotBlank
    @Size(max = 50)
    private String itemCode;

    @NotBlank
    @Size(max = 100)
    private String itemName;

    @NotBlank
    @Pattern(regexp = "(?i)RM|SA|FG", message = "itemType must be RM, SA, or FG")
    private String itemType;

}
