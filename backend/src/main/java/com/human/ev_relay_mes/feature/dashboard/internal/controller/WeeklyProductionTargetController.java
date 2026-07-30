package com.human.ev_relay_mes.feature.dashboard.internal.controller;

import com.human.ev_relay_mes.feature.dashboard.api.WeeklyTargetRequest;
import com.human.ev_relay_mes.feature.dashboard.api.WeeklyTargetResponse;
import com.human.ev_relay_mes.feature.dashboard.internal.service.WeeklyProductionTargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard/weekly-target")
@RequiredArgsConstructor
public class WeeklyProductionTargetController {

    private final WeeklyProductionTargetService service;

    @GetMapping
    public WeeklyTargetResponse get(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return service.get(weekStart);
    }

    @PutMapping
    public WeeklyTargetResponse save(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @Valid @RequestBody WeeklyTargetRequest request) {
        return service.save(weekStart, request);
    }
}
