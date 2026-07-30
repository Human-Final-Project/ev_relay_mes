package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.common.util.RequestValues;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.masterdata.api.ProcessCodes;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.quality.api.Inspection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
class InspectionRules {

    private static final Map<String, String> MEASUREMENT_DEFECT_CODES = Map.ofEntries(
            Map.entry(ProcessCodes.WINDING + ":COIL_RESISTANCE", "COIL_RESISTANCE_NG"),
            Map.entry(ProcessCodes.CONTACT_WELDING + ":WELD_STRENGTH", "WELD_STRENGTH_NG"),
            Map.entry(ProcessCodes.CONTACT_WELDING + ":CONTACT_RESISTANCE", "CONTACT_RESISTANCE_NG"),
            Map.entry(ProcessCodes.CONTACT_WELDING + ":CONTACT_POSITION", "CONTACT_POSITION_NG"),
            Map.entry(ProcessCodes.SEALING + ":GAS_PRESSURE", "GAS_PRESSURE_NG"),
            Map.entry(ProcessCodes.SEALING + ":LEAK_RATE", "SEAL_LEAK_NG"),
            Map.entry(ProcessCodes.FINAL_INSPECTION + ":INSULATION_RESISTANCE", "INSULATION_NG"),
            Map.entry(ProcessCodes.FINAL_INSPECTION + ":WITHSTAND_VOLTAGE", "WITHSTAND_VOLTAGE_NG"),
            Map.entry(ProcessCodes.FINAL_INSPECTION + ":OPERATION_VOLTAGE", "OPERATION_VOLTAGE_NG"),
            Map.entry(ProcessCodes.FINAL_INSPECTION + ":CONTACT_BOUNCE", "CONTACT_BOUNCE_NG"));

    Inspection.Result judge(BigDecimal value, BigDecimal lower, BigDecimal upper) {
        boolean below = lower != null && value.compareTo(lower) < 0;
        boolean above = upper != null && value.compareTo(upper) > 0;
        return below || above ? Inspection.Result.NG : Inspection.Result.OK;
    }

    Inspection.Result parseJudgment(String value) {
        return RequestValues.parseEnum(
                Inspection.Result.class,
                value,
                ErrorCode.INVALID_INSPECTION_RESULT);
    }

    void validateLot(Lot lot, Process process) {
        if (lot.getStatus() != Lot.Status.RUNNING) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS,
                    "생산 중인 LOT에만 검사 측정값을 등록할 수 있습니다.");
        }
        if (lot.getCurrentProcess() == null
                || !isProcessReady(lot, process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_PROCESS_ORDER,
                    "LOT의 현재 공정과 판정 공정이 일치하지 않습니다.");
        }
    }

    void validateMachineProcess(Machine machine, Process process) {
        if (!machine.getProcess().getProcessCode().equals(process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "설비에 지정된 공정과 검사 공정이 일치하지 않습니다.");
        }
    }

    void validateUnitSequence(int unitSeq, int expectedInputQty) {
        if (unitSeq > expectedInputQty) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_UNIT_SEQ,
                    "검사 일련번호가 공정 투입수량을 초과합니다.");
        }
    }

    void validateUnit(String receivedUnit, String standardUnit) {
        if (!RequestValues.isBlank(receivedUnit)
                && !receivedUnit.equalsIgnoreCase(standardUnit)) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_VALUE,
                    "측정 단위가 검사 기준 단위와 일치하지 않습니다.");
        }
    }

    String measurementDefectCode(String processCode, String inspectionItem) {
        String code = MEASUREMENT_DEFECT_CODES.get(processCode + ":" + inspectionItem);
        if (code == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "검사 항목에 연결된 불량 코드가 없습니다.");
        }
        return code;
    }

    private boolean isProcessReady(Lot lot, String processCode) {
        String current = lot.getCurrentProcess().getProcessCode();
        return current.equals(processCode)
                || (ProcessCodes.WINDING.equals(current)
                && ProcessCodes.CONTACT_WELDING.equals(processCode));
    }
}
