package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.ItemRequestDto;
import com.human.ev_relay_mes.Dto.Response.ItemResponseDto;
import com.human.ev_relay_mes.Service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @PostMapping
    public ResponseEntity<ItemResponseDto> createItem(@Valid @RequestBody ItemRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.createItem(dto));
    }

    @GetMapping
    public List<ItemResponseDto> getItems() {
        return itemService.getItems();
    }

    @GetMapping("/{code}")
    public ItemResponseDto getItem(@PathVariable String code) {
        return itemService.getItem(code);
    }

    @PutMapping("/{code}")
    public ItemResponseDto updateItem(
            @PathVariable String code, @Valid @RequestBody ItemRequestDto dto) {
        return itemService.updateItem(code, dto);
    }

    @PatchMapping("/{code}/active")
    public ItemResponseDto updateActive(@PathVariable String code, @RequestParam boolean active) {
        return itemService.updateUseYn(code, active);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteItem(@PathVariable String code) {
        itemService.deleteItem(code);
        return ResponseEntity.noContent().build();
    }
}
