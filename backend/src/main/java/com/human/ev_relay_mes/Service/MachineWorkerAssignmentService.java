package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Response.MachineWorkerAssignmentResponseDto;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineWorkerAssignment;
import com.human.ev_relay_mes.Entity.Worker;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MachineWorkerAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineWorkerAssignmentService {

    private final MachineWorkerAssignmentRepository assignmentRepository;
    private final MachineRepository machineRepository;
    private final WorkerService workerService;

    public List<MachineWorkerAssignmentResponseDto> getAssignments(String machineId) {
        findMachine(machineId);
        return assignmentRepository
                .findByMachine_MachineIdOrderByAssignmentRoleAscAssignedAtAsc(machineId)
                .stream()
                .map(MachineWorkerAssignmentResponseDto::fromEntity)
                .toList();
    }

    public List<MachineWorkerAssignmentResponseDto> getAllAssignments() {
        return assignmentRepository.findAll().stream()
                .map(MachineWorkerAssignmentResponseDto::fromEntity)
                .toList();
    }

    @Transactional
    public MachineWorkerAssignmentResponseDto assignResponsible(
            String machineId,
            Long workerId) {
        Machine machine = findMachineForUpdate(machineId);
        Worker worker = workerService.findActiveWorker(workerId);
        Optional<MachineWorkerAssignment> target = assignmentRepository
                .findByMachine_MachineIdAndWorker_WorkerId(machineId, workerId);
        Optional<MachineWorkerAssignment> current = assignmentRepository
                .findByMachine_MachineIdAndAssignmentRole(
                        machineId,
                        MachineWorkerAssignment.AssignmentRole.RESPONSIBLE);

        if (current.isPresent()
                && current.get().getWorker().getWorkerId().equals(workerId)) {
            return MachineWorkerAssignmentResponseDto.fromEntity(current.get());
        }
        current.ifPresent(assignmentRepository::delete);

        MachineWorkerAssignment assignment = target.orElseGet(() ->
                MachineWorkerAssignment.builder()
                        .machine(machine)
                        .worker(worker)
                        .build());
        assignment.setAssignmentRole(MachineWorkerAssignment.AssignmentRole.RESPONSIBLE);
        return MachineWorkerAssignmentResponseDto.fromEntity(
                assignmentRepository.save(assignment));
    }

    @Transactional
    public MachineWorkerAssignmentResponseDto addWorker(String machineId, Long workerId) {
        Machine machine = findMachineForUpdate(machineId);
        Worker worker = workerService.findActiveWorker(workerId);
        Optional<MachineWorkerAssignment> existing = assignmentRepository
                .findByMachine_MachineIdAndWorker_WorkerId(machineId, workerId);
        if (existing.isPresent()) {
            return MachineWorkerAssignmentResponseDto.fromEntity(existing.get());
        }
        MachineWorkerAssignment assignment = MachineWorkerAssignment.builder()
                .machine(machine)
                .worker(worker)
                .assignmentRole(MachineWorkerAssignment.AssignmentRole.WORKER)
                .build();
        return MachineWorkerAssignmentResponseDto.fromEntity(
                assignmentRepository.save(assignment));
    }

    @Transactional
    public void removeAssignment(String machineId, Long workerId) {
        findMachineForUpdate(machineId);
        if (assignmentRepository
                .findByMachine_MachineIdAndWorker_WorkerId(machineId, workerId)
                .isEmpty()) {
            throw new CustomException(ErrorCode.MACHINE_WORKER_ASSIGNMENT_NOT_FOUND);
        }
        assignmentRepository.deleteByMachine_MachineIdAndWorker_WorkerId(machineId, workerId);
    }

    public Optional<MachineWorkerAssignment> findResponsible(String machineId) {
        return assignmentRepository.findByMachine_MachineIdAndAssignmentRole(
                machineId,
                MachineWorkerAssignment.AssignmentRole.RESPONSIBLE);
    }

    private Machine findMachine(String machineId) {
        return machineRepository.findById(machineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
    }

    private Machine findMachineForUpdate(String machineId) {
        return machineRepository.findByIdForUpdate(machineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
    }
}
