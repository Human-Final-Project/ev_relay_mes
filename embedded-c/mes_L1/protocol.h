#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <stddef.h> // size_t 정의를 위해 추가

// 4.1 공정 및 설비 코드 정의
#define PROC_COIL_WINDING   "OP20"
#define PROC_WELDING        "OP30"
#define PROC_ASSEMBLY       "OP40_OP50"
#define PROC_SEALING        "OP60"
#define PROC_INSPECTION     "OP70"
#define PROC_PACKING        "OP80"

#define MACH_COIL_WINDING   "EQ-WIND-01"
#define MACH_WELDING        "EQ-WELD-01"
#define MACH_ASSEMBLY       "EQ-ASSY-01"
#define MACH_SEALING        "EQ-SEAL-01"
#define MACH_INSPECTION     "EQ-TEST-01"
#define MACH_PACKING        "EQ-PACK-01"

// 4.2 상태 코드 정의
#define STATUS_IDLE         "IDLE"
#define STATUS_RUNNING      "RUNNING"
#define STATUS_ERROR        "ERROR"
#define STATUS_STOPPED      "STOPPED"

#define PROD_COMPLETED      "COMPLETED"
#define PROD_FAILED         "FAILED"

#define RES_OK              "OK"
#define RES_NG              "NG"

// 5.6 알람 레벨 정의
#define ALARM_LVL_WARNING   "WARNING"
#define ALARM_LVL_ERROR     "ERROR"

// 5.5 및 5.6 표준 결함/알람 코드 수정 및 추가
#define DEFECT_COIL_SHORT    "COIL_SHORT_NG"     // 용접 대신 코일 단락 불량 추가
#define DEFECT_WELD_STRENGTH "WELD_STRENGTH_NG" // (이건 용접 장비용)

#define ALARM_MOTOR_OVERLOAD "MOTOR_OVERLOAD"   // 모터 과부하는 권선기에도 적합함

// 메시지 빌더 함수 원형
int build_hello_msg(char *out_buf, size_t buf_size, const char *mach_id);
int build_heartbeat_msg(char *out_buf, size_t buf_size, const char *mach_id);
int build_production_msg(char *out_buf, size_t buf_size, const char *mach_id, const char *proc_code, const char *lot_no, int ok_qty, int ng_qty, const char *status);

// ★ [누락 항목 추가] 설비 상태 메시지 빌더 원형
int build_status_msg(char *out_buf, size_t buf_size, const char *mach_id, const char *status, const char *lot_no, const char *proc_code, const char *msg);

int build_defect_msg(char *out_buf, size_t buf_size, const char *mach_id, const char *proc_code, const char *lot_no, const char *defect_code, int defect_qty);
int build_alarm_msg(char *out_buf, size_t buf_size, const char *mach_id, const char *alarm_code, const char *alarm_level);

#endif // PROTOCOL_H