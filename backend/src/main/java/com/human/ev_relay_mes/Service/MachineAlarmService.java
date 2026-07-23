package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineAlarmSearchRequestDto;
import com.human.ev_relay_mes.Dto.Response.MachineAlarmResponseDto;
import com.human.ev_relay_mes.Entity.AlarmCode;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineAlarmHistory;
import com.human.ev_relay_mes.Entity.MachineStatusHistory;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.WorkCommand;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.AlarmCodeRepository;
import com.human.ev_relay_mes.Repository.MachineAlarmHistoryRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import com.human.ev_relay_mes.Repository.WorkCommandRepository;
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

    private static final List<WorkCommand.Status> ALARM_CONTEXT_STATUSES = List.of(
            WorkCommand.Status.PENDING,
            WorkCommand.Status.DISPATCHED,
            WorkCommand.Status.ACCEPTED);

    private final MachineAlarmHistoryRepository machineAlarmHistoryRepository;
    private final MachineRepository machineRepository;
    private final AlarmCodeRepository alarmCodeRepository;
    private final MemberRepository memberRepository;
    private final MachineStatusHistoryRepository machineStatusHistoryRepository;
    private final WorkCommandRepository workCommandRepository;
    private final WorkCommandService workCommandService;
    private final ProductionScheduleRequestService productionScheduleRequestService;

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
        WorkCommand contextCommand = workCommandRepository
                .findFirstByMachine_MachineIdAndStatusInOrderByCreatedAtDescCommandIdDesc(
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

    // 로그인 사용자가 발생 중인 알람을 해제한다. 해제자는 내부 감사 이력으로만 저장한다.
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

        // 과거 COMM_TIMEOUT/COMM_DISCONNECTED가 해제되지 않은 채 남아 있어도
        // 실제 설비 ERROR 해제와 RESUME을 막지 않는다. 생산 정지를 유지해야 하는
        // 다른 설비 ERROR만 차단 조건으로 본다.
        boolean anotherBlockingErrorExists = machineAlarmHistoryRepository
                .findActiveByMachineForUpdate(machine.getMachineId()).stream()
                .filter(item -> !item.getMachineAlarmHistoryId()
                        .equals(clearedHistory.getMachineAlarmHistoryId()))
                .filter(item -> "ERROR".equalsIgnoreCase(item.getAlarmLevel()))
                .anyMatch(item -> !isCommunicationAlarm(item));
        if (anotherBlockingErrorExists) {
            return;
        }

        String lotNo = clearedHistory.getLot() == null
                ? null : clearedHistory.getLot().getLotNo();
        String processCode = clearedHistory.getProcess() == null
                ? null : clearedHistory.getProcess().getProcessCode();
        var resumeCommand = workCommandService.createResumeCommand(
                machine.getMachineId(), lotNo, processCode);
        if (resumeCommand.isPresent()) {
            return;
        }

        // HOLD 상태의 중단 작업이 실제로 남아 있는데 일시적인 명령 경쟁 때문에
        // RESUME 생성이 실패한 경우 설비를 IDLE로 잘못 풀지 않는다. 다음 해제/복구
        // 재시도에서 같은 중단 컨텍스트로 다시 RESUME을 만들 수 있게 유지한다.
        if (workCommandService.hasHeldInterruptedWork(
                machine.getMachineId(), lotNo, processCode)) {
            return;
        }

        if (!isCommunicationAlarm(clearedHistory)) {
            changeMachineStatus(machine, Machine.Status.IDLE, "알람 해제 후 대기 상태 복구");
            productionScheduleRequestService.requestMachine(machine.getMachineId());
        }
        // 통신 알람은 L1 재접속 직후 전송되는 MACHINE_STATUS 스냅샷으로 복구한다.
    }

    private boolean isCommunicationAlarm(MachineAlarmHistory history) {
        String alarmCode = history.getAlarmCode().getAlarmCode();
        return "COMM_DISCONNECTED".equals(alarmCode) || "COMM_TIMEOUT".equals(alarmCode);
    }

    private void changeMachineStatus(Machine machine, Machine.Status status, String message) {
        if (machine.getStatus() == status) {
            return;
        }
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEventId(String eventId) {
        return isBlank(eventId) ? null : eventId.trim();
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return value != null
                && (start == null || !value.isBefore(start))
                && (end == null || !value.isAfter(end));
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
                .lotNo(history.getLot() == null ? null : history.getLot().getLotNo())
                .processCode(history.getProcess() == null ? null : history.getProcess().getProcessCode())
                .processName(history.getProcess() == null ? null : history.getProcess().getProcessName())
                .occurredAt(history.getOccurredAt())
                .clearedAt(history.getClearedAt())
                .clearedById(clearer == null ? null : clearer.getMemberId())
                .clearedByName(clearer == null ? null : clearer.getMemberName())
                .message(history.getMessage())
                .cleared(history.getClearedAt() != null)
                .build();
    }
}
