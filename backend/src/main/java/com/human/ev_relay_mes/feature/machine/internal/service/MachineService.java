package com.human.ev_relay_mes.feature.machine.internal.service;

import com.human.ev_relay_mes.common.util.RequestValues;
import com.human.ev_relay_mes.feature.machine.api.MachineStatusReceiveRequestDto;
import com.human.ev_relay_mes.feature.machine.api.MachineResponseDto;
import com.human.ev_relay_mes.feature.machine.api.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineStatusHistory;
import com.human.ev_relay_mes.feature.machine.api.MachineMonitoring;
import com.human.ev_relay_mes.feature.production.api.ProductionSchedulingRequests;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandOperations;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineRepository;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.feature.production.api.ProductionData;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineService implements MachineMonitoring {

    private final MachineRepository machineRepository;
    private final MachineStatusHistoryRepository machineStatusHistoryRepository;
    private final MasterDataLookup masterDataLookup;
    private final ProductionData productionData;
    private final WorkCommandOperations workCommandService;
    private final ProductionSchedulingRequests productionSchedulingRequests;
    private final MachineCommunicationRecovery communicationRecovery;
    private final MachineResponseAssembler responseAssembler;

    // 설비 현황 화면에 전체 설비의 기본 정보와 현재 상태를 표시할 때 사용한다.
    @Override
    public List<MachineResponseDto> getMachines() {
        return machineRepository.findAll().stream()
                .map(responseAssembler::toMachineResponse)
                .toList();
    }

    // 설비 상세 화면에서 특정 설비의 정보와 현재 상태를 조회할 때 사용한다.
    @Override
    public MachineResponseDto getMachine(String machineId) {
        return responseAssembler.toMachineResponse(findMachine(machineId));
    }

    // L2가 전달한 설비 상태를 현재 상태에 반영하고 상태 변경 이력을 남길 때 사용한다.
    @Transactional
    @Override
    public MachineStatusHistoryResponseDto updateStatus(MachineStatusReceiveRequestDto dto) {
        String eventId = RequestValues.trimToNull(dto.getEventId());
        if (eventId != null) {
            var existing = machineStatusHistoryRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return responseAssembler.toHistoryResponse(existing.get());
            }
        }
        Machine machine = findMachine(dto.getMachineId());
        Machine.Status previousStatus = machine.getStatus();
        Machine.Status status = parseStatus(dto.getStatus());
        Lot lot = RequestValues.isBlank(dto.getLotNo()) ? null : findLot(dto.getLotNo());
        com.human.ev_relay_mes.feature.masterdata.api.Process process =
                RequestValues.isBlank(dto.getProcessCode()) ? null
                : masterDataLookup.getRequiredProcess(dto.getProcessCode());

        boolean connectionStateSync = "connection_state_sync".equalsIgnoreCase(dto.getMessage());
        if (connectionStateSync) {
            communicationRecovery.synchronize(machine, status, lot, process);
        }

        boolean resumeCompleted = status == Machine.Status.RUNNING
                && lot != null
                && process != null
                && lot.getStatus() == Lot.Status.HOLD
                && workCommandService.completeResumeCommand(lot, process, machine);
        if (resumeCompleted) {
            lot.setStatus(Lot.Status.RUNNING);
        }

        var latestHistory = machineStatusHistoryRepository
                .findFirstByMachine_MachineIdOrderByRecordedAtDesc(machine.getMachineId());
        if (!resumeCompleted
                && previousStatus == status
                && latestHistory.filter(history -> sameStatusSnapshot(history, status, lot, process)).isPresent()) {
            if (status == Machine.Status.IDLE) {
                productionSchedulingRequests.requestMachine(machine.getMachineId());
            }
            return responseAssembler.toHistoryResponse(latestHistory.get());
        }

        machine.setStatus(status);
        MachineStatusHistory history = MachineStatusHistory.builder()
                .eventId(eventId)
                .machine(machine)
                .status(status)
                .lot(lot)
                .process(process)
                .message(dto.getMessage())
                .build();
        MachineStatusHistory savedHistory = machineStatusHistoryRepository.save(history);
        if (status == Machine.Status.IDLE) {
            productionSchedulingRequests.requestMachine(machine.getMachineId());
        }
        return responseAssembler.toHistoryResponse(savedHistory);
    }

    // 설비 상세 화면에서 특정 설비의 상태 변화 이력을 최신순으로 표시할 때 사용한다.
    @Override
    public List<MachineStatusHistoryResponseDto> getStatusHistory(String machineId) {
        findMachine(machineId);
        return machineStatusHistoryRepository.findByMachine_MachineIdOrderByRecordedAtDesc(machineId)
                .stream().map(responseAssembler::toHistoryResponse).toList();
    }

    private boolean sameStatusSnapshot(
            MachineStatusHistory history,
            Machine.Status status,
            Lot lot,
            com.human.ev_relay_mes.feature.masterdata.api.Process process) {
        String historyLotNo = history.getLot() == null ? null : history.getLot().getLotNo();
        String requestedLotNo = lot == null ? null : lot.getLotNo();
        String historyProcessCode = history.getProcess() == null
                ? null : history.getProcess().getProcessCode();
        String requestedProcessCode = process == null ? null : process.getProcessCode();
        return history.getStatus() == status
                && java.util.Objects.equals(historyLotNo, requestedLotNo)
                && java.util.Objects.equals(historyProcessCode, requestedProcessCode);
    }

    // 요청된 설비가 실제 등록된 설비인지 확인하고 Entity를 가져올 때 내부적으로 사용한다.
    private Machine findMachine(String machineId) {
        return machineRepository.findById(machineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
    }

    // 설비 상태 메시지에 포함된 LOT 번호를 생산 LOT와 연결할 때 내부적으로 사용한다.
    private Lot findLot(String lotNo) {
        return productionData.getRequiredLot(lotNo);
    }

    // 외부에서 받은 상태 문자열을 설비 상태 Enum으로 안전하게 변환할 때 사용한다.
    private Machine.Status parseStatus(String status) {
        return RequestValues.parseEnum(
                Machine.Status.class,
                status,
                ErrorCode.INVALID_MACHINE_STATUS);
    }

}
