package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.InspectionStandardRequestDto;
import com.human.ev_relay_mes.Dto.Response.InspectionStandardResponseDto;
import com.human.ev_relay_mes.Entity.InspectionStandard;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.LotInspectionStandardSnapshot;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.InspectionStandardRepository;
import com.human.ev_relay_mes.Repository.LotInspectionStandardSnapshotRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionStandardService {

    public static final String INSPECTION_PROCESS_CODE = "OP70";

    private final InspectionStandardRepository standardRepository;
    private final LotInspectionStandardSnapshotRepository snapshotRepository;
    private final ProcessRepository processRepository;

    @Transactional
    public List<InspectionStandardResponseDto> getAll() {
        processRepository.findById(INSPECTION_PROCESS_CODE)
                .filter(process -> "Y".equalsIgnoreCase(process.getUseYn()))
                .ifPresent(this::ensureDefaultStandards);
        return standardRepository.findAllByOrderByProcess_ProcessOrderAscStandardIdAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public InspectionStandardResponseDto create(InspectionStandardRequestDto dto) {
        validateLimits(dto.getLowerLimit(), dto.getUpperLimit());
        String processCode = normalizeProcessCode(dto.getProcessCode());
        String inspectionItem = normalizeInspectionItem(dto.getInspectionItem());
        Process process = inspectionProcess(processCode);
        standardRepository.findByProcess_ProcessCodeAndInspectionItem(
                        processCode, inspectionItem)
                .ifPresent(existing -> {
                    throw new CustomException(ErrorCode.DUPLICATE_RESOURCE,
                            "이미 등록된 공정 검사 항목입니다.");
                });

        InspectionStandard standard = InspectionStandard.builder()
                .process(process)
                .inspectionItem(inspectionItem)
                .itemName(dto.getItemName().trim())
                .unit(dto.getUnit().trim())
                .lowerLimit(dto.getLowerLimit())
                .upperLimit(dto.getUpperLimit())
                .useYn(normalizeUseYn(dto.getUseYn()))
                .standardVersion(1)
                .build();
        return toResponse(standardRepository.save(standard));
    }

    @Transactional
    public InspectionStandardResponseDto update(
            Long standardId, InspectionStandardRequestDto dto) {
        validateLimits(dto.getLowerLimit(), dto.getUpperLimit());
        InspectionStandard standard = standardRepository.findById(standardId)
                .orElseThrow(() -> new CustomException(ErrorCode.INSPECTION_STANDARD_NOT_FOUND));
        String processCode = normalizeProcessCode(dto.getProcessCode());
        String inspectionItem = normalizeInspectionItem(dto.getInspectionItem());
        Process process = inspectionProcess(processCode);

        standardRepository.findByProcess_ProcessCodeAndInspectionItem(
                        processCode, inspectionItem)
                .filter(existing -> !existing.getStandardId().equals(standardId))
                .ifPresent(existing -> {
                    throw new CustomException(ErrorCode.DUPLICATE_RESOURCE,
                            "이미 등록된 공정 검사 항목입니다.");
                });

        standard.setProcess(process);
        standard.setInspectionItem(inspectionItem);
        standard.setItemName(dto.getItemName().trim());
        standard.setUnit(dto.getUnit().trim());
        standard.setLowerLimit(dto.getLowerLimit());
        standard.setUpperLimit(dto.getUpperLimit());
        standard.setUseYn(normalizeUseYn(dto.getUseYn()));
        standard.setStandardVersion(standard.getStandardVersion() + 1);
        return toResponse(standard);
    }

    @Transactional
    public List<LotInspectionStandardSnapshot> captureStandardsIfAbsent(
            Lot lot, Process process) {
        List<LotInspectionStandardSnapshot> existing = snapshotRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderBySnapshotIdAsc(
                        lot.getLotNo(), process.getProcessCode());
        if (!existing.isEmpty()) {
            return existing;
        }

        ensureDefaultStandards(process);
        List<InspectionStandard> activeStandards = standardRepository
                .findByProcess_ProcessCodeAndUseYnOrderByStandardIdAsc(
                        process.getProcessCode(), "Y");
        if (activeStandards.isEmpty()) {
            throw new CustomException(ErrorCode.INSPECTION_STANDARD_NOT_CONFIGURED,
                    "활성화된 검사 기준이 없습니다.");
        }

        List<LotInspectionStandardSnapshot> snapshots = activeStandards.stream()
                .map(standard -> LotInspectionStandardSnapshot.builder()
                        .lot(lot)
                        .process(process)
                        .inspectionItem(standard.getInspectionItem())
                        .itemName(standard.getItemName())
                        .unit(standard.getUnit())
                        .lowerLimit(standard.getLowerLimit())
                        .upperLimit(standard.getUpperLimit())
                        .standardVersion(standard.getStandardVersion())
                        .build())
                .toList();
        return snapshotRepository.saveAll(snapshots);
    }

    @Transactional
    public LotInspectionStandardSnapshot resolveSnapshot(
            Lot lot, Process process, String inspectionItem) {
        String normalizedItem = normalizeInspectionItem(inspectionItem);
        return snapshotRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndInspectionItem(
                        lot.getLotNo(), process.getProcessCode(), normalizedItem)
                .orElseGet(() -> {
                    captureStandardsIfAbsent(lot, process);
                    return snapshotRepository
                            .findByLot_LotNoAndProcess_ProcessCodeAndInspectionItem(
                                    lot.getLotNo(), process.getProcessCode(), normalizedItem)
                            .orElseThrow(() -> new CustomException(
                                    ErrorCode.INSPECTION_STANDARD_NOT_FOUND,
                                    "LOT 시작 시점에 등록되지 않은 검사 항목입니다."));
                });
    }

    public long snapshotCount(Lot lot, Process process) {
        return snapshotRepository.countByLot_LotNoAndProcess_ProcessCode(
                lot.getLotNo(), process.getProcessCode());
    }

    private void ensureDefaultStandards(Process process) {
        if (!INSPECTION_PROCESS_CODE.equals(process.getProcessCode())
                || standardRepository.countByProcess_ProcessCode(process.getProcessCode()) > 0) {
            return;
        }
        standardRepository.save(InspectionStandard.builder()
                .process(process)
                .inspectionItem("OPERATION_VOLTAGE")
                .itemName("동작 전압")
                .unit("V")
                .lowerLimit(new BigDecimal("10.000"))
                .upperLimit(new BigDecimal("14.000"))
                .useYn("Y")
                .standardVersion(1)
                .build());
        standardRepository.save(InspectionStandard.builder()
                .process(process)
                .inspectionItem("COIL_RESISTANCE")
                .itemName("코일 저항")
                .unit("OHM")
                .lowerLimit(new BigDecimal("80.000"))
                .upperLimit(new BigDecimal("120.000"))
                .useYn("Y")
                .standardVersion(1)
                .build());
        standardRepository.save(InspectionStandard.builder()
                .process(process)
                .inspectionItem("CONTACT_RESISTANCE")
                .itemName("접촉 저항")
                .unit("mOHM")
                .lowerLimit(new BigDecimal("0.000"))
                .upperLimit(new BigDecimal("50.000"))
                .useYn("Y")
                .standardVersion(1)
                .build());
    }

    private Process inspectionProcess(String processCode) {
        if (!INSPECTION_PROCESS_CODE.equals(processCode)) {
            throw new CustomException(ErrorCode.INVALID_PROCESS_ORDER,
                    "검사 기준은 OP70 공정에만 등록할 수 있습니다.");
        }
        return processRepository.findById(processCode)
                .filter(process -> "Y".equalsIgnoreCase(process.getUseYn()))
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
    }

    private String normalizeProcessCode(String processCode) {
        return processCode == null ? "" : processCode.trim().toUpperCase();
    }

    private String normalizeInspectionItem(String inspectionItem) {
        return inspectionItem == null ? "" : inspectionItem.trim().toUpperCase();
    }

    private void validateLimits(BigDecimal lower, BigDecimal upper) {
        if (lower == null && upper == null) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_LIMIT,
                    "하한값 또는 상한값 중 하나 이상이 필요합니다.");
        }
        if (lower != null && upper != null && lower.compareTo(upper) > 0) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_LIMIT);
        }
    }

    private String normalizeUseYn(String useYn) {
        return useYn == null || useYn.isBlank() ? "Y" : useYn.trim().toUpperCase();
    }

    private InspectionStandardResponseDto toResponse(InspectionStandard standard) {
        return InspectionStandardResponseDto.builder()
                .standardId(standard.getStandardId())
                .processCode(standard.getProcess().getProcessCode())
                .processName(standard.getProcess().getProcessName())
                .inspectionItem(standard.getInspectionItem())
                .itemName(standard.getItemName())
                .unit(standard.getUnit())
                .lowerLimit(standard.getLowerLimit())
                .upperLimit(standard.getUpperLimit())
                .useYn(standard.getUseYn())
                .standardVersion(standard.getStandardVersion())
                .createdAt(standard.getCreatedAt())
                .updatedAt(standard.getUpdatedAt())
                .build();
    }
}
