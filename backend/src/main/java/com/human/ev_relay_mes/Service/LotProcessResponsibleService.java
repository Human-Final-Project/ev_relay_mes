package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Response.LotProcessResponsibleResponseDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.LotProcessResponsible;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Repository.LotProcessResponsibleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotProcessResponsibleService {

    private final LotProcessResponsibleRepository responsibleRepository;
    private final MachineWorkerAssignmentService assignmentService;

    @Transactional
    public void captureIfAbsent(Lot lot, Process process, Machine machine) {
        if (responsibleRepository
                .findByLot_LotNoAndProcess_ProcessCode(
                        lot.getLotNo(),
                        process.getProcessCode())
                .isPresent()) {
            return;
        }
        assignmentService.findResponsible(machine.getMachineId())
                .ifPresent(assignment -> responsibleRepository.save(
                        LotProcessResponsible.builder()
                                .lot(lot)
                                .process(process)
                                .machine(machine)
                                .worker(assignment.getWorker())
                                .build()));
    }

    public List<LotProcessResponsibleResponseDto> getByLotNo(String lotNo) {
        return responsibleRepository.findByLot_LotNoOrderByProcess_ProcessOrderAsc(lotNo)
                .stream()
                .map(LotProcessResponsibleResponseDto::fromEntity)
                .toList();
    }
}
