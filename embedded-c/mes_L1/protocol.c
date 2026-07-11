#include "protocol.h"
#include <stdio.h>

#define VERSION "V1"

int build_hello_msg(char *out_buf, size_t buf_size, const char *mach_id) {
    // V1,HELLO,MACHINE_ID\n
    return snprintf(out_buf, buf_size, "%s,HELLO,%s\n", VERSION, mach_id);
}

int build_heartbeat_msg(char *out_buf, size_t buf_size, const char *mach_id) {
    // V1,HEARTBEAT,MACHINE_ID\n
    return snprintf(out_buf, buf_size, "%s,HEARTBEAT,%s\n", VERSION, mach_id);
}

int build_production_msg(char *out_buf, size_t buf_size, const char *mach_id, const char *proc_code, const char *lot_no, int ok_qty, int ng_qty, const char *status) {
    // 검증 규칙: INPUT_QTY = OK_QTY + NG_QTY
    int input_qty = ok_qty + ng_qty;
    
    // V1,PRODUCTION,MACHINE_ID,PROCESS_CODE,LOT_NO,INPUT_QTY,OK_QTY,NG_QTY,STATUS\n
    return snprintf(out_buf, buf_size, "%s,PRODUCTION,%s,%s,%s,%d,%d,%d,%s\n",
                    VERSION, mach_id, proc_code, lot_no, input_qty, ok_qty, ng_qty, status);
}

int build_status_msg(char *out_buf, size_t buf_size, const char *mach_id, const char *status, const char *lot_no, const char *proc_code, const char *msg) {
    // 값이 없으면 빈 문자열 대신 하이픈 '-' 사용 규칙 적용
    const char *final_lot = (lot_no == NULL || lot_no[0] == '\0') ? "-" : lot_no;
    const char *final_msg = (msg == NULL || msg[0] == '\0') ? "-" : msg;

    // V1,MACHINE_STATUS,MACHINE_ID,STATUS,LOT_NO,PROCESS_CODE,MESSAGE\n
    return snprintf(out_buf, buf_size, "%s,MACHINE_STATUS,%s,%s,%s,%s,%s\n",
                    VERSION, mach_id, status, final_lot, proc_code, final_msg);
}