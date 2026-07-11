#ifndef PROTOCOL_H
#define PROTOCOL_H

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

// 5.5 및 5.6 표준 결함/알람 코드
#define DEFECT_WELD_STRENGTH "WELD_STRENGTH_NG"
#define ALARM_MOTOR_OVERLOAD "MOTOR_OVERLOAD"

// 메시지 빌더 함수 원형
int build_hello_msg(char *out_buf, size_t buf_size, const char *mach_id);
int build_heartbeat_msg(char *out_buf, size_t buf_size, const char *mach_id);
int build_production_msg(char *out_buf, size_t buf_size, const char *mach_id, const char *proc_code, const char *lot_no, int ok_qty, int ng_qty, const char *status);
int build_status_msg(char *out_buf, size_t buf_size, const char *mach_id, const char *status, const char *lot_no, const char *proc_code, const char *msg);

#endif // PROTOCOL_H