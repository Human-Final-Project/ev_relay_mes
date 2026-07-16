package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.ProcessRequestDto;
import com.human.ev_relay_mes.Dto.Response.ProcessResponseDto;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessService {

    private final ProcessRepository processRepository;

    public List<ProcessResponseDto> getProcesses() {
        return processRepository.findAll().stream().map(ProcessResponseDto::fromEntity).toList();
    }

    public ProcessResponseDto getProcess(String processCode) {
        return ProcessResponseDto.fromEntity(findProcess(processCode));
    }

    @Transactional
    public ProcessResponseDto createProcess(ProcessRequestDto dto) {
        if (processRepository.existsByProcessCode(dto.getProcessCode())) {
            throw new CustomException(ErrorCode.DUPLICATE_PROCESS_CODE);
        }
        validateOrder(dto.getProcessOrder(), null);
        Process process = Process.builder()
                .processCode(dto.getProcessCode())
                .processName(dto.getProcessName())
                .processOrder(dto.getProcessOrder())
                .description(dto.getDescription())
                .build();
        return ProcessResponseDto.fromEntity(processRepository.save(process));
    }

    @Transactional
    public ProcessResponseDto updateProcess(String processCode, ProcessRequestDto dto) {
        if (!processCode.equals(dto.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "경로와 요청 본문의 공정 코드가 다릅니다.");
        }
        Process process = findProcess(processCode);
        validateOrder(dto.getProcessOrder(), processCode);
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

    @Transactional
    public void deleteProcess(String processCode) {
        processRepository.delete(findProcess(processCode));
    }

    private Process findProcess(String processCode) {
        return processRepository.findById(processCode)
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
    }

    private void validateOrder(Integer processOrder, String currentCode) {
        boolean duplicate = currentCode == null
                ? processRepository.existsByProcessOrder(processOrder)
                : processRepository.existsByProcessOrderAndProcessCodeNot(processOrder, currentCode);
        if (duplicate) {
            throw new CustomException(ErrorCode.INVALID_PROCESS_ORDER, "이미 사용 중인 공정 순서입니다.");
        }
    }
}
