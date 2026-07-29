package com.human.ev_relay_mes.feature.masterdata.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.masterdata.api.AlarmCode;
import com.human.ev_relay_mes.feature.masterdata.api.Bom;
import com.human.ev_relay_mes.feature.masterdata.api.DefectCode;
import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.masterdata.internal.repository.AlarmCodeRepository;
import com.human.ev_relay_mes.feature.masterdata.internal.repository.BomRepository;
import com.human.ev_relay_mes.feature.masterdata.internal.repository.DefectCodeRepository;
import com.human.ev_relay_mes.feature.masterdata.internal.repository.ItemRepository;
import com.human.ev_relay_mes.feature.masterdata.internal.repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterDataLookupService implements MasterDataLookup {

    private final ItemRepository itemRepository;
    private final ProcessRepository processRepository;
    private final BomRepository bomRepository;
    private final AlarmCodeRepository alarmCodeRepository;
    private final DefectCodeRepository defectCodeRepository;

    @Override
    public Item getRequiredItem(String itemCode) {
        return itemRepository.findById(itemCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    }

    @Override
    public Process getRequiredProcess(String processCode) {
        return processRepository.findById(processCode)
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
    }

    @Override
    public Optional<Process> findProcess(String processCode) {
        return processRepository.findById(processCode);
    }

    @Override
    public Process getFirstProcess() {
        return processRepository.findFirstByOrderByProcessOrderAsc()
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
    }

    @Override
    public Optional<Process> findNextProcess(Integer processOrder) {
        return processRepository
                .findFirstByProcessOrderGreaterThanOrderByProcessOrderAsc(processOrder);
    }

    @Override
    public Optional<Process> findPreviousProcess(Integer processOrder) {
        return processRepository
                .findFirstByProcessOrderLessThanOrderByProcessOrderDesc(processOrder);
    }

    @Override
    public List<Bom> getActiveBom(String parentItemCode) {
        return bomRepository.findByParentItem_ItemCodeAndUseYnOrderByChildItem_ItemCodeAsc(
                parentItemCode, "Y");
    }

    @Override
    public AlarmCode getRequiredAlarmCode(String alarmCode) {
        return alarmCodeRepository.findById(alarmCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ALARM_CODE_NOT_FOUND));
    }

    @Override
    public DefectCode getRequiredDefectCode(String defectCode) {
        return defectCodeRepository.findById(defectCode)
                .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_CODE_NOT_FOUND));
    }
}
