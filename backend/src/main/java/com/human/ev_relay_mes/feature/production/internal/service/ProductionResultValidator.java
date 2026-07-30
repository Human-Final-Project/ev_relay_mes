package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.masterdata.api.ProcessCodes;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.ProductionResultReceiveRequestDto;
import org.springframework.stereotype.Component;

@Component
class ProductionResultValidator {

    void validateRequest(ProductionResultReceiveRequestDto request) {
        if (request.getInputQty() != request.getOkQty() + request.getNgQty()) {
            throw new CustomException(ErrorCode.PRODUCTION_QUANTITY_MISMATCH);
        }
        if (request.getStartedAt() != null && request.getEndedAt() != null
                && request.getStartedAt().isAfter(request.getEndedAt())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "생산 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    void validateLot(Lot lot, String processCode) {
        if (lot.getStatus() != Lot.Status.RUNNING && lot.getStatus() != Lot.Status.HOLD) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS,
                    "생산 중이거나 설비 오류로 보류된 LOT에만 실적을 등록할 수 있습니다.");
        }
        if (lot.getCurrentProcess() == null || !isProcessReady(lot, processCode)) {
            throw new CustomException(ErrorCode.INVALID_PROCESS_ORDER,
                    "LOT의 현재 공정과 생산실적 공정이 일치하지 않습니다.");
        }
    }

    void validateMachineProcess(Machine machine, Process process) {
        if (!machine.getProcess().getProcessCode().equals(process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "설비에 지정된 공정과 생산실적 공정이 일치하지 않습니다.");
        }
    }

    void validateCumulativeQuantity(int expectedInputQty, String status, int totalInputQty) {
        if (totalInputQty > expectedInputQty) {
            throw new CustomException(ErrorCode.INVALID_PRODUCTION_QUANTITY,
                    "공정 생산수량이 LOT 투입수량을 초과합니다.");
        }
        if ("COMPLETED".equalsIgnoreCase(status) && totalInputQty < expectedInputQty) {
            throw new CustomException(ErrorCode.INVALID_PRODUCTION_STATUS,
                    "LOT 투입수량을 모두 처리하기 전에는 공정을 완료할 수 없습니다.");
        }
    }

    private boolean isProcessReady(Lot lot, String processCode) {
        String currentProcessCode = lot.getCurrentProcess().getProcessCode();
        return currentProcessCode.equals(processCode)
                || (ProcessCodes.WINDING.equals(currentProcessCode)
                && ProcessCodes.CONTACT_WELDING.equals(processCode));
    }
}
