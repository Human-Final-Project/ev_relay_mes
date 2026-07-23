package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.ProductionLog;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.ProductionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 여러 WorkOrder/LOT을 공정별로 동시에 흘려보내는 파이프라인 스케줄러다.
 * LOT 전체를 직렬화하지 않고, 각 설비의 IDLE 상태와 PENDING/활성 명령을
 * 설비 예약 조건으로 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionSchedulerService {

    private static final String OP20 = "OP20";
    private static final String OP30 = "OP30";
    private static final String ASSEMBLY = "OP40_OP50";

    private final LotRepository lotRepository;
    private final MachineRepository machineRepository;
    private final ProcessRepository processRepository;
    private final ProductionLogRepository productionLogRepository;
    private final WorkCommandService workCommandService;

    /** 현재 LOT이 대기 중인 공정을 즉시 예약할 수 있으면 START를 생성한다. */
    @Transactional
    public boolean tryScheduleLot(String lotNo) {
        Lot lot = lotRepository.findByLotNoForUpdate(lotNo)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
        return tryScheduleLockedLot(lot);
    }

    /**
     * 특정 설비가 IDLE이 되었을 때 FIFO 순서로 그 설비를 기다리는 LOT을 찾는다.
     * OP20/OP30은 두 설비가 모두 비었을 때 같은 LOT에 원자적으로 함께 배정한다.
     */
    @Transactional
    public boolean tryAssignMachine(String machineId) {
        // 여기서 설비를 먼저 잠그면 OP20/OP30 동시 IDLE 이벤트가 서로 반대
        // 순서로 잠금을 잡을 수 있다. 실제 예약 잠금은 WorkCommandService가
        // 항상 OP20 -> OP30 순서로 획득한다.
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        if (machine.getStatus() != Machine.Status.IDLE
                || workCommandService.hasActiveCommandForMachine(machineId)) {
            return false;
        }

        String processCode = machine.getProcess().getProcessCode();
        List<Lot> candidates = lotRepository.findPipelineCandidatesForUpdate(Lot.Status.RUNNING);
        for (Lot lot : candidates) {
            if (!isWaitingForProcess(lot, processCode)) {
                continue;
            }
            if (tryScheduleLockedLot(lot)) {
                return true;
            }
        }
        return false;
    }

    /** 서버 복구나 운영자 수동 호출에 사용할 수 있는 전체 IDLE 설비 재배정 메서드다. */
    @Transactional
    public int tryAssignAllIdleMachines() {
        int assigned = 0;
        for (Machine machine : machineRepository.findAll().stream()
                .sorted((left, right) -> Integer.compare(
                        left.getProcess().getProcessOrder(),
                        right.getProcess().getProcessOrder()))
                .toList()) {
            if (machine.getStatus() == Machine.Status.IDLE
                    && tryAssignMachine(machine.getMachineId())) {
                assigned++;
            }
        }
        return assigned;
    }

    private boolean tryScheduleLockedLot(Lot lot) {
        if (lot.getStatus() != Lot.Status.RUNNING || lot.getCurrentProcess() == null) {
            return false;
        }

        String processCode = lot.getCurrentProcess().getProcessCode();
        if (OP20.equals(processCode)) {
            if (!isInitialPairUnstarted(lot)) {
                return false;
            }
            return workCommandService.tryCreateInitialStartCommands(lot).isPresent();
        }

        if (workCommandService.hasActiveExecution(lot.getLotNo(), processCode)
                || workCommandService.hasStartedProcess(lot.getLotNo(), processCode)) {
            return false;
        }

        int inputQty = expectedInputQty(lot, lot.getCurrentProcess());
        if (inputQty <= 0) {
            lot.setStatus(Lot.Status.HOLD);
            return false;
        }
        return workCommandService
                .tryCreateStartCommand(lot, lot.getCurrentProcess(), inputQty)
                .isPresent();
    }

    private boolean isWaitingForProcess(Lot lot, String machineProcessCode) {
        if (lot.getCurrentProcess() == null) {
            return false;
        }
        String lotProcessCode = lot.getCurrentProcess().getProcessCode();
        if (OP20.equals(machineProcessCode) || OP30.equals(machineProcessCode)) {
            return OP20.equals(lotProcessCode) && isInitialPairUnstarted(lot);
        }
        return machineProcessCode.equals(lotProcessCode)
                && !workCommandService.hasActiveExecution(lot.getLotNo(), lotProcessCode)
                && !workCommandService.hasStartedProcess(lot.getLotNo(), lotProcessCode);
    }

    private boolean isInitialPairUnstarted(Lot lot) {
        return !workCommandService.hasStartedProcess(lot.getLotNo(), OP20)
                && !workCommandService.hasStartedProcess(lot.getLotNo(), OP30)
                && !workCommandService.hasActiveExecution(lot.getLotNo(), OP20)
                && !workCommandService.hasActiveExecution(lot.getLotNo(), OP30);
    }

    private int expectedInputQty(Lot lot, Process process) {
        String processCode = process.getProcessCode();
        if (ASSEMBLY.equals(processCode)) {
            return Math.min(processOkQty(lot, OP20), processOkQty(lot, OP30));
        }
        return processRepository
                .findFirstByProcessOrderLessThanOrderByProcessOrderDesc(process.getProcessOrder())
                .map(previous -> processOkQty(lot, previous.getProcessCode()))
                .orElse(0);
    }

    private int processOkQty(Lot lot, String processCode) {
        return productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        lot.getLotNo(), processCode)
                .stream()
                .mapToInt(ProductionLog::getOkQty)
                .sum();
    }
}
