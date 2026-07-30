package com.human.ev_relay_mes.feature.machine.internal.service;

import com.human.ev_relay_mes.common.util.RequestValues;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmSearchRequestDto;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmResponseDto;
import com.human.ev_relay_mes.feature.masterdata.api.AlarmCode;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmHistory;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmOperations;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.auth.api.MemberLookup;
import com.human.ev_relay_mes.feature.collector.api.WorkCommand;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandOperations;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineAlarmHistoryRepository;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineAlarmService implements MachineAlarmOperations {

    private static final List<WorkCommand.Status> ALARM_CONTEXT_STATUSES = List.of(
            WorkCommand.Status.PENDING,
            WorkCommand.Status.DISPATCHED,
            WorkCommand.Status.ACCEPTED);

    private final MachineAlarmHistoryRepository machineAlarmHistoryRepository;
    private final MachineRepository machineRepository;
    private final MasterDataLookup masterDataLookup;
    private final MemberLookup memberLookup;
    private final WorkCommandOperations workCommandService;
    private final MachineAlarmRecoveryCoordinator recoveryCoordinator;
    private final MachineAlarmResponseAssembler responseAssembler;

    // L2 수집기가 전달한 설비 알람을 검증하고 발생 이력으로 저장할 때 사용한다.
    @Transactional
    @Override
    public MachineAlarmResponseDto createAlarm(MachineAlarmReceiveRequestDto dto) {
        String eventId = RequestValues.trimToNull(dto.getEventId());
        if (eventId != null) {
            var existing = machineAlarmHistoryRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return responseAssembler.toResponse(existing.get());
            }
        }
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        AlarmCode alarmCode = masterDataLookup.getRequiredAlarmCode(dto.getAlarmCode());
        validateAlarm(machine, alarmCode, dto.getAlarmLevel());
        String alarmLevel = dto.getAlarmLevel().toUpperCase();
        WorkCommand contextCommand = workCommandService
                .findLatestCommandForMachine(
                        machine.getMachineId(), ALARM_CONTEXT_STATUSES)
                .orElse(null);

        MachineAlarmHistory history = MachineAlarmHistory.builder()
                .eventId(eventId)
                .machine(machine)
                .alarmCode(alarmCode)
                .alarmLevel(alarmLevel)
                .lot(contextCommand == null ? null : contextCommand.getLot())
                .process(contextCommand == null ? machine.getProcess() : contextCommand.getProcess())
                .occurredAt(dto.getOccurredAt() == null ? LocalDateTime.now() : dto.getOccurredAt())
                .message(dto.getMessage())
                .build();
        MachineAlarmHistory savedHistory = machineAlarmHistoryRepository.save(history);
        if ("ERROR".equals(alarmLevel)) {
            // 상태 이력은 뒤이어 오는 MACHINE_STATUS ERROR가 한 번만 저장한다.
            machine.setStatus(Machine.Status.ERROR);
            workCommandService.pauseForMachineError(machine.getMachineId());
        }
        return responseAssembler.toResponse(savedHistory);
    }

    // 알람 관리 화면에서 설비·알람 코드·등급·해제 여부·기간 조건으로 이력을 조회할 때 사용한다.
    @Override
    public List<MachineAlarmResponseDto> search(MachineAlarmSearchRequestDto condition) {
        RequestValues.validateSearchPeriod(condition.getStartAt(), condition.getEndAt());
        return machineAlarmHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "occurredAt")).stream()
                .filter(item -> RequestValues.isBlank(condition.getMachineId())
                        || item.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(item -> RequestValues.isBlank(condition.getProcessCode())
                        || (item.getProcess() != null
                        && item.getProcess().getProcessCode().equals(condition.getProcessCode())))
                .filter(item -> RequestValues.isBlank(condition.getAlarmCode())
                        || item.getAlarmCode().getAlarmCode().equals(condition.getAlarmCode()))
                .filter(item -> RequestValues.isBlank(condition.getAlarmLevel())
                        || item.getAlarmLevel().equalsIgnoreCase(condition.getAlarmLevel()))
                .filter(item -> condition.getCleared() == null
                        || condition.getCleared().equals(item.getClearedAt() != null))
                .filter(item -> isWithin(item.getOccurredAt(), condition.getStartAt(), condition.getEndAt()))
                .map(responseAssembler::toResponse)
                .toList();
    }

    // 로그인 사용자가 발생 중인 알람을 해제한다. 해제자는 내부 감사 이력으로만 저장한다.
    @Transactional
    @Override
    public MachineAlarmResponseDto clearAlarm(Long historyId, Long memberId) {
        MachineAlarmHistory history = machineAlarmHistoryRepository.findByIdForUpdate(historyId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_ALARM_HISTORY_NOT_FOUND));
        if (history.getClearedAt() != null) {
            throw new CustomException(ErrorCode.ALARM_ALREADY_CLEARED);
        }
        Member member = memberLookup.getRequiredById(memberId);
        history.setClearedAt(LocalDateTime.now());
        history.setClearedBy(member);
        recoveryCoordinator.recoverAfterClear(history);
        return responseAssembler.toResponse(history);
    }

    @Override
    public long countActiveAlarms() {
        return machineAlarmHistoryRepository
                .findByClearedAtIsNullOrderByOccurredAtDesc()
                .size();
    }

    @Override
    public long countAlarmsOccurredBetween(LocalDateTime startAt, LocalDateTime endAt) {
        return machineAlarmHistoryRepository
                .findByOccurredAtBetweenOrderByOccurredAtDesc(startAt, endAt)
                .size();
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

    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return value != null
                && (start == null || !value.isBefore(start))
                && (end == null || !value.isAfter(end));
    }

}
