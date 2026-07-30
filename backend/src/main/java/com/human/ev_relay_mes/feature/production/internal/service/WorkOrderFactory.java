package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import com.human.ev_relay_mes.feature.production.internal.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class WorkOrderFactory {

    private final WorkOrderRepository workOrderRepository;

    WorkOrder create(
            Item item,
            int targetQty,
            LocalDateTime plannedStartAt,
            LocalDateTime plannedEndAt,
            Member creator) {
        return WorkOrder.builder()
                .orderNo(generateOrderNo())
                .item(item)
                .targetQty(targetQty)
                .plannedStartAt(plannedStartAt)
                .plannedEndAt(plannedEndAt)
                .createdBy(creator)
                .build();
    }

    private String generateOrderNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String orderNo;
        do {
            orderNo = "WO-" + date + "-" + UUID.randomUUID().toString()
                    .substring(0, 8).toUpperCase();
        } while (workOrderRepository.existsByOrderNo(orderNo));
        return orderNo;
    }
}
