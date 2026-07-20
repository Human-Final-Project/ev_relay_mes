package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.LotCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.LotStatusRequestDto;
import com.human.ev_relay_mes.Dto.Response.LotResponseDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotService {

    private final LotRepository lotRepository;
    private final WorkOrderRepository workOrderRepository;
    private final MemberRepository memberRepository;
    private final ProcessRepository processRepository;
    private final MaterialLotService materialLotService;
    private final WorkCommandService workCommandService;
    private final LotProcessResponsibleService lotProcessResponsibleService;

    @Transactional
    public LotResponseDto createLot(Long workOrderId, LotCreateRequestDto dto, Long memberId) {
        WorkOrder workOrder = findWorkOrderForUpdate(workOrderId);
        if (workOrder.getStatus() != WorkOrder.Status.RELEASED
                && workOrder.getStatus() != WorkOrder.Status.RUNNING) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "확정된 작업지시에서만 생산 LOT를 생성할 수 있습니다.");
        }
        long allocatedQty = lotRepository.sumInputQtyByWorkOrderIdExcludingStatus(
                workOrderId, Lot.Status.SCRAPPED);
        if (allocatedQty + dto.getInputQty() > workOrder.getTargetQty()) {
            throw new CustomException(ErrorCode.INVALID_LOT_QUANTITY,
                    "LOT 수량 합계가 작업지시 목표 수량을 초과합니다.");
        }
        Member creator = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        Process firstProcess = processRepository.findFirstByUseYnOrderByProcessOrderAsc("Y")
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
        Lot lot = Lot.builder()
                .lotNo(generateLotNo())
                .workOrder(workOrder)
                .item(workOrder.getItem())
                .currentProcess(firstProcess)
                .inputQty(dto.getInputQty())
                .createdBy(creator)
                .build();
        return toResponse(lotRepository.save(lot));
    }

    public List<LotResponseDto> getLots(String status, Long workOrderId) {
        List<Lot> lots;
        if (workOrderId != null) {
            findWorkOrder(workOrderId);
            lots = lotRepository.findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(workOrderId);
            if (!isBlank(status)) {
                Lot.Status parsedStatus = parseStatus(status);
                lots = lots.stream().filter(lot -> lot.getStatus() == parsedStatus).toList();
            }
        } else {
            lots = isBlank(status)
                    ? lotRepository.findAllByOrderByCreatedAtDesc()
                    : lotRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        }
        return lots.stream().map(this::toResponse).toList();
    }

    public LotResponseDto getLot(Long id) {
        return toResponse(findLot(id));
    }

    public LotResponseDto getLotByNo(String lotNo) {
        return toResponse(findLotByNo(lotNo));
    }

    @Transactional
    public LotResponseDto updateStatus(Long id, LotStatusRequestDto dto) {
        Lot lot = findLotForUpdate(id);
        Lot.Status targetStatus = parseStatus(dto.getStatus());
        if (lot.getStatus() == targetStatus) {
            return toResponse(lot);
        }
        validateTransition(lot, targetStatus);
        applyStatus(lot, targetStatus);
        return toResponse(lot);
    }

    @Transactional
    public void deleteLot(Long id) {
        Lot lot = findLotForUpdate(id);
        if (lot.getStatus() != Lot.Status.WAITING) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS_TRANSITION,
                    "대기 상태의 LOT만 삭제할 수 있습니다.");
        }
        lotRepository.delete(lot);
    }

    private void validateTransition(Lot lot, Lot.Status targetStatus) {
        boolean allowed = switch (lot.getStatus()) {
            case WAITING -> targetStatus == Lot.Status.RUNNING
                    || targetStatus == Lot.Status.HOLD
                    || targetStatus == Lot.Status.SCRAPPED;
            case RUNNING -> targetStatus == Lot.Status.HOLD
                    || targetStatus == Lot.Status.COMPLETED
                    || targetStatus == Lot.Status.SCRAPPED;
            case HOLD -> targetStatus == Lot.Status.WAITING
                    || targetStatus == Lot.Status.RUNNING
                    || targetStatus == Lot.Status.SCRAPPED;
            case COMPLETED, SCRAPPED -> false;
        };
        if (!allowed) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS_TRANSITION);
        }
        if (targetStatus == Lot.Status.COMPLETED
                && lot.getOkQty() + lot.getNgQty() != lot.getInputQty()) {
            throw new CustomException(ErrorCode.INVALID_LOT_QUANTITY,
                    "투입 수량과 양품·불량 수량이 일치해야 LOT를 완료할 수 있습니다.");
        }
    }

    private void applyStatus(Lot lot, Lot.Status targetStatus) {
        if (targetStatus != Lot.Status.RUNNING) {
            lot.setStartRequestedAt(null);
        }
        if (targetStatus == Lot.Status.RUNNING) {
            if (lot.getStartedAt() == null) {
                lot.setStartRequestedAt(LocalDateTime.now());
                if (!tryStart(lot)) {
                    return;
                }
            }
            WorkOrder workOrder = lot.getWorkOrder();
            if (workOrder.getStatus() == WorkOrder.Status.RELEASED) {
                workOrder.setStatus(WorkOrder.Status.RUNNING);
            }
        }
        lot.setStatus(targetStatus);
        if (targetStatus == Lot.Status.COMPLETED || targetStatus == Lot.Status.SCRAPPED) {
            lot.setCompletedAt(LocalDateTime.now());
        }
    }

    private boolean tryStart(Lot lot) {
        try {
            materialLotService.consumeMaterials(lot.getItem().getItemCode(), lot.getInputQty());
        } catch (CustomException exception) {
            if (exception.getErrorCode() == ErrorCode.INSUFFICIENT_MATERIAL_QUANTITY) {
                return false;
            }
            throw exception;
        }
        workCommandService.createInitialStartCommands(lot);
        lot.setStartedAt(LocalDateTime.now());
        lot.setStartRequestedAt(null);
        return true;
    }

    @TransactionalEventListener(
            classes = MaterialLotService.MaterialStockChangedEvent.class,
            phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryMaterialWaitingLots() {
        List<Long> lotIds = lotRepository
                .findByStatusAndStartRequestedAtIsNotNullOrderByStartRequestedAtAsc(Lot.Status.WAITING)
                .stream().map(Lot::getLotId).toList();
        for (Long lotId : lotIds) {
            Lot lot = findLotForUpdate(lotId);
            if (lot.getStatus() != Lot.Status.WAITING || lot.getStartRequestedAt() == null) {
                continue;
            }
            if (tryStart(lot)) {
                lot.setStatus(Lot.Status.RUNNING);
                if (lot.getWorkOrder().getStatus() == WorkOrder.Status.RELEASED) {
                    lot.getWorkOrder().setStatus(WorkOrder.Status.RUNNING);
                }
            }
        }
    }

    private Lot findLot(Long id) {
        return lotRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private Lot findLotForUpdate(Long id) {
        return lotRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private Lot findLotByNo(String lotNo) {
        return lotRepository.findByLotNo(lotNo)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private WorkOrder findWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));
    }

    private WorkOrder findWorkOrderForUpdate(Long id) {
        return workOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));
    }

    private Lot.Status parseStatus(String status) {
        try {
            return Lot.Status.valueOf(status.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS);
        }
    }

    private String generateLotNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String lotNo;
        do {
            lotNo = "LOT-" + date + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (lotRepository.existsByLotNo(lotNo));
        return lotNo;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LotResponseDto toResponse(Lot lot) {
        return LotResponseDto.fromEntity(
                lot,
                lotProcessResponsibleService.getByLotNo(lot.getLotNo()));
    }

}
