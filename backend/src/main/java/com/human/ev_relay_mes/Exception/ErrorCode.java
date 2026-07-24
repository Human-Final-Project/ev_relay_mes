package com.human.ev_relay_mes.Exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C002", "요청 값의 타입이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C003", "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "요청한 리소스를 찾을 수 없습니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "C005", "이미 존재하는 리소스입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 내부 오류가 발생했습니다."),

    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "C006", "다른 데이터에서 사용 중이어서 처리할 수 없습니다."),

    // Auth / Member
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "M001", "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "M002", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "M003", "접근 권한이 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "사용자를 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "M005", "이미 사용 중인 로그인 ID입니다."),
    MEMBER_LOCKED(HttpStatus.FORBIDDEN, "M006", "잠긴 사용자 계정입니다."),
    MEMBER_RETIRED(HttpStatus.FORBIDDEN, "M007", "퇴사 또는 비활성 처리된 사용자 계정입니다."),
    INVALID_MEMBER_ROLE(HttpStatus.BAD_REQUEST, "M008", "사용자 권한 값이 올바르지 않습니다."),
    INVALID_MEMBER_STATUS(HttpStatus.BAD_REQUEST, "M009", "사용자 상태 값이 올바르지 않습니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "M010", "현재 비밀번호가 일치하지 않습니다."),
    NEW_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "M011", "새 비밀번호와 비밀번호 확인이 일치하지 않습니다."),

    // Item
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "I001", "품목을 찾을 수 없습니다."),
    DUPLICATE_ITEM_CODE(HttpStatus.CONFLICT, "I002", "이미 존재하는 품목 코드입니다."),
    INVALID_ITEM_TYPE(HttpStatus.BAD_REQUEST, "I003", "품목 유형 값이 올바르지 않습니다."),
    ITEM_NOT_USABLE(HttpStatus.BAD_REQUEST, "I004", "사용할 수 없는 품목입니다."),

    // Process
    PROCESS_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "공정을 찾을 수 없습니다."),
    DUPLICATE_PROCESS_CODE(HttpStatus.CONFLICT, "P002", "이미 존재하는 공정 코드입니다."),
    PROCESS_NOT_USABLE(HttpStatus.BAD_REQUEST, "P003", "사용할 수 없는 공정입니다."),
    INVALID_PROCESS_ORDER(HttpStatus.BAD_REQUEST, "P004", "공정 순서가 올바르지 않습니다."),

    // BOM
    BOM_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "BOM 정보를 찾을 수 없습니다."),
    DUPLICATE_BOM(HttpStatus.CONFLICT, "B002", "이미 등록된 BOM 구성입니다."),
    INVALID_BOM_QUANTITY(HttpStatus.BAD_REQUEST, "B003", "BOM 소요 수량이 올바르지 않습니다."),
    INVALID_BOM_ITEM_RELATION(HttpStatus.BAD_REQUEST, "B004", "BOM 상위/하위 품목 관계가 올바르지 않습니다."),
    BOM_NOT_USABLE(HttpStatus.BAD_REQUEST, "B005", "사용할 수 없는 BOM입니다."),

    // Material lot
    MATERIAL_LOT_NOT_FOUND(HttpStatus.NOT_FOUND, "ML001", "자재 LOT를 찾을 수 없습니다."),
    DUPLICATE_MATERIAL_LOT_NO(HttpStatus.CONFLICT, "ML002", "이미 존재하는 자재 LOT 번호입니다."),
    INVALID_MATERIAL_LOT_STATUS(HttpStatus.BAD_REQUEST, "ML003", "자재 LOT 상태 값이 올바르지 않습니다."),
    INVALID_MATERIAL_LOT_QUANTITY(HttpStatus.BAD_REQUEST, "ML004", "자재 LOT 수량이 올바르지 않습니다."),
    MATERIAL_LOT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "ML005", "사용 가능한 자재 LOT가 아닙니다."),
    INSUFFICIENT_MATERIAL_QUANTITY(HttpStatus.BAD_REQUEST, "ML006", "자재 LOT 잔여 수량이 부족합니다."),

    // Work order
    WORK_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "WO001", "작업지시를 찾을 수 없습니다."),
    DUPLICATE_WORK_ORDER_NO(HttpStatus.CONFLICT, "WO002", "이미 존재하는 작업지시 번호입니다."),
    INVALID_WORK_ORDER_STATUS(HttpStatus.BAD_REQUEST, "WO003", "작업지시 상태 값이 올바르지 않습니다."),
    INVALID_WORK_ORDER_QUANTITY(HttpStatus.BAD_REQUEST, "WO004", "작업지시 수량이 올바르지 않습니다."),
    WORK_ORDER_ALREADY_STARTED(HttpStatus.CONFLICT, "WO005", "이미 시작된 작업지시입니다."),
    WORK_ORDER_ALREADY_COMPLETED(HttpStatus.CONFLICT, "WO006", "이미 완료된 작업지시입니다."),
    WORK_ORDER_CANCELED(HttpStatus.CONFLICT, "WO007", "취소된 작업지시입니다."),
    WORK_ORDER_TARGET_NOT_MET(HttpStatus.CONFLICT, "WO008", "작업지시 목표 양품 수량이 충족되지 않았습니다."),
    ANOTHER_WORK_ORDER_IN_PROGRESS(HttpStatus.CONFLICT, "WO009", "다른 작업지시가 현재 생산 중입니다."),
    SUPPLEMENT_NOT_REQUIRED(HttpStatus.CONFLICT, "WO010", "추가 생산이 필요하지 않습니다."),
    SUPPLEMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "WO011", "진행 중인 추가 생산 LOT가 이미 존재합니다."),
    EARLIER_WORK_ORDER_NOT_COMPLETED(HttpStatus.CONFLICT, "WO012", "먼저 확정된 작업지시가 아직 완료되지 않았습니다."),
    SUPPLEMENT_LIMIT_REACHED(HttpStatus.CONFLICT, "WO013", "자동 보충 생산 횟수 제한에 도달했습니다."),

    // Lot
    LOT_NOT_FOUND(HttpStatus.NOT_FOUND, "L001", "생산 LOT를 찾을 수 없습니다."),
    DUPLICATE_LOT_NO(HttpStatus.CONFLICT, "L002", "이미 존재하는 생산 LOT 번호입니다."),
    INVALID_LOT_STATUS(HttpStatus.BAD_REQUEST, "L003", "생산 LOT 상태 값이 올바르지 않습니다."),
    INVALID_LOT_QUANTITY(HttpStatus.BAD_REQUEST, "L004", "생산 LOT 수량이 올바르지 않습니다."),
    LOT_ALREADY_STARTED(HttpStatus.CONFLICT, "L005", "이미 시작된 생산 LOT입니다."),
    LOT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "L006", "이미 완료된 생산 LOT입니다."),
    LOT_ON_HOLD(HttpStatus.CONFLICT, "L007", "보류 중인 생산 LOT입니다."),
    INVALID_LOT_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "L008", "생산 LOT 상태 변경이 올바르지 않습니다."),
    ANOTHER_LOT_IN_PROGRESS(HttpStatus.CONFLICT, "L009", "다른 생산 LOT가 현재 가동 중입니다."),
    INITIAL_LOT_ALREADY_EXISTS(HttpStatus.CONFLICT, "L010", "초기 생산 LOT가 이미 존재합니다."),

    // Production log
    PRODUCTION_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "PL001", "생산 실적 로그를 찾을 수 없습니다."),
    INVALID_PRODUCTION_QUANTITY(HttpStatus.BAD_REQUEST, "PL002", "생산 실적 수량이 올바르지 않습니다."),
    INVALID_PRODUCTION_STATUS(HttpStatus.BAD_REQUEST, "PL003", "생산 실적 상태 값이 올바르지 않습니다."),
    PRODUCTION_QUANTITY_MISMATCH(HttpStatus.BAD_REQUEST, "PL004", "투입/양품/불량 수량이 일치하지 않습니다."),

    // Inspection
    INSPECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "IN001", "검사 결과를 찾을 수 없습니다."),
    INVALID_INSPECTION_RESULT(HttpStatus.BAD_REQUEST, "IN002", "검사 결과 값이 올바르지 않습니다."),
    INVALID_INSPECTION_LIMIT(HttpStatus.BAD_REQUEST, "IN003", "검사 기준값이 올바르지 않습니다."),
    INVALID_INSPECTION_VALUE(HttpStatus.BAD_REQUEST, "IN004", "검사 측정값이 올바르지 않습니다."),
    INSPECTION_STANDARD_NOT_FOUND(HttpStatus.NOT_FOUND, "IN005", "검사 기준을 찾을 수 없습니다."),
    INSPECTION_STANDARD_NOT_CONFIGURED(HttpStatus.CONFLICT, "IN006", "활성 검사 기준이 설정되어 있지 않습니다."),
    INVALID_INSPECTION_UNIT_SEQ(HttpStatus.BAD_REQUEST, "IN007", "검사 제품 순번이 올바르지 않습니다."),
    DUPLICATE_INSPECTION_MEASUREMENT(HttpStatus.CONFLICT, "IN008", "동일 제품의 검사 항목이 이미 등록되어 있습니다."),

    // Machine
    MACHINE_NOT_FOUND(HttpStatus.NOT_FOUND, "MC001", "설비를 찾을 수 없습니다."),
    DUPLICATE_MACHINE_ID(HttpStatus.CONFLICT, "MC002", "이미 존재하는 설비 ID입니다."),
    INVALID_MACHINE_STATUS(HttpStatus.BAD_REQUEST, "MC003", "설비 상태 값이 올바르지 않습니다."),
    MACHINE_NOT_USABLE(HttpStatus.BAD_REQUEST, "MC004", "사용할 수 없는 설비입니다."),
    MACHINE_NOT_IDLE(HttpStatus.CONFLICT, "MC005", "대기 상태가 아닌 설비입니다."),
    MACHINE_ALREADY_RUNNING(HttpStatus.CONFLICT, "MC006", "이미 가동 중인 설비입니다."),
    MACHINE_STOPPED(HttpStatus.CONFLICT, "MC007", "정지 상태의 설비입니다."),
    MACHINE_ERROR_STATE(HttpStatus.CONFLICT, "MC008", "이상 상태의 설비입니다."),
    INVALID_MACHINE_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "MC009", "설비 상태 변경이 올바르지 않습니다."),

    // Worker / machine assignment
    WORKER_NOT_FOUND(HttpStatus.NOT_FOUND, "WK001", "작업자를 찾을 수 없습니다."),
    WORKER_NO_DUPLICATED(HttpStatus.CONFLICT, "WK002", "이미 등록된 사번입니다."),
    INVALID_WORKER_STATUS(HttpStatus.BAD_REQUEST, "WK003", "작업자 상태 값이 올바르지 않습니다."),
    WORKER_INACTIVE(HttpStatus.CONFLICT, "WK004", "비활성 작업자는 설비에 배치할 수 없습니다."),
    WORKER_ASSIGNED_TO_MACHINE(HttpStatus.CONFLICT, "WK005", "설비에 배치된 작업자입니다."),
    MACHINE_WORKER_ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "WK006", "설비 인원 배치 정보를 찾을 수 없습니다."),

    // Defect
    DEFECT_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "불량 코드를 찾을 수 없습니다."),
    DUPLICATE_DEFECT_CODE(HttpStatus.CONFLICT, "D002", "이미 존재하는 불량 코드입니다."),
    DEFECT_CODE_NOT_USABLE(HttpStatus.BAD_REQUEST, "D003", "사용할 수 없는 불량 코드입니다."),
    DEFECT_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "D004", "불량 이력을 찾을 수 없습니다."),
    INVALID_DEFECT_QUANTITY(HttpStatus.BAD_REQUEST, "D005", "불량 수량이 올바르지 않습니다."),
    DEFECT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "D006", "이미 확인 처리된 불량 이력입니다."),

    // Alarm
    ALARM_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "A001", "알람 코드를 찾을 수 없습니다."),
    DUPLICATE_ALARM_CODE(HttpStatus.CONFLICT, "A002", "이미 존재하는 알람 코드입니다."),
    ALARM_CODE_NOT_USABLE(HttpStatus.BAD_REQUEST, "A003", "사용할 수 없는 알람 코드입니다."),
    MACHINE_ALARM_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "A004", "설비 알람 이력을 찾을 수 없습니다."),
    INVALID_ALARM_LEVEL(HttpStatus.BAD_REQUEST, "A005", "알람 등급 값이 올바르지 않습니다."),
    ALARM_ALREADY_CLEARED(HttpStatus.CONFLICT, "A006", "이미 해제된 알람입니다."),
    ALARM_NOT_CLEARED(HttpStatus.CONFLICT, "A007", "아직 해제되지 않은 알람입니다."),

    // Machine status history
    MACHINE_STATUS_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "MS001", "설비 상태 이력을 찾을 수 없습니다."),
    INVALID_MACHINE_STATUS_HISTORY(HttpStatus.BAD_REQUEST, "MS002", "설비 상태 이력 값이 올바르지 않습니다."),

    // Work command
    WORK_COMMAND_NOT_FOUND(HttpStatus.NOT_FOUND, "WC001", "작업명령을 찾을 수 없습니다."),
    WORK_COMMAND_ALREADY_EXISTS(HttpStatus.CONFLICT, "WC002", "동일 LOT와 공정의 진행 중인 작업명령이 이미 존재합니다."),
    INVALID_WORK_COMMAND_STATUS(HttpStatus.CONFLICT, "WC003", "작업명령 상태가 올바르지 않습니다."),
    PROCESS_MACHINE_NOT_CONFIGURED(HttpStatus.CONFLICT, "WC004", "공정에 사용할 수 있는 설비가 등록되어 있지 않습니다."),
    WORK_COMMAND_MACHINE_MISMATCH(HttpStatus.CONFLICT, "WC005", "작업명령의 설비와 ACK 설비가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
