package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.ProcessRequestDto;
import com.human.ev_relay_mes.Dto.Response.ProcessResponseDto;
import com.human.ev_relay_mes.Entity.Process;
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

    @Transactional
    public ProcessResponseDto createProcess(ProcessRequestDto dto) {
        validateUnique(dto, null);
        Process process = Process.builder()
                .processCode(dto.getProcessCode())
                .processName(dto.getProcessName())
                .processOrder(dto.getProcessOrder())
                .description(dto.getDescription())
                .useYn("Y")
                .build();
        return ProcessResponseDto.fromEntity(processRepository.save(process));
    }

    @Transactional
    public ProcessResponseDto updateProcess(String processCode, ProcessRequestDto dto) {
        if (!processCode.equals(dto.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Process process = findProcess(processCode);
        validateUnique(dto, processCode);
        process.setProcessName(dto.getProcessName());
        process.setProcessOrder(dto.getProcessOrder());
        process.setDescription(dto.getDescription());
        return ProcessResponseDto.fromEntity(process);
    }

    @Transactional
    public ProcessResponseDto updateUseYn(String processCode, boolean active) {
        Process process = findProcess(processCode);
        process.setUseYn(active ? "Y" : "N");
        return ProcessResponseDto.fromEntity(process);
    }

    private Process findProcess(String processCode) {
        return processRepository.findById(processCode)
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
    }

    private void validateUnique(ProcessRequestDto dto, String currentCode) {
        if (currentCode == null && processRepository.existsById(dto.getProcessCode())) {
            throw new CustomException(ErrorCode.DUPLICATE_PROCESS_CODE);
        }
        boolean duplicatedOrder = currentCode == null
                ? processRepository.existsByProcessOrder(dto.getProcessOrder())
                : processRepository.existsByProcessOrderAndProcessCodeNot(dto.getProcessOrder(), currentCode);
        if (duplicatedOrder) {
            throw new CustomException(ErrorCode.INVALID_PROCESS_ORDER);
        }
    }
}
