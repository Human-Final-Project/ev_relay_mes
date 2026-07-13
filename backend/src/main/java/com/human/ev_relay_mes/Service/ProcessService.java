package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Response.ProcessResponseDto;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Entity.Process;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessService {

    private final ProcessRepository processRepository;

    // 공정 전체 조회
    public List<ProcessResponseDto> getProcesses(){
        return processRepository.findAll()
                .stream()
                .map(ProcessResponseDto::fromEntity)
                .toList();
    }

    // 공정 코드로 조회
    public Process getProcess(String processCode) {
        return processRepository.findById(processCode)
                .orElseThrow(() -> new RuntimeException("공정을 찾을 수 없습니다."));
    }
}
