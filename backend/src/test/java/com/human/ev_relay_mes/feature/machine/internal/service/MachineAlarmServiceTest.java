package com.human.ev_relay_mes.feature.machine.internal.service;

import com.human.ev_relay_mes.feature.production.api.ProductionSchedulingRequests;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandOperations;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandResponseDto;
import com.human.ev_relay_mes.feature.masterdata.api.AlarmCode;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmHistory;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.collector.api.WorkCommand;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineAlarmHistoryRepository;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineRepository;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.feature.auth.api.MemberLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MachineAlarmServiceTest {

    @Mock MachineAlarmHistoryRepository machineAlarmHistoryRepository;
    @Mock MachineRepository machineRepository;
    @Mock MasterDataLookup masterDataLookup;
    @Mock MemberLookup memberLookup;
    @Mock MachineStatusHistoryRepository machineStatusHistoryRepository;
    @Mock WorkCommandOperations workCommandService;
    @Mock ProductionSchedulingRequests productionSchedulingRequests;

    MachineAlarmService machineAlarmService;

    @BeforeEach
    void setUp() {
        MachineAlarmRecoveryCoordinator recoveryCoordinator =
                new MachineAlarmRecoveryCoordinator(
                        machineAlarmHistoryRepository,
                        machineStatusHistoryRepository,
                        workCommandService,
                        productionSchedulingRequests);
        machineAlarmService = new MachineAlarmService(
                machineAlarmHistoryRepository,
                machineRepository,
                masterDataLookup,
                memberLookup,
                workCommandService,
                recoveryCoordinator,
                new MachineAlarmResponseAssembler());
    }

    @Test
    void returnsExistingAlarmForDuplicateEventIdWithoutPausingAgain() {
        Process process = process();
        Machine machine = machine(process, Machine.Status.ERROR);
        MachineAlarmHistory existing = MachineAlarmHistory.builder()
                .machineAlarmHistoryId(12L)
                .eventId("alarm-001")
                .machine(machine)
                .alarmCode(alarmCode())
                .alarmLevel("ERROR")
                .occurredAt(LocalDateTime.now())
                .build();
        MachineAlarmReceiveRequestDto dto = new MachineAlarmReceiveRequestDto();
        dto.setEventId("alarm-001");
        dto.setMachineId("EQ-WIND-01");
        dto.setAlarmCode("MOTOR_OVERLOAD");
        dto.setAlarmLevel("ERROR");
        when(machineAlarmHistoryRepository.findByEventId("alarm-001"))
                .thenReturn(Optional.of(existing));

        var response = machineAlarmService.createAlarm(dto);

        assertThat(response.getMachineAlarmHistoryId()).isEqualTo(12L);
        verifyNoInteractions(machineRepository, masterDataLookup,
                machineStatusHistoryRepository, workCommandService);
    }

    @Test
    void errorAlarmStopsMachineAndPausesItsWork() {
        Process process = process();
        Machine machine = machine(process, Machine.Status.RUNNING);
        AlarmCode alarmCode = alarmCode();
        MachineAlarmReceiveRequestDto dto = new MachineAlarmReceiveRequestDto();
        dto.setMachineId("EQ-WIND-01");
        dto.setAlarmCode("MOTOR_OVERLOAD");
        dto.setAlarmLevel("ERROR");
        dto.setOccurredAt(LocalDateTime.now());

        when(machineRepository.findById("EQ-WIND-01")).thenReturn(Optional.of(machine));
        when(masterDataLookup.getRequiredAlarmCode("MOTOR_OVERLOAD")).thenReturn(alarmCode);
        when(machineAlarmHistoryRepository.save(any(MachineAlarmHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        machineAlarmService.createAlarm(dto);

        assertThat(machine.getStatus()).isEqualTo(Machine.Status.ERROR);
        verify(workCommandService).pauseForMachineError("EQ-WIND-01");
    }


    @Test
    void warningAlarmKeepsProductionRunningAndCapturesLotContext() {
        Process process = process();
        Machine machine = machine(process, Machine.Status.RUNNING);
        Lot lot = Lot.builder().lotNo("LOT-001").build();
        WorkCommand command = WorkCommand.builder()
                .machine(machine)
                .process(process)
                .lot(lot)
                .inputQty(10)
                .status(WorkCommand.Status.ACCEPTED)
                .commandType(WorkCommand.CommandType.START)
                .build();
        AlarmCode warningCode = AlarmCode.builder()
                .alarmCode("WIRE_TENSION_WARN")
                .alarmName("wire tension warning")
                .machineType("EQ-WIND")
                .build();
        MachineAlarmReceiveRequestDto dto = new MachineAlarmReceiveRequestDto();
        dto.setMachineId("EQ-WIND-01");
        dto.setAlarmCode("WIRE_TENSION_WARN");
        dto.setAlarmLevel("WARN");

        when(machineRepository.findById("EQ-WIND-01")).thenReturn(Optional.of(machine));
        when(masterDataLookup.getRequiredAlarmCode("WIRE_TENSION_WARN")).thenReturn(warningCode);
        when(workCommandService
                .findLatestCommandForMachine(
                        org.mockito.ArgumentMatchers.eq("EQ-WIND-01"), any()))
                .thenReturn(Optional.of(command));
        when(machineAlarmHistoryRepository.save(any(MachineAlarmHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = machineAlarmService.createAlarm(dto);

        assertThat(machine.getStatus()).isEqualTo(Machine.Status.RUNNING);
        assertThat(response.getLotNo()).isEqualTo("LOT-001");
        assertThat(response.getProcessCode()).isEqualTo("OP20");
        verify(workCommandService, never()).pauseForMachineError(any());
    }

    @Test
    void clearingLastErrorRequestsResumeButKeepsMachineInErrorUntilL1Reply() {
        Process process = process();
        Machine machine = machine(process, Machine.Status.ERROR);
        MachineAlarmHistory history = MachineAlarmHistory.builder()
                .machineAlarmHistoryId(1L)
                .machine(machine)
                .alarmCode(alarmCode())
                .alarmLevel("ERROR")
                .occurredAt(LocalDateTime.now())
                .build();
        Member member = Member.builder().memberId(10L).memberName("operator").build();

        when(machineAlarmHistoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(history));
        when(memberLookup.getRequiredById(10L)).thenReturn(member);
        when(machineAlarmHistoryRepository.findActiveByMachineForUpdate("EQ-WIND-01"))
                .thenReturn(List.of());
        when(workCommandService.createResumeCommand("EQ-WIND-01", null, null))
                .thenReturn(Optional.of(mock(WorkCommandResponseDto.class)));

        machineAlarmService.clearAlarm(1L, 10L);

        assertThat(history.getClearedAt()).isNotNull();
        assertThat(history.getClearedBy()).isEqualTo(member);
        assertThat(machine.getStatus()).isEqualTo(Machine.Status.ERROR);
        verify(workCommandService).createResumeCommand("EQ-WIND-01", null, null);
        verifyNoInteractions(machineStatusHistoryRepository);
    }


    @Test
    void clearingEquipmentErrorWithoutInterruptedWorkReturnsMachineToIdle() {
        Process process = process();
        Machine machine = machine(process, Machine.Status.ERROR);
        MachineAlarmHistory history = MachineAlarmHistory.builder()
                .machineAlarmHistoryId(2L)
                .machine(machine)
                .alarmCode(alarmCode())
                .alarmLevel("ERROR")
                .occurredAt(LocalDateTime.now())
                .build();
        Member member = Member.builder().memberId(10L).memberName("operator").build();

        when(machineAlarmHistoryRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(history));
        when(memberLookup.getRequiredById(10L)).thenReturn(member);
        when(machineAlarmHistoryRepository.findActiveByMachineForUpdate("EQ-WIND-01"))
                .thenReturn(List.of());
        when(workCommandService.createResumeCommand("EQ-WIND-01", null, null)).thenReturn(Optional.empty());
        when(machineStatusHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        machineAlarmService.clearAlarm(2L, 10L);

        assertThat(machine.getStatus()).isEqualTo(Machine.Status.IDLE);
        verify(machineStatusHistoryRepository).save(any());
        verify(productionSchedulingRequests).requestMachine("EQ-WIND-01");
    }

    @Test
    void clearingCommunicationAlarmWaitsForReconnectStatusSnapshot() {
        Process process = process();
        Machine machine = machine(process, Machine.Status.ERROR);
        AlarmCode communicationCode = AlarmCode.builder()
                .alarmCode("COMM_DISCONNECTED")
                .alarmName("disconnected")
                .machineType("COMMON")
                .build();
        MachineAlarmHistory history = MachineAlarmHistory.builder()
                .machineAlarmHistoryId(3L)
                .machine(machine)
                .alarmCode(communicationCode)
                .alarmLevel("ERROR")
                .occurredAt(LocalDateTime.now())
                .build();
        Member member = Member.builder().memberId(10L).memberName("operator").build();

        when(machineAlarmHistoryRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(history));
        when(memberLookup.getRequiredById(10L)).thenReturn(member);
        when(machineAlarmHistoryRepository.findActiveByMachineForUpdate("EQ-WIND-01"))
                .thenReturn(List.of());
        when(workCommandService.createResumeCommand("EQ-WIND-01", null, null)).thenReturn(Optional.empty());

        machineAlarmService.clearAlarm(3L, 10L);

        assertThat(machine.getStatus()).isEqualTo(Machine.Status.ERROR);
        verifyNoInteractions(machineStatusHistoryRepository);
    }

    private Process process() {
        return Process.builder().processCode("OP20").processName("winding").processOrder(20).build();
    }

    private Machine machine(Process process, Machine.Status status) {
        return Machine.builder()
                .machineId("EQ-WIND-01")
                .machineName("winder")
                .machineType("EQ-WIND")
                .process(process)
                .status(status)
                .build();
    }

    private AlarmCode alarmCode() {
        return AlarmCode.builder()
                .alarmCode("MOTOR_OVERLOAD")
                .alarmName("motor overload")
                .machineType("EQ-WIND")
                .build();
    }
}
