package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.WorkCommandAckRequestDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.ProductionLog;
import com.human.ev_relay_mes.Entity.WorkCommand;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.InspectionUnitResultRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.ProductionLogRepository;
import com.human.ev_relay_mes.Repository.WorkCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class WorkCommandServiceTest {

    @Mock
    private WorkCommandRepository workCommandRepository;
    @Mock
    private MachineRepository machineRepository;
    @Mock
    private ProcessRepository processRepository;
    @Mock
    private ProductionLogRepository productionLogRepository;
    @Mock
    private InspectionUnitResultRepository inspectionUnitResultRepository;
    @Mock
    private LotProcessResponsibleService lotProcessResponsibleService;
    @Mock
    private InspectionStandardService inspectionStandardService;

    @InjectMocks
    private WorkCommandService workCommandService;

    @Test
    void createsStartCommandsForBothParallelProcesses() {
        Process op20 = process("OP20", 1);
        Process op30 = process("OP30", 2);
        Machine wind = machine("EQ-WIND-01", op20);
        Machine weld = machine("EQ-WELD-01", op30);
        Lot lot = Lot.builder().lotNo("LOT-001").currentProcess(op20).inputQty(10).build();

        when(processRepository.findById("OP20")).thenReturn(Optional.of(op20));
        when(processRepository.findById("OP30")).thenReturn(Optional.of(op30));
        when(machineRepository.findUsableByProcessForUpdate("OP20")).thenReturn(List.of(wind));
        when(machineRepository.findUsableByProcessForUpdate("OP30")).thenReturn(List.of(weld));
        when(workCommandRepository.save(any(WorkCommand.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var commands = workCommandService.createInitialStartCommands(lot);

        assertThat(commands).hasSize(2);
        assertThat(commands).extracting(command -> command.getProcessCode())
                .containsExactly("OP20", "OP30");
        assertThat(commands).allMatch(command -> command.getStatus().equals("PENDING"));
    }

    @Test
    void claimsOnlyFirstCommandWhenSameIdleMachineHasQueue() {
        Process process = process("OP20", 1);
        Machine machine = machine("EQ-WIND-01", process);
        Lot firstLot = Lot.builder().lotNo("LOT-001").build();
        Lot secondLot = Lot.builder().lotNo("LOT-002").build();
        WorkCommand first = command(1L, firstLot, machine, process);
        WorkCommand second = command(2L, secondLot, machine, process);

        when(workCommandRepository.findByStatusForUpdate(WorkCommand.Status.PENDING))
                .thenReturn(List.of(first, second));
        when(workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq("EQ-WIND-01"), anyCollection()))
                .thenReturn(false, true);

        var claimed = workCommandService.claimPendingCommands();

        assertThat(claimed).hasSize(1);
        assertThat(first.getStatus()).isEqualTo(WorkCommand.Status.DISPATCHED);
        assertThat(second.getStatus()).isEqualTo(WorkCommand.Status.PENDING);
    }

    @Test
    void claimsPendingCommandsOnlyForRequestedMachine() {
        Process process = process("OP70", 70);
        Machine machine = machine("EQ-TEST-01", process);
        WorkCommand command = command(
                70L,
                Lot.builder().lotNo("LOT-070").build(),
                machine,
                process);

        when(workCommandRepository.findByMachineAndStatusForDispatch(
                "EQ-TEST-01", WorkCommand.Status.PENDING))
                .thenReturn(List.of(command));
        when(workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                eq("EQ-TEST-01"), anyCollection())).thenReturn(false);

        var claimed = workCommandService.claimPendingCommands(" EQ-TEST-01 ");

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getMachineId()).isEqualTo("EQ-TEST-01");
        assertThat(command.getStatus()).isEqualTo(WorkCommand.Status.DISPATCHED);
        org.mockito.Mockito.verify(workCommandRepository)
                .findByMachineAndStatusForDispatch(
                        "EQ-TEST-01", WorkCommand.Status.PENDING);
    }

    @Test
    void releasesUnsentDispatchedCommandBackToPending() {
        Process process = process("OP70", 70);
        Machine machine = machine("EQ-TEST-01", process);
        WorkCommand command = command(
                71L,
                Lot.builder().lotNo("LOT-071").build(),
                machine,
                process);
        command.setStatus(WorkCommand.Status.DISPATCHED);
        command.setDispatchedAt(java.time.LocalDateTime.now());
        when(workCommandRepository.findByIdForUpdate(71L))
                .thenReturn(Optional.of(command));

        var released = workCommandService.releaseDispatchedCommand(
                71L, "EQ-TEST-01");

        assertThat(released.getStatus()).isEqualTo("PENDING");
        assertThat(command.getStatus()).isEqualTo(WorkCommand.Status.PENDING);
        assertThat(command.getDispatchedAt()).isNull();
    }

    @Test
    void requeuesStaleDispatchedCommandBeforeMachinePolling() {
        Process process = process("OP70", 70);
        Machine machine = machine("EQ-TEST-01", process);
        WorkCommand stale = command(
                72L,
                Lot.builder().lotNo("LOT-072").build(),
                machine,
                process);
        stale.setStatus(WorkCommand.Status.DISPATCHED);
        stale.setDispatchedAt(java.time.LocalDateTime.now().minusSeconds(30));

        when(workCommandRepository.findStaleDispatchedByMachineForUpdate(
                eq("EQ-TEST-01"),
                eq(WorkCommand.Status.DISPATCHED),
                any(java.time.LocalDateTime.class)))
                .thenReturn(List.of(stale));
        when(workCommandRepository.findByMachineAndStatusForDispatch(
                "EQ-TEST-01", WorkCommand.Status.PENDING))
                .thenReturn(List.of(stale));
        when(workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                eq("EQ-TEST-01"), anyCollection())).thenReturn(false);

        var claimed = workCommandService.claimPendingCommands("EQ-TEST-01");

        assertThat(claimed).hasSize(1);
        assertThat(stale.getStatus()).isEqualTo(WorkCommand.Status.DISPATCHED);
        assertThat(stale.getDispatchedAt()).isAfter(
                java.time.LocalDateTime.now().minusSeconds(5));
    }

    @Test
    void acknowledgesCommandWhenMachineMatches() {
        Process process = process("OP20", 1);
        Machine machine = machine("EQ-WIND-01", process);
        WorkCommand command = command(101L, Lot.builder().lotNo("LOT-001").build(), machine, process);
        command.setStatus(WorkCommand.Status.DISPATCHED);
        WorkCommandAckRequestDto dto = ack(101L, "EQ-WIND-01", "ACCEPTED");

        when(workCommandRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(command));

        var result = workCommandService.acknowledge(dto);

        assertThat(result.getCommandId()).isEqualTo(101L);
        assertThat(result.getMachineId()).isEqualTo("EQ-WIND-01");
        assertThat(result.getStatus()).isEqualTo("ACCEPTED");
    }


    @Test
    void acceptsDuplicateAcceptedAckAfterCommandCompleted() {
        Process process = process("OP20", 1);
        Machine machine = machine("EQ-WIND-01", process);
        WorkCommand command = command(101L, Lot.builder().lotNo("LOT-001").build(), machine, process);
        command.setStatus(WorkCommand.Status.COMPLETED);
        command.setAcknowledgedAt(java.time.LocalDateTime.now().minusSeconds(1));
        WorkCommandAckRequestDto dto = ack(101L, "EQ-WIND-01", "ACCEPTED");

        when(workCommandRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(command));

        var result = workCommandService.acknowledge(dto);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void rejectsAckWhenMachineDoesNotMatchCommand() {
        Process process = process("OP20", 1);
        Machine machine = machine("EQ-WIND-01", process);
        WorkCommand command = command(101L, Lot.builder().lotNo("LOT-001").build(), machine, process);
        command.setStatus(WorkCommand.Status.DISPATCHED);
        WorkCommandAckRequestDto dto = ack(101L, "EQ-WELD-01", "ACCEPTED");

        when(workCommandRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(command));

        assertThatThrownBy(() -> workCommandService.acknowledge(dto))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.WORK_COMMAND_MACHINE_MISMATCH);
    }

    @Test
    void cancelsRunningCommandAndHoldsLotWhenMachineErrors() {
        Process process = process("OP20", 1);
        Machine machine = machine("EQ-WIND-01", process);
        Lot lot = Lot.builder().lotNo("LOT-001").status(Lot.Status.RUNNING).build();
        WorkCommand command = command(101L, lot, machine, process);
        command.setStatus(WorkCommand.Status.ACCEPTED);
        when(workCommandRepository.findByMachineAndStatusInForUpdate(
                eq("EQ-WIND-01"), anyCollection())).thenReturn(List.of(command));

        workCommandService.pauseForMachineError("EQ-WIND-01");

        assertThat(command.getStatus()).isEqualTo(WorkCommand.Status.CANCELED);
        assertThat(command.getCompletedAt()).isNotNull();
        assertThat(lot.getStatus()).isEqualTo(Lot.Status.HOLD);
    }

    @Test
    void createsResumeCommandForRemainingQuantity() {
        Process process = process("OP20", 1);
        Machine machine = machine("EQ-WIND-01", process);
        machine.setStatus(Machine.Status.ERROR);
        Lot lot = Lot.builder().lotNo("LOT-001").status(Lot.Status.HOLD).build();
        WorkCommand interrupted = command(101L, lot, machine, process);
        interrupted.setStatus(WorkCommand.Status.CANCELED);
        ProductionLog partial = ProductionLog.builder().inputQty(4).build();

        when(workCommandRepository
                .findFirstByMachine_MachineIdAndStatusOrderByCreatedAtDescCommandIdDesc(
                        "EQ-WIND-01", WorkCommand.Status.CANCELED))
                .thenReturn(Optional.of(interrupted));
        when(workCommandRepository.findByLot_LotNoOrderByCreatedAtAsc("LOT-001"))
                .thenReturn(List.of(interrupted));
        when(productionLogRepository.findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                "LOT-001", "OP20")).thenReturn(List.of(partial));
        when(workCommandRepository.save(any(WorkCommand.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = workCommandService.createResumeCommand("EQ-WIND-01");

        ArgumentCaptor<WorkCommand> captor = ArgumentCaptor.forClass(WorkCommand.class);
        org.mockito.Mockito.verify(workCommandRepository).save(captor.capture());
        assertThat(result).isPresent();
        assertThat(captor.getValue().getCommandType()).isEqualTo(WorkCommand.CommandType.RESUME);
        assertThat(captor.getValue().getInputQty()).isEqualTo(6);
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkCommand.Status.PENDING);
    }


    @Test
    void createsOp70ResumeCommandFromCompletedInspectionUnits() {
        Process process = process("OP70", 70);
        Machine machine = machine("EQ-TEST-01", process);
        machine.setStatus(Machine.Status.ERROR);
        Lot lot = Lot.builder().lotNo("LOT-070").status(Lot.Status.HOLD).build();
        WorkCommand interrupted = command(701L, lot, machine, process);
        interrupted.setStatus(WorkCommand.Status.CANCELED);

        when(workCommandRepository
                .findFirstByMachine_MachineIdAndStatusOrderByCreatedAtDescCommandIdDesc(
                        "EQ-TEST-01", WorkCommand.Status.CANCELED))
                .thenReturn(Optional.of(interrupted));
        when(workCommandRepository.findByLot_LotNoOrderByCreatedAtAsc("LOT-070"))
                .thenReturn(List.of(interrupted));
        when(inspectionUnitResultRepository
                .countByLot_LotNoAndProcess_ProcessCode("LOT-070", "OP70"))
                .thenReturn(3L);
        when(workCommandRepository.save(any(WorkCommand.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = workCommandService.createResumeCommand("EQ-TEST-01");

        ArgumentCaptor<WorkCommand> captor = ArgumentCaptor.forClass(WorkCommand.class);
        org.mockito.Mockito.verify(workCommandRepository).save(captor.capture());
        assertThat(result).isPresent();
        assertThat(captor.getValue().getCommandType()).isEqualTo(WorkCommand.CommandType.RESUME);
        assertThat(captor.getValue().getInputQty()).isEqualTo(7);
    }

    @Test
    void dispatchesResumeCommandWhileMachineIsInError() {
        Process process = process("OP20", 1);
        Machine machine = machine("EQ-WIND-01", process);
        machine.setStatus(Machine.Status.ERROR);
        WorkCommand resume = command(201L, Lot.builder().lotNo("LOT-001").build(), machine, process);
        resume.setCommandType(WorkCommand.CommandType.RESUME);
        when(workCommandRepository.findByStatusForUpdate(WorkCommand.Status.PENDING))
                .thenReturn(List.of(resume));

        var claimed = workCommandService.claimPendingCommands();

        assertThat(claimed).hasSize(1);
        assertThat(resume.getStatus()).isEqualTo(WorkCommand.Status.DISPATCHED);
    }

    private Process process(String code, int order) {
        return Process.builder()
                .processCode(code).processName(code).processOrder(order).build();
    }

    private Machine machine(String id, Process process) {
        return Machine.builder()
                .machineId(id).machineName(id).machineType("TEST")
                .process(process).status(Machine.Status.IDLE).build();
    }

    private WorkCommand command(Long id, Lot lot, Machine machine, Process process) {
        return WorkCommand.builder()
                .commandId(id).commandType(WorkCommand.CommandType.START)
                .machine(machine).process(process).lot(lot).inputQty(10).build();
    }

    private WorkCommandAckRequestDto ack(Long commandId, String machineId, String status) {
        WorkCommandAckRequestDto dto = new WorkCommandAckRequestDto();
        dto.setCommandId(commandId);
        dto.setMachineId(machineId);
        dto.setAckStatus(status);
        return dto;
    }
}
