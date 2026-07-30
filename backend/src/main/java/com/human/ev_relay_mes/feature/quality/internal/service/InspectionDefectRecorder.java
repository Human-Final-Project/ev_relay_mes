package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.quality.api.DefectHistoryCreateRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InspectionDefectRecorder {

    private final DefectService defectService;

    void record(Lot lot, Machine machine, Process process,
                Integer unitSeq, String defectCode, String message) {
        DefectHistoryCreateRequestDto defect = new DefectHistoryCreateRequestDto();
        defect.setEventId("AUTO-" + lot.getLotNo() + "-" + process.getProcessCode()
                + "-" + unitSeq + "-" + defectCode);
        defect.setLotNo(lot.getLotNo());
        defect.setMachineId(machine.getMachineId());
        defect.setProcessCode(process.getProcessCode());
        defect.setDefectCode(defectCode);
        defect.setDefectQty(1);
        defect.setMessage(message);
        defectService.createDefect(defect);
    }
}
