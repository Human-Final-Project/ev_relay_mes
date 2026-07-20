package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.InspectionStandardRequestDto;
import com.human.ev_relay_mes.Dto.Response.InspectionStandardResponseDto;
import com.human.ev_relay_mes.Entity.*;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionStandardService {
    private static final String INSPECTION_PROCESS_CODE = "OP70";

    private final InspectionStandardRepository standardRepository;
    private final LotInspectionStandardSnapshotRepository snapshotRepository;
    private final ProcessRepository processRepository;

    public List<InspectionStandardResponseDto> getAll() {
        Process process = inspectionProcess();
        ensureDefaultStandards(process);
        return standardRepository.findByProcess_ProcessCodeOrderByStandardIdAsc(INSPECTION_PROCESS_CODE)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public InspectionStandardResponseDto updateLimits(Long standardId, InspectionStandardRequestDto dto) {
        validateLimits(dto.getLowerLimit(), dto.getUpperLimit());
        InspectionStandard standard = standardRepository.findById(standardId)
                .orElseThrow(() -> new CustomException(ErrorCode.INSPECTION_STANDARD_NOT_FOUND));
        standard.setLowerLimit(dto.getLowerLimit());
        standard.setUpperLimit(dto.getUpperLimit());
        standard.setStandardVersion(standard.getStandardVersion() + 1);
        return toResponse(standard);
    }

    @Transactional
    public List<LotInspectionStandardSnapshot> captureStandardsIfAbsent(Lot lot, Process process) {
        List<LotInspectionStandardSnapshot> existing = snapshotRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderBySnapshotIdAsc(
                        lot.getLotNo(), process.getProcessCode());
        if (!existing.isEmpty()) return existing;

        ensureDefaultStandards(process);
        List<InspectionStandard> standards = standardRepository
                .findByProcess_ProcessCodeOrderByStandardIdAsc(process.getProcessCode());
        if (standards.isEmpty()) {
            throw new CustomException(ErrorCode.INSPECTION_STANDARD_NOT_CONFIGURED);
        }
        return snapshotRepository.saveAll(standards.stream()
                .map(s -> LotInspectionStandardSnapshot.builder()
                        .lot(lot).process(process)
                        .inspectionItem(s.getInspectionItem())
                        .itemName(s.getItemName()).unit(s.getUnit())
                        .lowerLimit(s.getLowerLimit()).upperLimit(s.getUpperLimit())
                        .standardVersion(s.getStandardVersion()).build())
                .toList());
    }

    @Transactional
    public LotInspectionStandardSnapshot resolveSnapshot(Lot lot, Process process, String inspectionItem) {
        String item = inspectionItem == null ? "" : inspectionItem.trim().toUpperCase();
        return snapshotRepository.findByLot_LotNoAndProcess_ProcessCodeAndInspectionItem(
                        lot.getLotNo(), process.getProcessCode(), item)
                .orElseGet(() -> {
                    captureStandardsIfAbsent(lot, process);
                    return snapshotRepository.findByLot_LotNoAndProcess_ProcessCodeAndInspectionItem(
                                    lot.getLotNo(), process.getProcessCode(), item)
                            .orElseThrow(() -> new CustomException(ErrorCode.INSPECTION_STANDARD_NOT_FOUND));
                });
    }

    public long snapshotCount(Lot lot, Process process) {
        return snapshotRepository.countByLot_LotNoAndProcess_ProcessCode(
                lot.getLotNo(), process.getProcessCode());
    }

    @Transactional
    public void ensureDefaultStandards(Process process) {
        if (!INSPECTION_PROCESS_CODE.equals(process.getProcessCode())
                || standardRepository.countByProcess_ProcessCode(process.getProcessCode()) > 0) return;
        standardRepository.save(defaultStandard(process, "OPERATION_VOLTAGE", "동작 전압", "V", "10.000", "14.000"));
        standardRepository.save(defaultStandard(process, "COIL_RESISTANCE", "코일 저항", "OHM", "80.000", "120.000"));
        standardRepository.save(defaultStandard(process, "CONTACT_RESISTANCE", "접촉 저항", "mOHM", "0.000", "50.000"));
    }

    private InspectionStandard defaultStandard(Process p, String item, String name, String unit, String low, String high) {
        return InspectionStandard.builder().process(p).inspectionItem(item).itemName(name).unit(unit)
                .lowerLimit(new BigDecimal(low)).upperLimit(new BigDecimal(high)).standardVersion(1).build();
    }

    private Process inspectionProcess() {
        return processRepository.findById(INSPECTION_PROCESS_CODE)
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
    }

    private void validateLimits(BigDecimal lower, BigDecimal upper) {
        if (lower == null && upper == null)
            throw new CustomException(ErrorCode.INVALID_INSPECTION_LIMIT);
        if (lower != null && upper != null && lower.compareTo(upper) > 0)
            throw new CustomException(ErrorCode.INVALID_INSPECTION_LIMIT);
    }

    private InspectionStandardResponseDto toResponse(InspectionStandard s) {
        return InspectionStandardResponseDto.builder()
                .standardId(s.getStandardId()).processCode(s.getProcess().getProcessCode())
                .processName(s.getProcess().getProcessName()).inspectionItem(s.getInspectionItem())
                .itemName(s.getItemName()).unit(s.getUnit()).lowerLimit(s.getLowerLimit())
                .upperLimit(s.getUpperLimit()).standardVersion(s.getStandardVersion())
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt()).build();
    }
}
