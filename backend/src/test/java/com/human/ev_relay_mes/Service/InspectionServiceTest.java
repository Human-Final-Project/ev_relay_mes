package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.Entity.Inspection;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Repository.InspectionRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
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
class InspectionServiceTest {

    @Mock InspectionRepository inspectionRepository;
    @Mock MachineRepository machineRepository;
    @Mock ProcessRepository processRepository;
    @Mock LotRepository lotRepository;

    @InjectMocks InspectionService inspectionService;

    @Test
    void returnsExistingInspectionForDuplicateEventId() {
        Process process = Process.builder()
                .processCode("OP70").processName("test").processOrder(70).build();
        Machine machine = Machine.builder()
                .machineId("EQ-TEST-01").machineName("tester")
                .machineType("TESTER").process(process).build();
        Lot lot = Lot.builder().lotNo("LOT-001").build();
        Inspection existing = Inspection.builder()
                .inspectionId(13L)
                .eventId("inspection-001")
                .lot(lot)
                .machine(machine)
                .process(process)
                .inspectionItem("resistance")
                .result(Inspection.Result.OK)
                .build();
        InspectionResultReceiveRequestDto dto = new InspectionResultReceiveRequestDto();
        dto.setEventId("inspection-001");
        when(inspectionRepository.findByEventId("inspection-001"))
                .thenReturn(Optional.of(existing));

        var response = inspectionService.saveResult(dto);

        assertThat(response.getInspectionId()).isEqualTo(13L);
        verifyNoInteractions(machineRepository, processRepository, lotRepository);
    }
}
