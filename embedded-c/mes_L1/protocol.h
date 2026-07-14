#ifndef EV_RELAY_L1_PROTOCOL_H
#define EV_RELAY_L1_PROTOCOL_H

#include <stddef.h>
#include <stdint.h>

#define L1_PROTOCOL_VERSION "V1"
#define L1_PROTOCOL_MAX_MESSAGE_SIZE 1024

#define L1_MACHINE_ID_CAPACITY 51
#define L1_PROCESS_CODE_CAPACITY 31
#define L1_LOT_NO_CAPACITY 51
#define L1_CODE_CAPACITY 51
#define L1_INSPECTION_ITEM_CAPACITY 101
#define L1_UNIT_CAPACITY 21
#define L1_MESSAGE_CAPACITY 256

typedef enum {
    L1_PROTOCOL_OK = 0,
    L1_PROTOCOL_NULL_ARGUMENT,
    L1_PROTOCOL_EMPTY_MESSAGE,
    L1_PROTOCOL_MESSAGE_TOO_LONG,
    L1_PROTOCOL_INVALID_UTF8,
    L1_PROTOCOL_MISSING_LINE_FEED,
    L1_PROTOCOL_INVALID_LINE_ENDING,
    L1_PROTOCOL_INVALID_FIELD_FORMAT,
    L1_PROTOCOL_UNSUPPORTED_VERSION,
    L1_PROTOCOL_UNEXPECTED_EVENT,
    L1_PROTOCOL_FIELD_COUNT_MISMATCH,
    L1_PROTOCOL_FIELD_TOO_LONG,
    L1_PROTOCOL_UNKNOWN_MACHINE,
    L1_PROTOCOL_PROCESS_MISMATCH,
    L1_PROTOCOL_INVALID_NUMBER,
    L1_PROTOCOL_OUT_OF_RANGE,
    L1_PROTOCOL_INVALID_VALUE,
    L1_PROTOCOL_BUFFER_TOO_SMALL
} L1ProtocolResult;

typedef enum {
    L1_COMMAND_UNKNOWN = 0,
    L1_COMMAND_START,
    L1_COMMAND_STOP,
    L1_COMMAND_RESUME
} L1CommandType;

typedef enum {
    L1_PRODUCTION_STATUS_UNKNOWN = 0,
    L1_PRODUCTION_COMPLETED,
    L1_PRODUCTION_FAILED
} L1ProductionStatus;

typedef enum {
    L1_INSPECTION_RESULT_UNKNOWN = 0,
    L1_INSPECTION_OK,
    L1_INSPECTION_NG
} L1InspectionResult;

typedef enum {
    L1_ALARM_LEVEL_UNKNOWN = 0,
    L1_ALARM_WARNING,
    L1_ALARM_ERROR
} L1AlarmLevel;

typedef enum {
    L1_MACHINE_STATUS_UNKNOWN = 0,
    L1_MACHINE_IDLE,
    L1_MACHINE_RUNNING,
    L1_MACHINE_ERROR,
    L1_MACHINE_STOPPED
} L1MachineStatus;

typedef enum {
    L1_ACK_STATUS_UNKNOWN = 0,
    L1_ACK_ACCEPTED,
    L1_ACK_REJECTED
} L1CommandAckStatus;

typedef struct {
    char machine_id[L1_MACHINE_ID_CAPACITY];
    char process_code[L1_PROCESS_CODE_CAPACITY];
    char lot_no[L1_LOT_NO_CAPACITY];
    int input_qty;
    int ok_qty;
    int ng_qty;
    L1ProductionStatus status;
} L1ProductionEvent;

typedef struct {
    char machine_id[L1_MACHINE_ID_CAPACITY];
    char process_code[L1_PROCESS_CODE_CAPACITY];
    char lot_no[L1_LOT_NO_CAPACITY];
    char item[L1_INSPECTION_ITEM_CAPACITY];
    double value;
    char unit[L1_UNIT_CAPACITY];
    int has_lower_limit;
    double lower_limit;
    int has_upper_limit;
    double upper_limit;
    L1InspectionResult result;
} L1InspectionEvent;

typedef struct {
    char machine_id[L1_MACHINE_ID_CAPACITY];
    char process_code[L1_PROCESS_CODE_CAPACITY];
    char lot_no[L1_LOT_NO_CAPACITY];
    char defect_code[L1_CODE_CAPACITY];
    int defect_qty;
    char message[L1_MESSAGE_CAPACITY];
} L1DefectEvent;

typedef struct {
    char machine_id[L1_MACHINE_ID_CAPACITY];
    char alarm_code[L1_CODE_CAPACITY];
    L1AlarmLevel alarm_level;
    char message[L1_MESSAGE_CAPACITY];
} L1AlarmEvent;

typedef struct {
    char machine_id[L1_MACHINE_ID_CAPACITY];
    L1MachineStatus status;
    char lot_no[L1_LOT_NO_CAPACITY];
    char process_code[L1_PROCESS_CODE_CAPACITY];
    char message[L1_MESSAGE_CAPACITY];
} L1MachineStatusEvent;

typedef struct {
    char machine_id[L1_MACHINE_ID_CAPACITY];
    int64_t command_id;
    L1CommandAckStatus status;
    char message[L1_MESSAGE_CAPACITY];
} L1CommandAckEvent;

typedef struct {
    int64_t command_id;
    L1CommandType type;
    char machine_id[L1_MACHINE_ID_CAPACITY];
    char process_code[L1_PROCESS_CODE_CAPACITY];
    char lot_no[L1_LOT_NO_CAPACITY];
    int input_qty;
} L1Command;

const char *l1_protocol_result_name(L1ProtocolResult result);
const char *l1_command_type_name(L1CommandType type);

L1ProtocolResult l1_protocol_parse_command(const char *line,
                                           L1Command *out_command);

L1ProtocolResult l1_protocol_build_hello(char *out_buffer,
                                         size_t buffer_size,
                                         const char *machine_id,
                                         size_t *out_length);
L1ProtocolResult l1_protocol_build_heartbeat(char *out_buffer,
                                             size_t buffer_size,
                                             const char *machine_id,
                                             size_t *out_length);
L1ProtocolResult l1_protocol_build_production(
    char *out_buffer,
    size_t buffer_size,
    const L1ProductionEvent *event,
    size_t *out_length);
L1ProtocolResult l1_protocol_build_inspection(
    char *out_buffer,
    size_t buffer_size,
    const L1InspectionEvent *event,
    size_t *out_length);
L1ProtocolResult l1_protocol_build_defect(char *out_buffer,
                                          size_t buffer_size,
                                          const L1DefectEvent *event,
                                          size_t *out_length);
L1ProtocolResult l1_protocol_build_alarm(char *out_buffer,
                                         size_t buffer_size,
                                         const L1AlarmEvent *event,
                                         size_t *out_length);
L1ProtocolResult l1_protocol_build_machine_status(
    char *out_buffer,
    size_t buffer_size,
    const L1MachineStatusEvent *event,
    size_t *out_length);
L1ProtocolResult l1_protocol_build_command_ack(
    char *out_buffer,
    size_t buffer_size,
    const L1CommandAckEvent *event,
    size_t *out_length);

#endif
