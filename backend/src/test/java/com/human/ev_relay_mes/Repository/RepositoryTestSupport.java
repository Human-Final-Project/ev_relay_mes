package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.WorkOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

abstract class RepositoryTestSupport {

    @Autowired
    protected TestEntityManager entityManager;

    protected Item item(String code, Item.ItemType type) {
        return entityManager.persistAndFlush(Item.builder()
                .itemCode(code)
                .itemName(code + " name")
                .itemType(type)
                .build());
    }

    protected Process process(String code, int order) {
        return entityManager.persistAndFlush(Process.builder()
                .processCode(code)
                .processName(code + " process")
                .processOrder(order)
                .build());
    }

    protected Member member(String loginId, Member.Role role, Member.Status status) {
        return entityManager.persistAndFlush(Member.builder()
                .loginId(loginId)
                .password("encoded-password")
                .memberName(loginId + " member")
                .role(role)
                .status(status)
                .build());
    }

    protected Machine machine(String id, Process process, Machine.Status status, String useYn) {
        return entityManager.persistAndFlush(Machine.builder()
                .machineId(id)
                .machineName(id + " machine")
                .machineType("TESTER")
                .process(process)
                .status(status)
                .useYn(useYn)
                .build());
    }

    protected WorkOrder workOrder(String orderNo, Item item, WorkOrder.Status status, int targetQty) {
        return entityManager.persistAndFlush(WorkOrder.builder()
                .orderNo(orderNo)
                .item(item)
                .targetQty(targetQty)
                .status(status)
                .build());
    }

    protected Lot lot(
            String lotNo,
            WorkOrder workOrder,
            Item item,
            Process process,
            Lot.Status status,
            int inputQty) {
        return entityManager.persistAndFlush(Lot.builder()
                .lotNo(lotNo)
                .workOrder(workOrder)
                .item(item)
                .currentProcess(process)
                .inputQty(inputQty)
                .status(status)
                .build());
    }
}
