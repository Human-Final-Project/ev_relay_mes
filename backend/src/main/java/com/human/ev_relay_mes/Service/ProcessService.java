package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Response.ProcessResponseDto;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessService {
    private final ProcessRepository processRepository;

    public List<ProcessResponseDto> getProcesses() {
        return processRepository.findAll(Sort.by("processOrder")).stream()
                .map(ProcessResponseDto::fromEntity).toList();
    }

    public ProcessResponseDto getProcess(String processCode) {
        return ProcessResponseDto.fromEntity(processRepository.findById(processCode)
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND)));
    }
}
