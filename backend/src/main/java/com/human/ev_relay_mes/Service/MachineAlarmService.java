package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineAlarmSearchRequestDto;
import com.human.ev_relay_mes.Dto.Response.MachineAlarmResponseDto;
import com.human.ev_relay_mes.Entity.AlarmCode;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineAlarmHistory;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.AlarmCodeRepository;
import com.human.ev_relay_mes.Repository.MachineAlarmHistoryRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineAlarmService {

    private final MachineAlarmHistoryRepository machineAlarmHistoryRepository;
    private final MachineRepository machineRepository;
    private final AlarmCodeRepository alarmCodeRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public MachineAlarmResponseDto createAlarm(MachineAlarmReceiveRequestDto dto) {
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        AlarmCode alarmCode = alarmCodeRepository.findById(dto.getAlarmCode())
                .orElseThrow(() -> new CustomException(ErrorCode.ALARM_CODE_NOT_FOUND));
        if (!"Y".equalsIgnoreCase(alarmCode.getUseYn())) {
            throw new CustomException(ErrorCode.ALARM_CODE_NOT_USABLE);
        }

        MachineAlarmHistory history = MachineAlarmHistory.builder()
                .machine(machine)
                .alarmCode(alarmCode)
                .alarmLevel(dto.getAlarmLevel())
                .occurredAt(dto.getOccurredAt())
                .message(dto.getMessage())
                .build();
        return toResponse(machineAlarmHistoryRepository.save(history));
    }

    public List<MachineAlarmResponseDto> search(MachineAlarmSearchRequestDto condition) {
        return machineAlarmHistoryRepository.findAll().stream()
                .filter(item -> isBlank(condition.getMachineId()) || item.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(item -> isBlank(condition.getAlarmCode()) || item.getAlarmCode().getAlarmCode().equals(condition.getAlarmCode()))
                .filter(item -> isBlank(condition.getAlarmLevel()) || item.getAlarmLevel().equalsIgnoreCase(condition.getAlarmLevel()))
                .filter(item -> condition.getCleared() == null
                        || condition.getCleared().equals(item.getClearedAt() != null))
                .filter(item -> isWithin(item.getOccurredAt(), condition.getStartAt(), condition.getEndAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MachineAlarmResponseDto clearAlarm(Long historyId, Long memberId) {
        MachineAlarmHistory history = machineAlarmHistoryRepository.findById(historyId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_ALARM_HISTORY_NOT_FOUND));
        if (history.getClearedAt() != null) {
            throw new CustomException(ErrorCode.ALARM_ALREADY_CLEARED);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        history.setClearedAt(LocalDateTime.now());
        history.setClearedBy(member);
        return toResponse(history);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    private MachineAlarmResponseDto toResponse(MachineAlarmHistory history) {
        Member clearer = history.getClearedBy();
        return MachineAlarmResponseDto.builder()
                .machineAlarmHistoryId(history.getMachineAlarmHistoryId())
                .machineId(history.getMachine().getMachineId())
                .machineName(history.getMachine().getMachineName())
                .alarmCode(history.getAlarmCode().getAlarmCode())
                .alarmName(history.getAlarmCode().getAlarmName())
                .alarmLevel(history.getAlarmLevel())
                .occurredAt(history.getOccurredAt())
                .clearedAt(history.getClearedAt())
                .clearedById(clearer == null ? null : clearer.getMemberId())
                .clearedByName(clearer == null ? null : clearer.getMemberName())
                .message(history.getMessage())
                .cleared(history.getClearedAt() != null)
                .build();
    }
}
