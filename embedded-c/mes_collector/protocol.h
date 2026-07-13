#ifndef MES_COLLECTOR_PROTOCOL_H
#define MES_COLLECTOR_PROTOCOL_H

#include <stddef.h>
#include <stdint.h>

#define PROTOCOL_VERSION "V1"

/* Capacities include the trailing null character and match the DB schema. */
#define PROTOCOL_MACHINE_ID_CAPACITY 51
#define PROTOCOL_PROCESS_CODE_CAPACITY 31
#define PROTOCOL_LOT_NO_CAPACITY 51
#define PROTOCOL_CODE_CAPACITY 51
#define PROTOCOL_INSPECTION_ITEM_CAPACITY 101
#define PROTOCOL_UNIT_CAPACITY 21
#define PROTOCOL_STATUS_CAPACITY 21
#define PROTOCOL_MESSAGE_CAPACITY 256

typedef enum {
    PROTOCOL_EVENT_UNKNOWN = 0,
    PROTOCOL_EVENT_HELLO,
    PROTOCOL_EVENT_HEARTBEAT,
    PROTOCOL_EVENT_PRODUCTION,
    PROTOCOL_EVENT_INSPECTION,
    PROTOCOL_EVENT_DEFECT,
    PROTOCOL_EVENT_ALARM,
    PROTOCOL_EVENT_MACHINE_STATUS,
    PROTOCOL_EVENT_COMMAND,
    PROTOCOL_EVENT_COMMAND_ACK
} ProtocolEventType;

typedef enum {
    PROTOCOL_COMMAND_UNKNOWN = 0,
    PROTOCOL_COMMAND_START,
    PROTOCOL_COMMAND_STOP,
    PROTOCOL_COMMAND_RESUME
} ProtocolCommandType;

typedef enum {
    PROTOCOL_RESULT_OK = 0,
    PROTOCOL_RESULT_NULL_ARGUMENT,
    PROTOCOL_RESULT_EMPTY_MESSAGE,
    PROTOCOL_RESULT_MESSAGE_TOO_LONG,
    PROTOCOL_RESULT_INVALID_UTF8,
    PROTOCOL_RESULT_MISSING_LINE_FEED,
    PROTOCOL_RESULT_INVALID_LINE_ENDING,
    PROTOCOL_RESULT_INVALID_FIELD_FORMAT,
    PROTOCOL_RESULT_UNSUPPORTED_VERSION,
    PROTOCOL_RESULT_UNKNOWN_EVENT,
    PROTOCOL_RESULT_UNEXPECTED_EVENT,
    PROTOCOL_RESULT_FIELD_COUNT_MISMATCH,
    PROTOCOL_RESULT_FIELD_TOO_LONG,
    PROTOCOL_RESULT_UNKNOWN_MACHINE,
    PROTOCOL_RESULT_PROCESS_MISMATCH,
    PROTOCOL_RESULT_INVALID_NUMBER,
    PROTOCOL_RESULT_OUT_OF_RANGE,
    PROTOCOL_RESULT_INVALID_VALUE,
    PROTOCOL_RESULT_BUFFER_TOO_SMALL
} ProtocolResult;

typedef struct {
    char machine_id[PROTOCOL_MACHINE_ID_CAPACITY];
} ProtocolConnectionEvent;

typedef struct {
    char machine_id[PROTOCOL_MACHINE_ID_CAPACITY];
    char process_code[PROTOCOL_PROCESS_CODE_CAPACITY];
    char lot_no[PROTOCOL_LOT_NO_CAPACITY];
    int input_qty;
    int ok_qty;
    int ng_qty;
    char status[PROTOCOL_STATUS_CAPACITY];
} ProtocolProductionEvent;

typedef struct {
    char machine_id[PROTOCOL_MACHINE_ID_CAPACITY];
    char process_code[PROTOCOL_PROCESS_CODE_CAPACITY];
    char lot_no[PROTOCOL_LOT_NO_CAPACITY];
    char item[PROTOCOL_INSPECTION_ITEM_CAPACITY];
    double value;
    char unit[PROTOCOL_UNIT_CAPACITY];
    int has_lower_limit;
    double lower_limit;
    int has_upper_limit;
    double upper_limit;
    char result[PROTOCOL_STATUS_CAPACITY];
} ProtocolInspectionEvent;

typedef struct {
    char machine_id[PROTOCOL_MACHINE_ID_CAPACITY];
    char process_code[PROTOCOL_PROCESS_CODE_CAPACITY];
    char lot_no[PROTOCOL_LOT_NO_CAPACITY];
    char defect_code[PROTOCOL_CODE_CAPACITY];
    int defect_qty;
    char message[PROTOCOL_MESSAGE_CAPACITY];
} ProtocolDefectEvent;

typedef struct {
    char machine_id[PROTOCOL_MACHINE_ID_CAPACITY];
    char alarm_code[PROTOCOL_CODE_CAPACITY];
    char alarm_level[PROTOCOL_STATUS_CAPACITY];
    char message[PROTOCOL_MESSAGE_CAPACITY];
} ProtocolAlarmEvent;

typedef struct {
    char machine_id[PROTOCOL_MACHINE_ID_CAPACITY];
    char status[PROTOCOL_STATUS_CAPACITY];
    char lot_no[PROTOCOL_LOT_NO_CAPACITY];
    char process_code[PROTOCOL_PROCESS_CODE_CAPACITY];
    char message[PROTOCOL_MESSAGE_CAPACITY];
} ProtocolMachineStatusEvent;

typedef struct {
    char machine_id[PROTOCOL_MACHINE_ID_CAPACITY];
    int64_t command_id;
    char ack_status[PROTOCOL_STATUS_CAPACITY];
    char message[PROTOCOL_MESSAGE_CAPACITY];
} ProtocolCommandAckEvent;

typedef struct {
    ProtocolEventType type;
    union {
        ProtocolConnectionEvent connection;
        ProtocolProductionEvent production;
        ProtocolInspectionEvent inspection;
        ProtocolDefectEvent defect;
        ProtocolAlarmEvent alarm;
        ProtocolMachineStatusEvent machine_status;
        ProtocolCommandAckEvent command_ack;
    } data;
} ProtocolMessage;

typedef struct {
    int64_t command_id;
    ProtocolCommandType type;
    char machine_id[PROTOCOL_MACHINE_ID_CAPACITY];
    char process_code[PROTOCOL_PROCESS_CODE_CAPACITY];
    char lot_no[PROTOCOL_LOT_NO_CAPACITY];
    int input_qty;
} ProtocolCommand;

const char *protocol_event_type_name(ProtocolEventType type);
const char *protocol_command_type_name(ProtocolCommandType type);
const char *protocol_result_name(ProtocolResult result);

/* Parses one complete LF-terminated L1 message. */
ProtocolResult protocol_parse_message(const char *line, ProtocolMessage *out_message);

/* Builds one LF-terminated L2 COMMAND message. */
ProtocolResult protocol_build_command(char *out_buffer,
                                      size_t buffer_size,
                                      const ProtocolCommand *command,
                                      size_t *out_length);

/* Returns non-zero only for one of the six configured machine/process pairs. */
int protocol_machine_matches_process(const char *machine_id, const char *process_code);

#endif
