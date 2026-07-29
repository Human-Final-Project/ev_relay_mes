package com.human.ev_relay_mes.feature.production.api;

import java.util.List;

/**
 * 다른 기능이 생산 저장소 대신 사용하는 공개 조회 계약입니다.
 */
public interface ProductionData {

    Lot getRequiredLot(String lotNo);

    Lot getRequiredLotForUpdate(String lotNo);

    List<Lot> getAllLots();

    List<WorkOrder> getAllWorkOrders();

    int sumInputQuantity(String lotNo, String processCode);
}
