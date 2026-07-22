package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Service.SystemConnectionStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mes/system-connections")
@RequiredArgsConstructor
public class SystemConnectionController {

    private final SystemConnectionStatusService systemConnectionStatusService;

    @GetMapping
    public SystemConnectionStatusService.SystemConnectionStatus getStatus() {
        return systemConnectionStatusService.getStatus();
    }
}
