package com.human.ev_relay_mes.feature.masterdata.api;

import java.util.List;
import java.util.Set;

/**
 * EV Relay 생산 공정의 업무 코드를 한 곳에서 관리한다.
 */
public final class ProcessCodes {

    public static final String WINDING = "OP20";
    public static final String CONTACT_WELDING = "OP30";
    public static final String ASSEMBLY = "OP40_OP50";
    public static final String SEALING = "OP60";
    public static final String FINAL_INSPECTION = "OP70";
    public static final String MARKING_AND_PACKAGING = "OP80";

    public static final Set<String> INITIAL_PARALLEL_PROCESSES =
            Set.of(WINDING, CONTACT_WELDING);
    public static final List<String> MEASUREMENT_PROCESSES =
            List.of(WINDING, CONTACT_WELDING, SEALING, FINAL_INSPECTION);

    private ProcessCodes() {
    }
}
