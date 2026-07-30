package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.feature.quality.api.Inspection;
import com.human.ev_relay_mes.feature.quality.api.InspectionResponseDto;
import com.human.ev_relay_mes.feature.quality.api.InspectionUnitResult;
import com.human.ev_relay_mes.feature.quality.api.InspectionUnitResultResponseDto;
import org.springframework.stereotype.Component;

@Component
class InspectionResponseAssembler {

    InspectionResponseDto toResponse(Inspection inspection) {
        return InspectionResponseDto.builder()
                .inspectionId(inspection.getInspectionId())
                .lotNo(inspection.getLot().getLotNo())
                .machineId(inspection.getMachine().getMachineId())
                .machineName(inspection.getMachine().getMachineName())
                .processCode(inspection.getProcess().getProcessCode())
                .processName(inspection.getProcess().getProcessName())
                .unitSeq(inspection.getUnitSeq())
                .inspectionItem(inspection.getInspectionItem())
                .measuredValue(inspection.getMeasuredValue())
                .unit(inspection.getUnit())
                .lowerLimit(inspection.getStandardSnapshot().getLowerLimit())
                .upperLimit(inspection.getStandardSnapshot().getUpperLimit())
                .standardVersion(inspection.getStandardSnapshot().getStandardVersion())
                .result(inspection.getResult().name())
                .inspectedAt(inspection.getInspectedAt())
                .build();
    }

    InspectionUnitResultResponseDto toUnitResponse(InspectionUnitResult result) {
        return InspectionUnitResultResponseDto.builder()
                .lotNo(result.getLot().getLotNo())
                .machineId(result.getMachine().getMachineId())
                .processCode(result.getProcess().getProcessCode())
                .unitSeq(result.getUnitSeq())
                .l1Result(nameOf(result.getL1Result()))
                .measurementResult(nameOf(result.getMeasurementResult()))
                .result(nameOf(result.getResult()))
                .evaluationStatus(result.getEvaluationStatus().name())
                .build();
    }

    private String nameOf(Inspection.Result result) {
        return result == null ? null : result.name();
    }
}
