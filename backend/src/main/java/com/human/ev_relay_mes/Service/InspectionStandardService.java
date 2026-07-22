package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.InspectionStandardRequestDto;
import com.human.ev_relay_mes.Dto.Response.InspectionStandardResponseDto;
import com.human.ev_relay_mes.Entity.*;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionStandardService {
    private final InspectionStandardRepository standardRepository;
    private final LotInspectionStandardSnapshotRepository snapshotRepository;
    private final ProcessRepository processRepository;

    public List<InspectionStandardResponseDto> getAll() {
        ensureDefaultsForKnownProcesses();
        return standardRepository.findAll(Sort.by("process.processOrder", "standardId"))
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

    public static boolean supportsMeasurements(String processCode) {
        return List.of("OP20", "OP30", "OP60", "OP70").contains(processCode);
    }

    @Transactional
    public void ensureDefaultStandards(Process process) {
        if (standardRepository.countByProcess_ProcessCode(process.getProcessCode()) > 0) return;
        switch (process.getProcessCode()) {
            case "OP20" -> standardRepository.save(defaultStandard(process, "COIL_RESISTANCE", "코일 저항", "OHM", "80.000", "120.000"));
            case "OP30" -> {
                standardRepository.save(defaultStandard(process, "WELD_STRENGTH", "용접 강도", "N", "40.000", "80.000"));
                standardRepository.save(defaultStandard(process, "CONTACT_RESISTANCE", "접촉 저항", "mOHM", "0.000", "50.000"));
                standardRepository.save(defaultStandard(process, "CONTACT_POSITION", "접점 위치 편차", "MM", "0.000", "0.200"));
            }
            case "OP60" -> {
                standardRepository.save(defaultStandard(process, "GAS_PRESSURE", "가스 압력", "BAR", "2.500", "3.500"));
                standardRepository.save(defaultStandard(process, "LEAK_RATE", "누설률", "SCCM", "0.000", "0.500"));
            }
            case "OP70" -> {
                standardRepository.save(defaultStandard(process, "INSULATION_RESISTANCE", "절연 저항", "MOHM", "100.000", "1000.000"));
                standardRepository.save(defaultStandard(process, "WITHSTAND_VOLTAGE", "내전압", "V", "1500.000", "2000.000"));
                standardRepository.save(defaultStandard(process, "OPERATION_VOLTAGE", "동작 전압", "V", "10.000", "14.000"));
                standardRepository.save(defaultStandard(process, "CONTACT_BOUNCE", "접점 바운스 시간", "MS", "0.000", "5.000"));
            }
            default -> { }
        }
    }

    private void ensureDefaultsForKnownProcesses() {
        List.of("OP20", "OP30", "OP60", "OP70").forEach(code ->
                processRepository.findById(code).ifPresent(this::ensureDefaultStandards));
    }

    private InspectionStandard defaultStandard(Process p, String item, String name, String unit, String low, String high) {
        return InspectionStandard.builder().process(p).inspectionItem(item).itemName(name).unit(unit)
                .lowerLimit(new BigDecimal(low)).upperLimit(new BigDecimal(high)).standardVersion(1).build();
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
