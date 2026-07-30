package com.human.ev_relay_mes.feature.workforce.internal.service;

import com.human.ev_relay_mes.common.util.RequestValues;
import com.human.ev_relay_mes.feature.workforce.api.WorkerRequestDto;
import com.human.ev_relay_mes.feature.workforce.api.WorkerResponseDto;
import com.human.ev_relay_mes.feature.workforce.api.Worker;
import com.human.ev_relay_mes.feature.workforce.api.WorkforceMemberLinkOperations;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.workforce.internal.repository.MachineWorkerAssignmentRepository;
import com.human.ev_relay_mes.feature.workforce.internal.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerService implements WorkforceMemberLinkOperations {

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
        List<Worker> workers = RequestValues.isBlank(status)
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

    @Override
    @Transactional
    public void createOrLinkOperator(Member member) {
        Worker worker = workerRepository.findByMember_MemberId(member.getMemberId())
                .orElseGet(() -> workerRepository.findByWorkerNo(member.getLoginId())
                        .orElseGet(() -> Worker.builder()
                                .workerNo(member.getLoginId())
                                .status(Worker.Status.ACTIVE)
                                .build()));
        if (worker.getMember() != null
                && !worker.getMember().getMemberId().equals(member.getMemberId())) {
            throw new CustomException(
                    ErrorCode.WORKER_NO_DUPLICATED,
                    "동일한 사번의 작업자가 다른 사용자 계정과 연결되어 있습니다.");
        }
        worker.setMember(member);
        synchronize(worker, member);
        workerRepository.save(worker);
    }

    @Override
    @Transactional
    public void synchronizeLinkedWorker(Member member) {
        workerRepository.findByMember_MemberId(member.getMemberId())
                .ifPresent(worker -> synchronize(worker, member));
    }

    private void synchronize(Worker worker, Member member) {
        worker.setWorkerName(member.getMemberName());
        worker.setDepartment(normalize(member.getDepartment()));
        worker.setPosition(normalize(member.getPosition()));
        worker.setStatus(member.getStatus() == Member.Status.ACTIVE
                ? Worker.Status.ACTIVE
                : Worker.Status.INACTIVE);
    }

    private Worker.Status parseStatus(String status) {
        if (RequestValues.isBlank(status)) {
            return Worker.Status.ACTIVE;
        }
        return RequestValues.parseEnum(
                Worker.Status.class,
                status,
                ErrorCode.INVALID_WORKER_STATUS);
    }

    private String normalize(String value) {
        return RequestValues.trimToNull(value);
    }
}
