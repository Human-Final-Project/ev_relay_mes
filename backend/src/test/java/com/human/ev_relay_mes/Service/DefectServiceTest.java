package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.DefectHistoryCreateRequestDto;
import com.human.ev_relay_mes.Entity.DefectCode;
import com.human.ev_relay_mes.Entity.DefectHistory;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Repository.DefectCodeRepository;
import com.human.ev_relay_mes.Repository.DefectHistoryRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefectServiceTest {

    @Mock DefectHistoryRepository defectHistoryRepository;
    @Mock DefectCodeRepository defectCodeRepository;
    @Mock MachineRepository machineRepository;
    @Mock ProcessRepository processRepository;
    @Mock MemberRepository memberRepository;
    @Mock LotRepository lotRepository;

    @InjectMocks DefectService defectService;

    @Test
    void returnsExistingDefectForDuplicateEventId() {
        Process process = Process.builder()
                .processCode("OP20").processName("winding").processOrder(20).build();
        Machine machine = Machine.builder()
                .machineId("EQ-WIND-01").machineName("winder")
                .machineType("WINDER").process(process).build();
        Lot lot = Lot.builder().lotNo("LOT-001").build();
        DefectCode defectCode = DefectCode.builder()
                .defectCode("WIRE_BREAK").defectName("wire break").process(process).build();
        DefectHistory existing = DefectHistory.builder()
                .defectHistoryId(14L)
                .eventId("defect-001")
                .lot(lot)
                .machine(machine)
                .process(process)
                .defectCode(defectCode)
                .defectQty(1)
                .build();
        DefectHistoryCreateRequestDto dto = new DefectHistoryCreateRequestDto();
        dto.setEventId("defect-001");
        when(defectHistoryRepository.findByEventId("defect-001"))
                .thenReturn(Optional.of(existing));

        var response = defectService.createDefect(dto);

        assertThat(response.getDefectHistoryId()).isEqualTo(14L);
        verifyNoInteractions(defectCodeRepository, machineRepository,
                processRepository, memberRepository, lotRepository);
    }
}
