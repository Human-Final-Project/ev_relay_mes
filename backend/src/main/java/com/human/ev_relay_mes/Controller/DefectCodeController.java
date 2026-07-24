package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Response.DefectCodeResponseDto;
import com.human.ev_relay_mes.Service.DefectCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/defect-codes")
@RequiredArgsConstructor
public class DefectCodeController {
    private final DefectCodeService defectCodeService;

    @GetMapping
    public List<DefectCodeResponseDto> getAll(@RequestParam(required = false) String processCode) {
        return defectCodeService.getDefectCodes(processCode);
    }

    @GetMapping("/{code}")
    public DefectCodeResponseDto getOne(@PathVariable String code) {
        return defectCodeService.getDefectCode(code);
    }
}
