package com.human.ev_relay_mes.feature.collector.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.collector.api.WorkCommand;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandAckRequestDto;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandResponseDto;
import com.human.ev_relay_mes.feature.collector.internal.repository.WorkCommandRepository;
import com.human.ev_relay_mes.feature.masterdata.api.InspectionStandardOperations;
import com.human.ev_relay_mes.feature.production.api.LotResponsibilityOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Component
@RequiredArgsConstructor
class WorkCommandAcknowledgementProcessor {

    private final WorkCommandRepository workCommandRepository;
    private final LotResponsibilityOperations lotResponsibilityOperations;
    private final InspectionStandardOperations inspectionStandardOperations;

    WorkCommandResponseDto acknowledge(WorkCommandAckRequestDto request) {
        WorkCommand command = workCommandRepository
                .findByIdForUpdate(request.getCommandId())
                .orElseThrow(() -> new CustomException(
                        ErrorCode.WORK_COMMAND_NOT_FOUND));
        if (!command.getMachine().getMachineId().equals(request.getMachineId())) {
            throw new CustomException(ErrorCode.WORK_COMMAND_MACHINE_MISMATCH);
        }

        WorkCommand.Status acknowledgedStatus =
                WorkCommand.Status.valueOf(request.getAckStatus().toUpperCase());
        if (isAlreadyProcessed(command, acknowledgedStatus)) {
            captureStartContextIfAccepted(command, acknowledgedStatus);
            applyMissingAcknowledgement(command, request);
            return WorkCommandResponseDto.fromEntity(command);
        }
        if (command.getStatus() != WorkCommand.Status.DISPATCHED) {
            throw new CustomException(ErrorCode.INVALID_WORK_COMMAND_STATUS);
        }

        command.setStatus(acknowledgedStatus);
        captureStartContextIfAccepted(command, acknowledgedStatus);
        command.setAckMessage(request.getMessage());
        command.setAcknowledgedAt(LocalDateTime.now());
        return WorkCommandResponseDto.fromEntity(command);
    }

    private boolean isAlreadyProcessed(
            WorkCommand command, WorkCommand.Status acknowledgedStatus) {
        boolean acceptedAlreadyProcessed =
                acknowledgedStatus == WorkCommand.Status.ACCEPTED
                        && EnumSet.of(
                        WorkCommand.Status.ACCEPTED,
                        WorkCommand.Status.COMPLETED,
                        WorkCommand.Status.CANCELED)
                        .contains(command.getStatus());
        return command.getStatus() == acknowledgedStatus
                || acceptedAlreadyProcessed;
    }

    private void applyMissingAcknowledgement(
            WorkCommand command, WorkCommandAckRequestDto request) {
        if (command.getAcknowledgedAt() == null) {
            command.setAcknowledgedAt(LocalDateTime.now());
        }
        if (command.getAckMessage() == null
                || command.getAckMessage().isBlank()) {
            command.setAckMessage(request.getMessage());
        }
    }

    private void captureStartContextIfAccepted(
            WorkCommand command, WorkCommand.Status status) {
        if (status != WorkCommand.Status.ACCEPTED
                || command.getCommandType() == WorkCommand.CommandType.STOP) {
            return;
        }
        lotResponsibilityOperations.captureIfAbsent(
                command.getLot(), command.getProcess(), command.getMachine());
        if (inspectionStandardOperations.supportsMeasurements(
                command.getProcess().getProcessCode())) {
            inspectionStandardOperations.captureStandardsIfAbsent(
                    command.getLot(), command.getProcess());
        }
    }
}
