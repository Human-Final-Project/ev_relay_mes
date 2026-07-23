package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Inspection;
import com.human.ev_relay_mes.Entity.InspectionUnitResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InspectionUnitResultRepository extends JpaRepository<InspectionUnitResult, Long> {
    Optional<InspectionUnitResult> findByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
            String lotNo, String processCode, Integer unitSeq);

    long countByLot_LotNoAndProcess_ProcessCode(String lotNo, String processCode);

    long countByLot_LotNoAndProcess_ProcessCodeAndEvaluationStatus(
            String lotNo, String processCode,
            InspectionUnitResult.EvaluationStatus evaluationStatus);

    long countByLot_LotNoAndProcess_ProcessCodeAndResult(
            String lotNo, String processCode, Inspection.Result result);

    List<InspectionUnitResult> findByLot_LotNoAndProcess_ProcessCodeOrderByUnitSeqAsc(
            String lotNo, String processCode);
}
