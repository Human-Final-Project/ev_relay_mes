package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineAlarmSearchRequestDto;
import com.human.ev_relay_mes.Dto.Response.MachineAlarmResponseDto;
import com.human.ev_relay_mes.Entity.AlarmCode;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineAlarmHistory;
import com.human.ev_relay_mes.Entity.MachineStatusHistory;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.AlarmCodeRepository;
import com.human.ev_relay_mes.Repository.MachineAlarmHistoryRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
    private final MachineStatusHistoryRepository machineStatusHistoryRepository;
    private final WorkCommandService workCommandService;

    // L2 수집기가 전달한 설비 알람을 검증하고 발생 이력으로 저장할 때 사용한다.
    @Transactional
    public MachineAlarmResponseDto createAlarm(MachineAlarmReceiveRequestDto dto) {
        String eventId = normalizeEventId(dto.getEventId());
        if (eventId != null) {
            var existing = machineAlarmHistoryRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        AlarmCode alarmCode = alarmCodeRepository.findById(dto.getAlarmCode())
                .orElseThrow(() -> new CustomException(ErrorCode.ALARM_CODE_NOT_FOUND));
        validateAlarm(machine, alarmCode, dto.getAlarmLevel());
        String alarmLevel = dto.getAlarmLevel().toUpperCase();

        MachineAlarmHistory history = MachineAlarmHistory.builder()
                .eventId(eventId)
                .machine(machine)
                .alarmCode(alarmCode)
                .alarmLevel(alarmLevel)
                .occurredAt(dto.getOccurredAt())
                .message(dto.getMessage())
                .build();
        MachineAlarmHistory savedHistory = machineAlarmHistoryRepository.save(history);
        if ("ERROR".equals(alarmLevel)) {
            if (machine.getStatus() != Machine.Status.ERROR) {
                changeMachineStatus(machine, Machine.Status.ERROR, "ERROR 알람 발생: " + alarmCode.getAlarmCode());
            }
            workCommandService.pauseForMachineError(machine.getMachineId());
        }
        return toResponse(savedHistory);
    }

    // 알람 관리 화면에서 설비·알람 코드·등급·해제 여부·기간 조건으로 이력을 조회할 때 사용한다.
    public List<MachineAlarmResponseDto> search(MachineAlarmSearchRequestDto condition) {
        validateSearchPeriod(condition.getStartAt(), condition.getEndAt());
        return machineAlarmHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "occurredAt")).stream()
                .filter(item -> isBlank(condition.getMachineId()) || item.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(item -> isBlank(condition.getAlarmCode()) || item.getAlarmCode().getAlarmCode().equals(condition.getAlarmCode()))
                .filter(item -> isBlank(condition.getAlarmLevel()) || item.getAlarmLevel().equalsIgnoreCase(condition.getAlarmLevel()))
                .filter(item -> condition.getCleared() == null
                        || condition.getCleared().equals(item.getClearedAt() != null))
                .filter(item -> isWithin(item.getOccurredAt(), condition.getStartAt(), condition.getEndAt()))
                .map(this::toResponse)
                .toList();
    }

    // 작업자가 발생 중인 알람을 해제하고 해제 시각과 처리자를 기록할 때 사용한다.
    @Transactional
    public MachineAlarmResponseDto clearAlarm(Long historyId, Long memberId) {
        MachineAlarmHistory history = machineAlarmHistoryRepository.findByIdForUpdate(historyId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_ALARM_HISTORY_NOT_FOUND));
        if (history.getClearedAt() != null) {
            throw new CustomException(ErrorCode.ALARM_ALREADY_CLEARED);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        history.setClearedAt(LocalDateTime.now());
        history.setClearedBy(member);
        requestResumeIfNoErrorAlarm(history);
        return toResponse(history);
    }

    private void validateAlarm(Machine machine, AlarmCode alarmCode, String alarmLevel) {
        if (!alarmCode.getMachineType().equalsIgnoreCase("COMMON")
                && !alarmCode.getMachineType().equalsIgnoreCase(machine.getMachineType())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "설비 유형과 알람 코드의 설비 유형이 일치하지 않습니다.");
        }
        if (!List.of("INFO", "WARN", "ERROR").contains(alarmLevel.toUpperCase())) {
            throw new CustomException(ErrorCode.INVALID_ALARM_LEVEL);
        }
    }

    private void requestResumeIfNoErrorAlarm(MachineAlarmHistory clearedHistory) {
        if (!"ERROR".equalsIgnoreCase(clearedHistory.getAlarmLevel())) {
            return;
        }
        Machine machine = clearedHistory.getMachine();
        boolean anotherErrorExists = machineAlarmHistoryRepository
                .existsByMachine_MachineIdAndAlarmLevelIgnoreCaseAndClearedAtIsNullAndMachineAlarmHistoryIdNot(
                        machine.getMachineId(), "ERROR", clearedHistory.getMachineAlarmHistoryId());
        if (!anotherErrorExists && machine.getStatus() == Machine.Status.ERROR) {
            workCommandService.createResumeCommand(machine.getMachineId());
        }
    }

    private void changeMachineStatus(Machine machine, Machine.Status status, String message) {
        machine.setStatus(status);
        MachineStatusHistory statusHistory = MachineStatusHistory.builder()
                .machine(machine)
                .status(status)
                .process(machine.getProcess())
                .message(message)
                .build();
        machineStatusHistoryRepository.save(statusHistory);
    }

    private void validateSearchPeriod(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "조회 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    // 선택 검색 조건이 입력되지 않았는지 판단할 때 내부적으로 사용한다.
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEventId(String eventId) {
        return isBlank(eventId) ? null : eventId.trim();
    }

    // 알람 발생 시각이 사용자가 지정한 조회 기간에 포함되는지 판단할 때 사용한다.
    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    // 알람 이력 Entity를 알람 화면과 API에 전달할 응답 DTO로 변환할 때 사용한다.
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
