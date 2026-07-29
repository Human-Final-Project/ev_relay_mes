package com.human.ev_relay_mes.feature.collector.api;

import java.util.List;

public interface CollectorStatusOperations {

    void receiveHeartbeat(List<String> connectedMachineIds, int totalCapacity);

    CollectorStatus getStatus();
}
