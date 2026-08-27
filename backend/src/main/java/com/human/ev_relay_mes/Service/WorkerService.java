package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.WorkerRequestDto;
import com.human.ev_relay_mes.Dto.Response.WorkerResponseDto;
import com.human.ev_relay_mes.Entity.Worker;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MachineWorkerAssignmentRepository;
import com.human.ev_relay_mes.Repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final MachineWorkerAssignmentRepository assignmentRepository;

    @Transactional
    public WorkerResponseDto create(WorkerRequestDto dto) {
        String workerNo = dto.getWorkerNo().trim();
        if (workerRepository.existsByWorkerNo(workerNo)) {
            throw new CustomException(ErrorCode.WORKER_NO_DUPLICATED);
        }
        Worker worker = Worker.builder()
                .workerNo(workerNo)
                .workerName(dto.getWorkerName().trim())
                .department(normalize(dto.getDepartment()))
                .position(normalize(dto.getPosition()))
                .status(parseStatus(dto.getStatus()))
                .build();
        return WorkerResponseDto.fromEntity(workerRepository.save(worker));
    }

    public List<WorkerResponseDto> getWorkers(String status) {
        List<Worker> workers = isBlank(status)
                ? workerRepository.findAllByOrderByWorkerNoAsc()
                : workerRepository.findByStatusOrderByWorkerNoAsc(parseStatus(status));
        return workers.stream().map(WorkerResponseDto::fromEntity).toList();
    }

    public WorkerResponseDto getWorker(Long workerId) {
        return WorkerResponseDto.fromEntity(findWorker(workerId));
    }

    @Transactional
    public WorkerResponseDto update(Long workerId, WorkerRequestDto dto) {
        Worker worker = findWorker(workerId);
        String workerNo = dto.getWorkerNo().trim();
        if (workerRepository.existsByWorkerNoAndWorkerIdNot(workerNo, workerId)) {
            throw new CustomException(ErrorCode.WORKER_NO_DUPLICATED);
        }
        Worker.Status targetStatus = parseStatus(dto.getStatus());
        if (targetStatus == Worker.Status.INACTIVE
                && assignmentRepository.existsByWorker_WorkerId(workerId)) {
            throw new CustomException(
                    ErrorCode.WORKER_ASSIGNED_TO_MACHINE,
                    "설비 배치를 해제한 뒤 작업자를 비활성화해야 합니다.");
        }
        worker.setWorkerNo(workerNo);
        worker.setWorkerName(dto.getWorkerName().trim());
        worker.setDepartment(normalize(dto.getDepartment()));
        worker.setPosition(normalize(dto.getPosition()));
        worker.setStatus(targetStatus);
        return WorkerResponseDto.fromEntity(worker);
    }

    @Transactional
    public WorkerResponseDto updateActive(Long workerId, boolean active) {
        Worker worker = findWorker(workerId);
        if (!active && assignmentRepository.existsByWorker_WorkerId(workerId)) {
            throw new CustomException(
                    ErrorCode.WORKER_ASSIGNED_TO_MACHINE,
                    "설비 배치를 해제한 뒤 작업자를 비활성화해야 합니다.");
        }
        worker.setStatus(active ? Worker.Status.ACTIVE : Worker.Status.INACTIVE);
        return WorkerResponseDto.fromEntity(worker);
    }

    public Worker findActiveWorker(Long workerId) {
        Worker worker = findWorker(workerId);
        if (worker.getStatus() != Worker.Status.ACTIVE) {
            throw new CustomException(ErrorCode.WORKER_INACTIVE);
        }
        return worker;
    }

    public Worker findWorker(Long workerId) {
        return workerRepository.findById(workerId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKER_NOT_FOUND));
    }

    private Worker.Status parseStatus(String status) {
        if (isBlank(status)) {
            return Worker.Status.ACTIVE;
        }
        try {
            return Worker.Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.INVALID_WORKER_STATUS);
        }
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
