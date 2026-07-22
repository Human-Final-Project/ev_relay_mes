package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.Entity.AlarmCode;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineAlarmHistory;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Repository.AlarmCodeRepository;
import com.human.ev_relay_mes.Repository.MachineAlarmHistoryRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineAlarmServiceTest {

    @Mock MachineAlarmHistoryRepository machineAlarmHistoryRepository;
    @Mock MachineRepository machineRepository;
    @Mock AlarmCodeRepository alarmCodeRepository;
    @Mock MemberRepository memberRepository;
    @Mock MachineStatusHistoryRepository machineStatusHistoryRepository;
    @Mock WorkCommandService workCommandService;

    @InjectMocks MachineAlarmService machineAlarmService;

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
        verifyNoInteractions(machineRepository, alarmCodeRepository,
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
        when(alarmCodeRepository.findById("MOTOR_OVERLOAD")).thenReturn(Optional.of(alarmCode));
        when(machineAlarmHistoryRepository.save(any(MachineAlarmHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(machineStatusHistoryRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        machineAlarmService.createAlarm(dto);

        assertThat(machine.getStatus()).isEqualTo(Machine.Status.ERROR);
        verify(workCommandService).pauseForMachineError("EQ-WIND-01");
    }

    @Test
    void communicationAlarmMarksMachineErrorWithoutCancelingWork() {
        Process process = process();
        Machine machine = machine(process, Machine.Status.RUNNING);
        AlarmCode communicationCode = AlarmCode.builder()
                .alarmCode("COMM_DISCONNECTED")
                .alarmName("communication disconnected")
                .machineType("COMMON")
                .build();
        MachineAlarmReceiveRequestDto dto = new MachineAlarmReceiveRequestDto();
        dto.setMachineId("EQ-WIND-01");
        dto.setAlarmCode("COMM_DISCONNECTED");
        dto.setAlarmLevel("ERROR");
        dto.setOccurredAt(LocalDateTime.now());

        when(machineRepository.findById("EQ-WIND-01")).thenReturn(Optional.of(machine));
        when(alarmCodeRepository.findById("COMM_DISCONNECTED"))
                .thenReturn(Optional.of(communicationCode));
        when(machineAlarmHistoryRepository.save(any(MachineAlarmHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(machineStatusHistoryRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        machineAlarmService.createAlarm(dto);

        assertThat(machine.getStatus()).isEqualTo(Machine.Status.ERROR);
        verify(workCommandService, never()).pauseForMachineError("EQ-WIND-01");
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
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));
        when(machineAlarmHistoryRepository
                .existsByMachine_MachineIdAndAlarmLevelIgnoreCaseAndClearedAtIsNullAndMachineAlarmHistoryIdNot(
                        "EQ-WIND-01", "ERROR", 1L)).thenReturn(false);

        machineAlarmService.clearAlarm(1L, 10L);

        assertThat(history.getClearedAt()).isNotNull();
        assertThat(history.getClearedBy()).isEqualTo(member);
        assertThat(machine.getStatus()).isEqualTo(Machine.Status.ERROR);
        verify(workCommandService).createResumeCommand("EQ-WIND-01");
        verifyNoInteractions(machineStatusHistoryRepository);
    }

    private Process process() {
        return Process.builder().processCode("OP20").processName("winding").processOrder(20).build();
    }

    private Machine machine(Process process, Machine.Status status) {
        return Machine.builder()
                .machineId("EQ-WIND-01")
                .machineName("winder")
                .machineType("WINDER")
                .process(process)
                .status(status)
                .build();
    }

    private AlarmCode alarmCode() {
        return AlarmCode.builder()
                .alarmCode("MOTOR_OVERLOAD")
                .alarmName("motor overload")
                .machineType("WINDER")
                .build();
    }
}
