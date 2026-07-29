package com.human.ev_relay_mes.feature.production.api;

import java.util.List;

public interface WorkOrderOperations {

    WorkOrderResponseDto createWorkOrder(WorkOrderRequestDto dto, Long memberId);

    List<WorkOrderResponseDto> getWorkOrders(String status);

    WorkOrderResponseDto getWorkOrder(Long id);

    WorkOrderResponseDto updateWorkOrder(Long id, WorkOrderRequestDto dto);

    WorkOrderResponseDto releaseAndStart(Long id, Long memberId);

    WorkOrderResponseDto updateStatus(Long id, WorkOrderStatusRequestDto dto);

    void deleteWorkOrder(Long id);
}
