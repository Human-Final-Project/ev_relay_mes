package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.ItemRequestDto;
import com.human.ev_relay_mes.Dto.Response.ItemResponseDto;
import com.human.ev_relay_mes.Service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class ItemController {

    private final ItemService itemService;

    @PostMapping
    public ResponseEntity<ItemResponseDto> createItem(
            @RequestBody ItemRequestDto dto){
        return ResponseEntity.ok(itemService.createItem(dto));
    }

    @GetMapping
    public ResponseEntity<List<ItemResponseDto>> getItems(){
        return ResponseEntity.ok(itemService.getItems());
    }

    @GetMapping("/{code}")
    public ResponseEntity<ItemResponseDto> getItem(
            @PathVariable String code){
        return ResponseEntity.ok(itemService.getItem(code));
    }

    @PutMapping
    public ResponseEntity<ItemResponseDto> updateItem(
            @RequestBody ItemRequestDto dto){
        return ResponseEntity.ok(itemService.updateItem(dto));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<String> deleteItem(
            @PathVariable String code){
        itemService.deleteItem(code);
        return ResponseEntity.ok("삭제 완료");
    }

}
