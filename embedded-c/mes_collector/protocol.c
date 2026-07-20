#include "protocol.h"

#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "config.h"

#define PROTOCOL_MAX_FIELDS 11

typedef struct {
    const char *machine_id;
    const char *process_code;
} MachineProcessPair;

static const MachineProcessPair MACHINE_PROCESS_PAIRS[] = {
    {"EQ-WIND-01", "OP20"},
    {"EQ-WELD-01", "OP30"},
    {"EQ-ASSY-01", "OP40_OP50"},
    {"EQ-SEAL-01", "OP60"},
    {"EQ-TEST-01", "OP70"},
    {"EQ-PACK-01", "OP80"}
};

static size_t bounded_string_length(const char *text, size_t limit)
{
    size_t length = 0;

    while (length < limit && text[length] != '\0') {
        ++length;
    }
    return length;
}

static int is_valid_utf8(const unsigned char *text, size_t length)
{
    size_t index = 0;

    while (index < length) {
        unsigned char first = text[index];
        size_t continuation_count;
        unsigned int code_point;
        size_t offset;

        if (first <= 0x7F) {
            ++index;
            continue;
        }
        if (first >= 0xC2 && first <= 0xDF) {
            continuation_count = 1;
            code_point = first & 0x1F;
        } else if (first >= 0xE0 && first <= 0xEF) {
            continuation_count = 2;
            code_point = first & 0x0F;
        } else if (first >= 0xF0 && first <= 0xF4) {
            continuation_count = 3;
            code_point = first & 0x07;
        } else {
            return 0;
        }

        if (index + continuation_count >= length) {
            return 0;
        }
        for (offset = 1; offset <= continuation_count; ++offset) {
            unsigned char next = text[index + offset];
            if ((next & 0xC0) != 0x80) {
                return 0;
            }
            code_point = (code_point << 6) | (next & 0x3F);
        }

        if ((continuation_count == 1 && code_point < 0x80)
            || (continuation_count == 2 && code_point < 0x800)
            || (continuation_count == 3 && code_point < 0x10000)
            || (code_point >= 0xD800 && code_point <= 0xDFFF)
            || code_point > 0x10FFFF) {
            return 0;
        }
        index += continuation_count + 1;
    }
    return 1;
}

static int field_has_valid_format(const char *field)
{
    size_t length;

    if (field == NULL || field[0] == '\0') {
        return 0;
    }
    length = strlen(field);
    return !isspace((unsigned char)field[0])
        && !isspace((unsigned char)field[length - 1]);
}

static int is_upper_code(const char *field)
{
    const unsigned char *cursor = (const unsigned char *)field;

    if (!field_has_valid_format(field) || strcmp(field, "-") == 0) {
        return 0;
    }
    while (*cursor != '\0') {
        if (!isupper(*cursor) && !isdigit(*cursor) && *cursor != '_'
            && *cursor != '-') {
            return 0;
        }
        ++cursor;
    }
    return 1;
}

static ProtocolResult copy_field(char *destination,
                                 size_t capacity,
                                 const char *source)
{
    size_t length = strlen(source);

    if (length >= capacity) {
        return PROTOCOL_RESULT_FIELD_TOO_LONG;
    }
    memcpy(destination, source, length + 1);
    return PROTOCOL_RESULT_OK;
}

static ProtocolResult split_fields(char *line,
                                   char **fields,
                                   size_t *field_count)
{
    char *field_start = line;
    char *cursor = line;
    size_t count = 0;

    for (;;) {
        if (*cursor == ',' || *cursor == '\0') {
            int reached_end = *cursor == '\0';

            if (count >= PROTOCOL_MAX_FIELDS) {
                return PROTOCOL_RESULT_FIELD_COUNT_MISMATCH;
            }
            if (cursor == field_start) {
                return PROTOCOL_RESULT_INVALID_FIELD_FORMAT;
            }
            *cursor = '\0';
            if (!field_has_valid_format(field_start)) {
                return PROTOCOL_RESULT_INVALID_FIELD_FORMAT;
            }
            fields[count++] = field_start;
            if (reached_end) {
                break;
            }
            field_start = cursor + 1;
        }
        ++cursor;
    }
    *field_count = count;
    return PROTOCOL_RESULT_OK;
}

static ProtocolEventType event_type_from_name(const char *name)
{
    if (strcmp(name, "HELLO") == 0) {
        return PROTOCOL_EVENT_HELLO;
    }
    if (strcmp(name, "HEARTBEAT") == 0) {
        return PROTOCOL_EVENT_HEARTBEAT;
    }
    if (strcmp(name, "PRODUCTION") == 0) {
        return PROTOCOL_EVENT_PRODUCTION;
    }
    if (strcmp(name, "INSPECTION") == 0) {
        return PROTOCOL_EVENT_INSPECTION;
    }
    if (strcmp(name, "DEFECT") == 0) {
        return PROTOCOL_EVENT_DEFECT;
    }
    if (strcmp(name, "ALARM") == 0) {
        return PROTOCOL_EVENT_ALARM;
    }
    if (strcmp(name, "MACHINE_STATUS") == 0) {
        return PROTOCOL_EVENT_MACHINE_STATUS;
    }
    if (strcmp(name, "COMMAND") == 0) {
        return PROTOCOL_EVENT_COMMAND;
    }
    if (strcmp(name, "COMMAND_ACK") == 0) {
        return PROTOCOL_EVENT_COMMAND_ACK;
    }
    return PROTOCOL_EVENT_UNKNOWN;
}

static size_t expected_field_count(ProtocolEventType type)
{
    switch (type) {
    case PROTOCOL_EVENT_HELLO:
    case PROTOCOL_EVENT_HEARTBEAT:
        return 3;
    case PROTOCOL_EVENT_PRODUCTION:
        return 9;
    case PROTOCOL_EVENT_INSPECTION:
        return 9;
    case PROTOCOL_EVENT_DEFECT:
        return 8;
    case PROTOCOL_EVENT_ALARM:
    case PROTOCOL_EVENT_COMMAND_ACK:
        return 6;
    case PROTOCOL_EVENT_MACHINE_STATUS:
        return 7;
    case PROTOCOL_EVENT_COMMAND:
        return 8;
    case PROTOCOL_EVENT_UNKNOWN:
    default:
        return 0;
    }
}

static int is_known_machine(const char *machine_id)
{
    size_t index;

    for (index = 0;
         index < sizeof(MACHINE_PROCESS_PAIRS) / sizeof(MACHINE_PROCESS_PAIRS[0]);
         ++index) {
        if (strcmp(machine_id, MACHINE_PROCESS_PAIRS[index].machine_id) == 0) {
            return 1;
        }
    }
    return 0;
}

static ProtocolResult validate_machine(const char *machine_id)
{
    if (bounded_string_length(machine_id, PROTOCOL_MACHINE_ID_CAPACITY)
        >= PROTOCOL_MACHINE_ID_CAPACITY) {
        return PROTOCOL_RESULT_FIELD_TOO_LONG;
    }
    return is_known_machine(machine_id)
        ? PROTOCOL_RESULT_OK
        : PROTOCOL_RESULT_UNKNOWN_MACHINE;
}

static ProtocolResult validate_machine_process(const char *machine_id,
                                               const char *process_code)
{
    ProtocolResult result = validate_machine(machine_id);

    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    if (bounded_string_length(process_code, PROTOCOL_PROCESS_CODE_CAPACITY)
        >= PROTOCOL_PROCESS_CODE_CAPACITY) {
        return PROTOCOL_RESULT_FIELD_TOO_LONG;
    }
    return protocol_machine_matches_process(machine_id, process_code)
        ? PROTOCOL_RESULT_OK
        : PROTOCOL_RESULT_PROCESS_MISMATCH;
}

static ProtocolResult parse_nonnegative_int(const char *text, int *out_value)
{
    char *end = NULL;
    long value;

    if (text == NULL || text[0] == '\0') {
        return PROTOCOL_RESULT_INVALID_NUMBER;
    }
    errno = 0;
    value = strtol(text, &end, 10);
    if (errno == ERANGE || value > INT_MAX || value < 0) {
        return PROTOCOL_RESULT_OUT_OF_RANGE;
    }
    if (end == text || *end != '\0') {
        return PROTOCOL_RESULT_INVALID_NUMBER;
    }
    *out_value = (int)value;
    return PROTOCOL_RESULT_OK;
}

static ProtocolResult parse_positive_int64(const char *text, int64_t *out_value)
{
    const unsigned char *cursor = (const unsigned char *)text;
    int64_t value = 0;

    if (text == NULL || text[0] == '\0') {
        return PROTOCOL_RESULT_INVALID_NUMBER;
    }
    while (*cursor != '\0') {
        int digit;

        if (!isdigit(*cursor)) {
            return PROTOCOL_RESULT_INVALID_NUMBER;
        }
        digit = *cursor - '0';
        if (value > (INT64_MAX - digit) / 10) {
            return PROTOCOL_RESULT_OUT_OF_RANGE;
        }
        value = value * 10 + digit;
        ++cursor;
    }
    if (value <= 0) {
        return PROTOCOL_RESULT_OUT_OF_RANGE;
    }
    *out_value = value;
    return PROTOCOL_RESULT_OK;
}

static void positive_int64_to_text(int64_t value, char output[21])
{
    char reversed[20];
    size_t length = 0;
    size_t index;

    do {
        reversed[length++] = (char)('0' + (value % 10));
        value /= 10;
    } while (value > 0);

    for (index = 0; index < length; ++index) {
        output[index] = reversed[length - index - 1];
    }
    output[length] = '\0';
}

static ProtocolResult parse_decimal(const char *text, double *out_value)
{
    char *end = NULL;
    double value;

    if (text == NULL || text[0] == '\0' || strcmp(text, "-") == 0) {
        return PROTOCOL_RESULT_INVALID_NUMBER;
    }
    errno = 0;
    value = strtod(text, &end);
    if (errno == ERANGE || !isfinite(value)) {
        return PROTOCOL_RESULT_OUT_OF_RANGE;
    }
    if (end == text || *end != '\0') {
        return PROTOCOL_RESULT_INVALID_NUMBER;
    }
    *out_value = value;
    return PROTOCOL_RESULT_OK;
}

static int string_is_one_of(const char *value,
                            const char *first,
                            const char *second,
                            const char *third,
                            const char *fourth)
{
    return strcmp(value, first) == 0
        || (second != NULL && strcmp(value, second) == 0)
        || (third != NULL && strcmp(value, third) == 0)
        || (fourth != NULL && strcmp(value, fourth) == 0);
}

static ProtocolResult validate_required_lot(const char *lot_no)
{
    if (bounded_string_length(lot_no, PROTOCOL_LOT_NO_CAPACITY)
        >= PROTOCOL_LOT_NO_CAPACITY) {
        return PROTOCOL_RESULT_FIELD_TOO_LONG;
    }
    return strcmp(lot_no, "-") == 0
        ? PROTOCOL_RESULT_INVALID_VALUE
        : PROTOCOL_RESULT_OK;
}

static ProtocolResult parse_connection_event(char **fields,
                                             ProtocolMessage *message)
{
    ProtocolResult result = validate_machine(fields[2]);

    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    return copy_field(message->data.connection.machine_id,
                      sizeof(message->data.connection.machine_id),
                      fields[2]);
}

static ProtocolResult parse_production(char **fields, ProtocolMessage *message)
{
    ProtocolProductionEvent *event = &message->data.production;
    ProtocolResult result = validate_machine_process(fields[2], fields[3]);

    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    if ((result = validate_required_lot(fields[4])) != PROTOCOL_RESULT_OK
        || (result = parse_nonnegative_int(fields[5], &event->input_qty)) != PROTOCOL_RESULT_OK
        || (result = parse_nonnegative_int(fields[6], &event->ok_qty)) != PROTOCOL_RESULT_OK
        || (result = parse_nonnegative_int(fields[7], &event->ng_qty)) != PROTOCOL_RESULT_OK) {
        return result;
    }
    if ((int64_t)event->input_qty
        != (int64_t)event->ok_qty + (int64_t)event->ng_qty) {
        return PROTOCOL_RESULT_INVALID_VALUE;
    }
    if (!string_is_one_of(fields[8],
                          "RUNNING",
                          "COMPLETED",
                          "FAILED",
                          NULL)) {
        return PROTOCOL_RESULT_INVALID_VALUE;
    }
    if ((result = copy_field(event->machine_id, sizeof(event->machine_id), fields[2])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->process_code, sizeof(event->process_code), fields[3])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->lot_no, sizeof(event->lot_no), fields[4])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->status, sizeof(event->status), fields[8])) != PROTOCOL_RESULT_OK) {
        return result;
    }
    return PROTOCOL_RESULT_OK;
}

static ProtocolResult parse_inspection(char **fields, ProtocolMessage *message)
{
    ProtocolInspectionEvent *event = &message->data.inspection;
    ProtocolResult result = validate_machine_process(fields[2], fields[3]);

    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    if ((result = validate_required_lot(fields[4])) != PROTOCOL_RESULT_OK
        || (result = parse_nonnegative_int(fields[5], &event->unit_seq)) != PROTOCOL_RESULT_OK
        || (result = parse_decimal(fields[7], &event->value)) != PROTOCOL_RESULT_OK) {
        return result;
    }
    if (event->unit_seq <= 0) {
        return PROTOCOL_RESULT_OUT_OF_RANGE;
    }
    if ((result = copy_field(event->machine_id, sizeof(event->machine_id), fields[2])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->process_code, sizeof(event->process_code), fields[3])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->lot_no, sizeof(event->lot_no), fields[4])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->item, sizeof(event->item), fields[6])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->unit, sizeof(event->unit), fields[8])) != PROTOCOL_RESULT_OK) {
        return result;
    }
    return PROTOCOL_RESULT_OK;
}

static ProtocolResult parse_defect(char **fields, ProtocolMessage *message)
{
    ProtocolDefectEvent *event = &message->data.defect;
    ProtocolResult result = validate_machine_process(fields[2], fields[3]);

    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    if ((result = validate_required_lot(fields[4])) != PROTOCOL_RESULT_OK) {
        return result;
    }
    if (!is_upper_code(fields[5])) {
        return PROTOCOL_RESULT_INVALID_VALUE;
    }
    if ((result = parse_nonnegative_int(fields[6], &event->defect_qty)) != PROTOCOL_RESULT_OK) {
        return result;
    }
    if (event->defect_qty < 1) {
        return PROTOCOL_RESULT_OUT_OF_RANGE;
    }
    if ((result = copy_field(event->machine_id, sizeof(event->machine_id), fields[2])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->process_code, sizeof(event->process_code), fields[3])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->lot_no, sizeof(event->lot_no), fields[4])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->defect_code, sizeof(event->defect_code), fields[5])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->message, sizeof(event->message), fields[7])) != PROTOCOL_RESULT_OK) {
        return result;
    }
    return PROTOCOL_RESULT_OK;
}

static ProtocolResult parse_alarm(char **fields, ProtocolMessage *message)
{
    ProtocolAlarmEvent *event = &message->data.alarm;
    ProtocolResult result = validate_machine(fields[2]);

    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    if (!is_upper_code(fields[3])) {
        return PROTOCOL_RESULT_INVALID_VALUE;
    }
    if (strcmp(fields[3], "COMM_DISCONNECTED") == 0
        || strcmp(fields[3], "COMM_TIMEOUT") == 0) {
        return PROTOCOL_RESULT_UNEXPECTED_EVENT;
    }
    if (!string_is_one_of(fields[4], "WARNING", "ERROR", NULL, NULL)) {
        return PROTOCOL_RESULT_INVALID_VALUE;
    }
    if ((result = copy_field(event->machine_id, sizeof(event->machine_id), fields[2])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->alarm_code, sizeof(event->alarm_code), fields[3])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->alarm_level, sizeof(event->alarm_level), fields[4])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->message, sizeof(event->message), fields[5])) != PROTOCOL_RESULT_OK) {
        return result;
    }
    return PROTOCOL_RESULT_OK;
}

static ProtocolResult parse_machine_status(char **fields, ProtocolMessage *message)
{
    ProtocolMachineStatusEvent *event = &message->data.machine_status;
    ProtocolResult result = validate_machine_process(fields[2], fields[5]);

    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    if (!string_is_one_of(fields[3], "IDLE", "RUNNING", "ERROR", "STOPPED")) {
        return PROTOCOL_RESULT_INVALID_VALUE;
    }
    if (strlen(fields[4]) >= PROTOCOL_LOT_NO_CAPACITY) {
        return PROTOCOL_RESULT_FIELD_TOO_LONG;
    }
    if ((result = copy_field(event->machine_id, sizeof(event->machine_id), fields[2])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->status, sizeof(event->status), fields[3])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->lot_no, sizeof(event->lot_no), fields[4])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->process_code, sizeof(event->process_code), fields[5])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->message, sizeof(event->message), fields[6])) != PROTOCOL_RESULT_OK) {
        return result;
    }
    return PROTOCOL_RESULT_OK;
}

static ProtocolResult parse_command_ack(char **fields, ProtocolMessage *message)
{
    ProtocolCommandAckEvent *event = &message->data.command_ack;
    ProtocolResult result = validate_machine(fields[2]);

    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    if ((result = parse_positive_int64(fields[3], &event->command_id)) != PROTOCOL_RESULT_OK) {
        return result;
    }
    if (!string_is_one_of(fields[4], "ACCEPTED", "REJECTED", NULL, NULL)) {
        return PROTOCOL_RESULT_INVALID_VALUE;
    }
    if ((result = copy_field(event->machine_id, sizeof(event->machine_id), fields[2])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->ack_status, sizeof(event->ack_status), fields[4])) != PROTOCOL_RESULT_OK
        || (result = copy_field(event->message, sizeof(event->message), fields[5])) != PROTOCOL_RESULT_OK) {
        return result;
    }
    return PROTOCOL_RESULT_OK;
}

const char *protocol_event_type_name(ProtocolEventType type)
{
    switch (type) {
    case PROTOCOL_EVENT_HELLO:
        return "HELLO";
    case PROTOCOL_EVENT_HEARTBEAT:
        return "HEARTBEAT";
    case PROTOCOL_EVENT_PRODUCTION:
        return "PRODUCTION";
    case PROTOCOL_EVENT_INSPECTION:
        return "INSPECTION";
    case PROTOCOL_EVENT_DEFECT:
        return "DEFECT";
    case PROTOCOL_EVENT_ALARM:
        return "ALARM";
    case PROTOCOL_EVENT_MACHINE_STATUS:
        return "MACHINE_STATUS";
    case PROTOCOL_EVENT_COMMAND:
        return "COMMAND";
    case PROTOCOL_EVENT_COMMAND_ACK:
        return "COMMAND_ACK";
    case PROTOCOL_EVENT_UNKNOWN:
    default:
        return "UNKNOWN";
    }
}

const char *protocol_command_type_name(ProtocolCommandType type)
{
    switch (type) {
    case PROTOCOL_COMMAND_START:
        return "START";
    case PROTOCOL_COMMAND_STOP:
        return "STOP";
    case PROTOCOL_COMMAND_RESUME:
        return "RESUME";
    case PROTOCOL_COMMAND_UNKNOWN:
    default:
        return "UNKNOWN";
    }
}

const char *protocol_result_name(ProtocolResult result)
{
    switch (result) {
    case PROTOCOL_RESULT_OK:
        return "OK";
    case PROTOCOL_RESULT_NULL_ARGUMENT:
        return "NULL_ARGUMENT";
    case PROTOCOL_RESULT_EMPTY_MESSAGE:
        return "EMPTY_MESSAGE";
    case PROTOCOL_RESULT_MESSAGE_TOO_LONG:
        return "MESSAGE_TOO_LONG";
    case PROTOCOL_RESULT_INVALID_UTF8:
        return "INVALID_UTF8";
    case PROTOCOL_RESULT_MISSING_LINE_FEED:
        return "MISSING_LINE_FEED";
    case PROTOCOL_RESULT_INVALID_LINE_ENDING:
        return "INVALID_LINE_ENDING";
    case PROTOCOL_RESULT_INVALID_FIELD_FORMAT:
        return "INVALID_FIELD_FORMAT";
    case PROTOCOL_RESULT_UNSUPPORTED_VERSION:
        return "UNSUPPORTED_VERSION";
    case PROTOCOL_RESULT_UNKNOWN_EVENT:
        return "UNKNOWN_EVENT";
    case PROTOCOL_RESULT_UNEXPECTED_EVENT:
        return "UNEXPECTED_EVENT";
    case PROTOCOL_RESULT_FIELD_COUNT_MISMATCH:
        return "FIELD_COUNT_MISMATCH";
    case PROTOCOL_RESULT_FIELD_TOO_LONG:
        return "FIELD_TOO_LONG";
    case PROTOCOL_RESULT_UNKNOWN_MACHINE:
        return "UNKNOWN_MACHINE";
    case PROTOCOL_RESULT_PROCESS_MISMATCH:
        return "PROCESS_MISMATCH";
    case PROTOCOL_RESULT_INVALID_NUMBER:
        return "INVALID_NUMBER";
    case PROTOCOL_RESULT_OUT_OF_RANGE:
        return "OUT_OF_RANGE";
    case PROTOCOL_RESULT_INVALID_VALUE:
        return "INVALID_VALUE";
    case PROTOCOL_RESULT_BUFFER_TOO_SMALL:
        return "BUFFER_TOO_SMALL";
    default:
        return "UNKNOWN_RESULT";
    }
}

int protocol_machine_matches_process(const char *machine_id, const char *process_code)
{
    size_t index;

    if (machine_id == NULL || process_code == NULL) {
        return 0;
    }
    for (index = 0;
         index < sizeof(MACHINE_PROCESS_PAIRS) / sizeof(MACHINE_PROCESS_PAIRS[0]);
         ++index) {
        if (strcmp(machine_id, MACHINE_PROCESS_PAIRS[index].machine_id) == 0
            && strcmp(process_code, MACHINE_PROCESS_PAIRS[index].process_code) == 0) {
            return 1;
        }
    }
    return 0;
}

ProtocolResult protocol_parse_message(const char *line, ProtocolMessage *out_message)
{
    char copy[COLLECTOR_MAX_MESSAGE_SIZE + 1];
    char *fields[PROTOCOL_MAX_FIELDS];
    size_t length;
    size_t index;
    size_t field_count = 0;
    ProtocolEventType type;
    ProtocolMessage parsed;
    ProtocolResult result;

    if (line == NULL || out_message == NULL) {
        return PROTOCOL_RESULT_NULL_ARGUMENT;
    }
    memset(out_message, 0, sizeof(*out_message));
    length = bounded_string_length(line, COLLECTOR_MAX_MESSAGE_SIZE + 1U);
    if (length == 0) {
        return PROTOCOL_RESULT_EMPTY_MESSAGE;
    }
    if (length > COLLECTOR_MAX_MESSAGE_SIZE) {
        return PROTOCOL_RESULT_MESSAGE_TOO_LONG;
    }
    if (line[length - 1] != '\n') {
        return PROTOCOL_RESULT_MISSING_LINE_FEED;
    }
    for (index = 0; index + 1 < length; ++index) {
        if (line[index] == '\r' || line[index] == '\n') {
            return PROTOCOL_RESULT_INVALID_LINE_ENDING;
        }
    }
    if (!is_valid_utf8((const unsigned char *)line, length - 1)) {
        return PROTOCOL_RESULT_INVALID_UTF8;
    }

    memcpy(copy, line, length - 1);
    copy[length - 1] = '\0';
    result = split_fields(copy, fields, &field_count);
    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    if (strcmp(fields[0], PROTOCOL_VERSION) != 0) {
        return PROTOCOL_RESULT_UNSUPPORTED_VERSION;
    }
    type = event_type_from_name(fields[1]);
    if (type == PROTOCOL_EVENT_UNKNOWN) {
        return PROTOCOL_RESULT_UNKNOWN_EVENT;
    }
    if (type == PROTOCOL_EVENT_COMMAND) {
        return PROTOCOL_RESULT_UNEXPECTED_EVENT;
    }
    if (field_count != expected_field_count(type)) {
        return PROTOCOL_RESULT_FIELD_COUNT_MISMATCH;
    }

    memset(&parsed, 0, sizeof(parsed));
    parsed.type = type;
    switch (type) {
    case PROTOCOL_EVENT_HELLO:
    case PROTOCOL_EVENT_HEARTBEAT:
        result = parse_connection_event(fields, &parsed);
        break;
    case PROTOCOL_EVENT_PRODUCTION:
        result = parse_production(fields, &parsed);
        break;
    case PROTOCOL_EVENT_INSPECTION:
        result = parse_inspection(fields, &parsed);
        break;
    case PROTOCOL_EVENT_DEFECT:
        result = parse_defect(fields, &parsed);
        break;
    case PROTOCOL_EVENT_ALARM:
        result = parse_alarm(fields, &parsed);
        break;
    case PROTOCOL_EVENT_MACHINE_STATUS:
        result = parse_machine_status(fields, &parsed);
        break;
    case PROTOCOL_EVENT_COMMAND_ACK:
        result = parse_command_ack(fields, &parsed);
        break;
    case PROTOCOL_EVENT_COMMAND:
    case PROTOCOL_EVENT_UNKNOWN:
    default:
        result = PROTOCOL_RESULT_UNEXPECTED_EVENT;
        break;
    }
    if (result == PROTOCOL_RESULT_OK) {
        *out_message = parsed;
    }
    return result;
}

ProtocolResult protocol_build_command(char *out_buffer,
                                      size_t buffer_size,
                                      const ProtocolCommand *command,
                                      size_t *out_length)
{
    const char *command_type;
    char command_id_text[21];
    int written;
    ProtocolResult result;

    if (out_buffer == NULL || command == NULL) {
        return PROTOCOL_RESULT_NULL_ARGUMENT;
    }
    if (buffer_size == 0) {
        return PROTOCOL_RESULT_BUFFER_TOO_SMALL;
    }
    out_buffer[0] = '\0';
    if (out_length != NULL) {
        *out_length = 0;
    }
    if (command->command_id <= 0) {
        return PROTOCOL_RESULT_OUT_OF_RANGE;
    }
    command_type = protocol_command_type_name(command->type);
    if (command->type != PROTOCOL_COMMAND_START
        && command->type != PROTOCOL_COMMAND_STOP
        && command->type != PROTOCOL_COMMAND_RESUME) {
        return PROTOCOL_RESULT_INVALID_VALUE;
    }
    result = validate_machine_process(command->machine_id, command->process_code);
    if (result != PROTOCOL_RESULT_OK) {
        return result;
    }
    if ((result = validate_required_lot(command->lot_no)) != PROTOCOL_RESULT_OK) {
        return result;
    }
    if (!field_has_valid_format(command->lot_no)
        || strchr(command->lot_no, ',') != NULL
        || strchr(command->lot_no, '\r') != NULL
        || strchr(command->lot_no, '\n') != NULL) {
        return PROTOCOL_RESULT_INVALID_FIELD_FORMAT;
    }
    if ((command->type == PROTOCOL_COMMAND_STOP && command->input_qty != 0)
        || (command->type != PROTOCOL_COMMAND_STOP && command->input_qty < 1)) {
        return PROTOCOL_RESULT_OUT_OF_RANGE;
    }
    positive_int64_to_text(command->command_id, command_id_text);
    written = snprintf(out_buffer,
                       buffer_size,
                       "%s,COMMAND,%s,%s,%s,%s,%s,%d\n",
                       PROTOCOL_VERSION,
                       command_id_text,
                       command_type,
                       command->machine_id,
                       command->process_code,
                       command->lot_no,
                       command->input_qty);
    if (written < 0) {
        out_buffer[0] = '\0';
        return PROTOCOL_RESULT_INVALID_VALUE;
    }
    if ((size_t)written > COLLECTOR_MAX_MESSAGE_SIZE) {
        out_buffer[0] = '\0';
        return PROTOCOL_RESULT_MESSAGE_TOO_LONG;
    }
    if ((size_t)written >= buffer_size) {
        out_buffer[0] = '\0';
        return PROTOCOL_RESULT_BUFFER_TOO_SMALL;
    }
    if (out_length != NULL) {
        *out_length = (size_t)written;
    }
    return PROTOCOL_RESULT_OK;
}
