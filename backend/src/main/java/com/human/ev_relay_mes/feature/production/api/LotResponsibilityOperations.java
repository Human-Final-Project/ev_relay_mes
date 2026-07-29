package com.human.ev_relay_mes.feature.production.api;

import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;

import java.util.List;

public interface LotResponsibilityOperations {

    void captureIfAbsent(Lot lot, Process process, Machine machine);

    List<LotProcessResponsibleResponseDto> getByLotNo(String lotNo);
}
