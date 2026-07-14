#include "protocol.h"

#include <ctype.h>
#include <limits.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "device_config.h"

#define L1_COMMAND_FIELD_COUNT 8

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

static L1ProtocolResult validate_field(const char *value, size_t capacity)
{
    size_t length;
    size_t index;

    if (value == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    length = bounded_string_length(value, capacity);
    if (length >= capacity) {
        return L1_PROTOCOL_FIELD_TOO_LONG;
    }
    if (length == 0
        || isspace((unsigned char)value[0])
        || isspace((unsigned char)value[length - 1])) {
        return L1_PROTOCOL_INVALID_FIELD_FORMAT;
    }
    for (index = 0; index < length; ++index) {
        if (value[index] == ',' || value[index] == '\r'
            || value[index] == '\n') {
            return L1_PROTOCOL_INVALID_FIELD_FORMAT;
        }
    }
    return L1_PROTOCOL_OK;
}

static L1ProtocolResult normalize_optional_field(const char *value,
                                                 size_t capacity,
                                                 const char **normalized)
{
    size_t length;

    if (value == NULL || normalized == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    length = bounded_string_length(value, capacity);
    if (length >= capacity) {
        return L1_PROTOCOL_FIELD_TOO_LONG;
    }
    if (length == 0) {
        *normalized = "-";
        return L1_PROTOCOL_OK;
    }
    *normalized = value;
    return validate_field(value, capacity);
}

static int is_upper_code(const char *value)
{
    const unsigned char *cursor = (const unsigned char *)value;

    if (value == NULL || value[0] == '\0' || strcmp(value, "-") == 0) {
        return 0;
    }
    while (*cursor != '\0') {
        if (!isupper(*cursor) && !isdigit(*cursor)
            && *cursor != '_' && *cursor != '-') {
            return 0;
        }
        ++cursor;
    }
    return 1;
}

static L1ProtocolResult validate_machine(const char *machine_id)
{
    L1ProtocolResult result =
        validate_field(machine_id, L1_MACHINE_ID_CAPACITY);

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    return l1_device_config_find(machine_id) != NULL
        ? L1_PROTOCOL_OK
        : L1_PROTOCOL_UNKNOWN_MACHINE;
}

static L1ProtocolResult validate_machine_process(const char *machine_id,
                                                 const char *process_code)
{
    L1ProtocolResult result = validate_machine(machine_id);

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    result = validate_field(process_code, L1_PROCESS_CODE_CAPACITY);
    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    return l1_device_config_matches(machine_id, process_code)
        ? L1_PROTOCOL_OK
        : L1_PROTOCOL_PROCESS_MISMATCH;
}

static L1ProtocolResult validate_required_lot(const char *lot_no)
{
    L1ProtocolResult result =
        validate_field(lot_no, L1_LOT_NO_CAPACITY);

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    return strcmp(lot_no, "-") == 0
        ? L1_PROTOCOL_INVALID_VALUE
        : L1_PROTOCOL_OK;
}

static L1ProtocolResult prepare_output(char *out_buffer,
                                       size_t buffer_size,
                                       size_t *out_length)
{
    if (out_buffer == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    if (buffer_size == 0) {
        return L1_PROTOCOL_BUFFER_TOO_SMALL;
    }
    out_buffer[0] = '\0';
    if (out_length != NULL) {
        *out_length = 0;
    }
    return L1_PROTOCOL_OK;
}

static L1ProtocolResult finish_output(char *out_buffer,
                                      size_t buffer_size,
                                      int written,
                                      size_t *out_length)
{
    if (written < 0) {
        out_buffer[0] = '\0';
        return L1_PROTOCOL_INVALID_VALUE;
    }
    if ((size_t)written > L1_PROTOCOL_MAX_MESSAGE_SIZE) {
        out_buffer[0] = '\0';
        return L1_PROTOCOL_MESSAGE_TOO_LONG;
    }
    if ((size_t)written >= buffer_size) {
        out_buffer[0] = '\0';
        return L1_PROTOCOL_BUFFER_TOO_SMALL;
    }
    if (out_length != NULL) {
        *out_length = (size_t)written;
    }
    return L1_PROTOCOL_OK;
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

static L1ProtocolResult parse_positive_int64(const char *text,
                                             int64_t *out_value)
{
    const unsigned char *cursor = (const unsigned char *)text;
    int64_t value = 0;

    if (text == NULL || text[0] == '\0') {
        return L1_PROTOCOL_INVALID_NUMBER;
    }
    while (*cursor != '\0') {
        int digit;

        if (!isdigit(*cursor)) {
            return L1_PROTOCOL_INVALID_NUMBER;
        }
        digit = *cursor - '0';
        if (value > (INT64_MAX - digit) / 10) {
            return L1_PROTOCOL_OUT_OF_RANGE;
        }
        value = value * 10 + digit;
        ++cursor;
    }
    if (value <= 0) {
        return L1_PROTOCOL_OUT_OF_RANGE;
    }
    *out_value = value;
    return L1_PROTOCOL_OK;
}

static L1ProtocolResult parse_nonnegative_int(const char *text, int *out_value)
{
    const unsigned char *cursor = (const unsigned char *)text;
    int value = 0;

    if (text == NULL || text[0] == '\0') {
        return L1_PROTOCOL_INVALID_NUMBER;
    }
    if (text[0] == '-') {
        return L1_PROTOCOL_OUT_OF_RANGE;
    }
    while (*cursor != '\0') {
        int digit;

        if (!isdigit(*cursor)) {
            return L1_PROTOCOL_INVALID_NUMBER;
        }
        digit = *cursor - '0';
        if (value > (INT_MAX - digit) / 10) {
            return L1_PROTOCOL_OUT_OF_RANGE;
        }
        value = value * 10 + digit;
        ++cursor;
    }
    *out_value = value;
    return L1_PROTOCOL_OK;
}

static L1ProtocolResult copy_field(char *destination,
                                   size_t capacity,
                                   const char *source)
{
    size_t length = strlen(source);

    if (length >= capacity) {
        return L1_PROTOCOL_FIELD_TOO_LONG;
    }
    memcpy(destination, source, length + 1);
    return L1_PROTOCOL_OK;
}

static L1ProtocolResult split_command_fields(char *line,
                                             char **fields,
                                             size_t *field_count)
{
    char *field_start = line;
    char *cursor = line;
    size_t count = 0;

    for (;;) {
        if (*cursor == ',' || *cursor == '\0') {
            int reached_end = *cursor == '\0';

            if (count >= L1_COMMAND_FIELD_COUNT) {
                return L1_PROTOCOL_FIELD_COUNT_MISMATCH;
            }
            if (cursor == field_start) {
                return L1_PROTOCOL_INVALID_FIELD_FORMAT;
            }
            *cursor = '\0';
            if (isspace((unsigned char)field_start[0])
                || isspace((unsigned char)cursor[-1])) {
                return L1_PROTOCOL_INVALID_FIELD_FORMAT;
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
    return L1_PROTOCOL_OK;
}

static L1CommandType command_type_from_name(const char *name)
{
    if (strcmp(name, "START") == 0) {
        return L1_COMMAND_START;
    }
    if (strcmp(name, "STOP") == 0) {
        return L1_COMMAND_STOP;
    }
    if (strcmp(name, "RESUME") == 0) {
        return L1_COMMAND_RESUME;
    }
    return L1_COMMAND_UNKNOWN;
}

static const char *production_status_name(L1ProductionStatus status)
{
    switch (status) {
    case L1_PRODUCTION_COMPLETED:
        return "COMPLETED";
    case L1_PRODUCTION_FAILED:
        return "FAILED";
    case L1_PRODUCTION_STATUS_UNKNOWN:
    default:
        return NULL;
    }
}

static const char *inspection_result_name(L1InspectionResult result)
{
    switch (result) {
    case L1_INSPECTION_OK:
        return "OK";
    case L1_INSPECTION_NG:
        return "NG";
    case L1_INSPECTION_RESULT_UNKNOWN:
    default:
        return NULL;
    }
}

static const char *alarm_level_name(L1AlarmLevel level)
{
    switch (level) {
    case L1_ALARM_WARNING:
        return "WARNING";
    case L1_ALARM_ERROR:
        return "ERROR";
    case L1_ALARM_LEVEL_UNKNOWN:
    default:
        return NULL;
    }
}

static const char *machine_status_name(L1MachineStatus status)
{
    switch (status) {
    case L1_MACHINE_IDLE:
        return "IDLE";
    case L1_MACHINE_RUNNING:
        return "RUNNING";
    case L1_MACHINE_ERROR:
        return "ERROR";
    case L1_MACHINE_STOPPED:
        return "STOPPED";
    case L1_MACHINE_STATUS_UNKNOWN:
    default:
        return NULL;
    }
}

static const char *ack_status_name(L1CommandAckStatus status)
{
    switch (status) {
    case L1_ACK_ACCEPTED:
        return "ACCEPTED";
    case L1_ACK_REJECTED:
        return "REJECTED";
    case L1_ACK_STATUS_UNKNOWN:
    default:
        return NULL;
    }
}

const char *l1_command_type_name(L1CommandType type)
{
    switch (type) {
    case L1_COMMAND_START:
        return "START";
    case L1_COMMAND_STOP:
        return "STOP";
    case L1_COMMAND_RESUME:
        return "RESUME";
    case L1_COMMAND_UNKNOWN:
    default:
        return "UNKNOWN";
    }
}

const char *l1_protocol_result_name(L1ProtocolResult result)
{
    switch (result) {
    case L1_PROTOCOL_OK:
        return "OK";
    case L1_PROTOCOL_NULL_ARGUMENT:
        return "NULL_ARGUMENT";
    case L1_PROTOCOL_EMPTY_MESSAGE:
        return "EMPTY_MESSAGE";
    case L1_PROTOCOL_MESSAGE_TOO_LONG:
        return "MESSAGE_TOO_LONG";
    case L1_PROTOCOL_INVALID_UTF8:
        return "INVALID_UTF8";
    case L1_PROTOCOL_MISSING_LINE_FEED:
        return "MISSING_LINE_FEED";
    case L1_PROTOCOL_INVALID_LINE_ENDING:
        return "INVALID_LINE_ENDING";
    case L1_PROTOCOL_INVALID_FIELD_FORMAT:
        return "INVALID_FIELD_FORMAT";
    case L1_PROTOCOL_UNSUPPORTED_VERSION:
        return "UNSUPPORTED_VERSION";
    case L1_PROTOCOL_UNEXPECTED_EVENT:
        return "UNEXPECTED_EVENT";
    case L1_PROTOCOL_FIELD_COUNT_MISMATCH:
        return "FIELD_COUNT_MISMATCH";
    case L1_PROTOCOL_FIELD_TOO_LONG:
        return "FIELD_TOO_LONG";
    case L1_PROTOCOL_UNKNOWN_MACHINE:
        return "UNKNOWN_MACHINE";
    case L1_PROTOCOL_PROCESS_MISMATCH:
        return "PROCESS_MISMATCH";
    case L1_PROTOCOL_INVALID_NUMBER:
        return "INVALID_NUMBER";
    case L1_PROTOCOL_OUT_OF_RANGE:
        return "OUT_OF_RANGE";
    case L1_PROTOCOL_INVALID_VALUE:
        return "INVALID_VALUE";
    case L1_PROTOCOL_BUFFER_TOO_SMALL:
        return "BUFFER_TOO_SMALL";
    default:
        return "UNKNOWN_RESULT";
    }
}

L1ProtocolResult l1_protocol_parse_command(const char *line,
                                           L1Command *out_command)
{
    char copy[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    char *fields[L1_COMMAND_FIELD_COUNT];
    size_t length;
    size_t index;
    size_t field_count = 0;
    L1ProtocolResult result;
    L1Command parsed;

    if (line == NULL || out_command == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    memset(out_command, 0, sizeof(*out_command));
    length = bounded_string_length(line, L1_PROTOCOL_MAX_MESSAGE_SIZE + 1U);
    if (length == 0) {
        return L1_PROTOCOL_EMPTY_MESSAGE;
    }
    if (length > L1_PROTOCOL_MAX_MESSAGE_SIZE) {
        return L1_PROTOCOL_MESSAGE_TOO_LONG;
    }
    if (line[length - 1] != '\n') {
        return L1_PROTOCOL_MISSING_LINE_FEED;
    }
    for (index = 0; index + 1 < length; ++index) {
        if (line[index] == '\r' || line[index] == '\n') {
            return L1_PROTOCOL_INVALID_LINE_ENDING;
        }
    }
    if (!is_valid_utf8((const unsigned char *)line, length - 1)) {
        return L1_PROTOCOL_INVALID_UTF8;
    }

    memcpy(copy, line, length - 1);
    copy[length - 1] = '\0';
    result = split_command_fields(copy, fields, &field_count);
    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    if (field_count != L1_COMMAND_FIELD_COUNT) {
        return L1_PROTOCOL_FIELD_COUNT_MISMATCH;
    }
    if (strcmp(fields[0], L1_PROTOCOL_VERSION) != 0) {
        return L1_PROTOCOL_UNSUPPORTED_VERSION;
    }
    if (strcmp(fields[1], "COMMAND") != 0) {
        return L1_PROTOCOL_UNEXPECTED_EVENT;
    }

    memset(&parsed, 0, sizeof(parsed));
    if ((result = parse_positive_int64(fields[2], &parsed.command_id))
        != L1_PROTOCOL_OK) {
        return result;
    }
    parsed.type = command_type_from_name(fields[3]);
    if (parsed.type == L1_COMMAND_UNKNOWN) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    if ((result = validate_machine_process(fields[4], fields[5]))
        != L1_PROTOCOL_OK
        || (result = validate_required_lot(fields[6])) != L1_PROTOCOL_OK
        || (result = parse_nonnegative_int(fields[7], &parsed.input_qty))
            != L1_PROTOCOL_OK) {
        return result;
    }
    if ((parsed.type == L1_COMMAND_STOP && parsed.input_qty != 0)
        || (parsed.type != L1_COMMAND_STOP && parsed.input_qty < 1)) {
        return L1_PROTOCOL_OUT_OF_RANGE;
    }
    if ((result = copy_field(parsed.machine_id,
                             sizeof(parsed.machine_id),
                             fields[4])) != L1_PROTOCOL_OK
        || (result = copy_field(parsed.process_code,
                                sizeof(parsed.process_code),
                                fields[5])) != L1_PROTOCOL_OK
        || (result = copy_field(parsed.lot_no,
                                sizeof(parsed.lot_no),
                                fields[6])) != L1_PROTOCOL_OK) {
        return result;
    }
    *out_command = parsed;
    return L1_PROTOCOL_OK;
}

static L1ProtocolResult build_connection_message(char *out_buffer,
                                                 size_t buffer_size,
                                                 const char *event_name,
                                                 const char *machine_id,
                                                 size_t *out_length)
{
    L1ProtocolResult result =
        prepare_output(out_buffer, buffer_size, out_length);
    int written;

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    if ((result = validate_machine(machine_id)) != L1_PROTOCOL_OK) {
        return result;
    }
    written = snprintf(out_buffer,
                       buffer_size,
                       "%s,%s,%s\n",
                       L1_PROTOCOL_VERSION,
                       event_name,
                       machine_id);
    return finish_output(out_buffer,
                         buffer_size,
                         written,
                         out_length);
}

L1ProtocolResult l1_protocol_build_hello(char *out_buffer,
                                         size_t buffer_size,
                                         const char *machine_id,
                                         size_t *out_length)
{
    return build_connection_message(out_buffer,
                                    buffer_size,
                                    "HELLO",
                                    machine_id,
                                    out_length);
}

L1ProtocolResult l1_protocol_build_heartbeat(char *out_buffer,
                                             size_t buffer_size,
                                             const char *machine_id,
                                             size_t *out_length)
{
    return build_connection_message(out_buffer,
                                    buffer_size,
                                    "HEARTBEAT",
                                    machine_id,
                                    out_length);
}

L1ProtocolResult l1_protocol_build_production(
    char *out_buffer,
    size_t buffer_size,
    const L1ProductionEvent *event,
    size_t *out_length)
{
    const char *status;
    L1ProtocolResult result =
        prepare_output(out_buffer, buffer_size, out_length);
    int written;

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    if (event == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    if ((result = validate_machine_process(event->machine_id,
                                           event->process_code))
            != L1_PROTOCOL_OK
        || (result = validate_required_lot(event->lot_no))
            != L1_PROTOCOL_OK) {
        return result;
    }
    if (event->input_qty < 0 || event->ok_qty < 0 || event->ng_qty < 0) {
        return L1_PROTOCOL_OUT_OF_RANGE;
    }
    if ((int64_t)event->input_qty
        != (int64_t)event->ok_qty + (int64_t)event->ng_qty) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    status = production_status_name(event->status);
    if (status == NULL) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    written = snprintf(out_buffer,
                       buffer_size,
                       "%s,PRODUCTION,%s,%s,%s,%d,%d,%d,%s\n",
                       L1_PROTOCOL_VERSION,
                       event->machine_id,
                       event->process_code,
                       event->lot_no,
                       event->input_qty,
                       event->ok_qty,
                       event->ng_qty,
                       status);
    return finish_output(out_buffer, buffer_size, written, out_length);
}

L1ProtocolResult l1_protocol_build_inspection(
    char *out_buffer,
    size_t buffer_size,
    const L1InspectionEvent *event,
    size_t *out_length)
{
    char lower_limit[64];
    char upper_limit[64];
    const char *unit;
    const char *result_name;
    L1ProtocolResult result =
        prepare_output(out_buffer, buffer_size, out_length);
    int written;

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    if (event == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    if ((result = validate_machine_process(event->machine_id,
                                           event->process_code))
            != L1_PROTOCOL_OK
        || (result = validate_required_lot(event->lot_no))
            != L1_PROTOCOL_OK
        || (result = validate_field(event->item,
                                    L1_INSPECTION_ITEM_CAPACITY))
            != L1_PROTOCOL_OK
        || (result = normalize_optional_field(event->unit,
                                              L1_UNIT_CAPACITY,
                                              &unit))
            != L1_PROTOCOL_OK) {
        return result;
    }
    if (!isfinite(event->value)
        || (event->has_lower_limit && !isfinite(event->lower_limit))
        || (event->has_upper_limit && !isfinite(event->upper_limit))) {
        return L1_PROTOCOL_OUT_OF_RANGE;
    }
    if ((event->has_lower_limit != 0 && event->has_lower_limit != 1)
        || (event->has_upper_limit != 0 && event->has_upper_limit != 1)) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    if (event->has_lower_limit && event->has_upper_limit
        && event->lower_limit > event->upper_limit) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    result_name = inspection_result_name(event->result);
    if (result_name == NULL) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    if (event->has_lower_limit) {
        int limit_length = snprintf(lower_limit,
                                    sizeof(lower_limit),
                                    "%.3f",
                                    event->lower_limit);

        if (limit_length < 0
            || (size_t)limit_length >= sizeof(lower_limit)) {
            return L1_PROTOCOL_OUT_OF_RANGE;
        }
    } else {
        strcpy(lower_limit, "-");
    }
    if (event->has_upper_limit) {
        int limit_length = snprintf(upper_limit,
                                    sizeof(upper_limit),
                                    "%.3f",
                                    event->upper_limit);

        if (limit_length < 0
            || (size_t)limit_length >= sizeof(upper_limit)) {
            return L1_PROTOCOL_OUT_OF_RANGE;
        }
    } else {
        strcpy(upper_limit, "-");
    }
    written = snprintf(out_buffer,
                       buffer_size,
                       "%s,INSPECTION,%s,%s,%s,%s,%.3f,%s,%s,%s,%s\n",
                       L1_PROTOCOL_VERSION,
                       event->machine_id,
                       event->process_code,
                       event->lot_no,
                       event->item,
                       event->value,
                       unit,
                       lower_limit,
                       upper_limit,
                       result_name);
    return finish_output(out_buffer, buffer_size, written, out_length);
}

L1ProtocolResult l1_protocol_build_defect(char *out_buffer,
                                          size_t buffer_size,
                                          const L1DefectEvent *event,
                                          size_t *out_length)
{
    const char *message;
    L1ProtocolResult result =
        prepare_output(out_buffer, buffer_size, out_length);
    int written;

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    if (event == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    if ((result = validate_machine_process(event->machine_id,
                                           event->process_code))
            != L1_PROTOCOL_OK
        || (result = validate_required_lot(event->lot_no))
            != L1_PROTOCOL_OK
        || (result = validate_field(event->defect_code,
                                    L1_CODE_CAPACITY))
            != L1_PROTOCOL_OK
        || (result = normalize_optional_field(event->message,
                                              L1_MESSAGE_CAPACITY,
                                              &message))
            != L1_PROTOCOL_OK) {
        return result;
    }
    if (!is_upper_code(event->defect_code)) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    if (event->defect_qty < 1) {
        return L1_PROTOCOL_OUT_OF_RANGE;
    }
    written = snprintf(out_buffer,
                       buffer_size,
                       "%s,DEFECT,%s,%s,%s,%s,%d,%s\n",
                       L1_PROTOCOL_VERSION,
                       event->machine_id,
                       event->process_code,
                       event->lot_no,
                       event->defect_code,
                       event->defect_qty,
                       message);
    return finish_output(out_buffer, buffer_size, written, out_length);
}

L1ProtocolResult l1_protocol_build_alarm(char *out_buffer,
                                         size_t buffer_size,
                                         const L1AlarmEvent *event,
                                         size_t *out_length)
{
    const char *level;
    const char *message;
    L1ProtocolResult result =
        prepare_output(out_buffer, buffer_size, out_length);
    int written;

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    if (event == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    if ((result = validate_machine(event->machine_id)) != L1_PROTOCOL_OK
        || (result = validate_field(event->alarm_code,
                                    L1_CODE_CAPACITY))
            != L1_PROTOCOL_OK
        || (result = normalize_optional_field(event->message,
                                              L1_MESSAGE_CAPACITY,
                                              &message))
            != L1_PROTOCOL_OK) {
        return result;
    }
    if (!is_upper_code(event->alarm_code)) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    if (strcmp(event->alarm_code, "COMM_DISCONNECTED") == 0
        || strcmp(event->alarm_code, "COMM_TIMEOUT") == 0) {
        return L1_PROTOCOL_UNEXPECTED_EVENT;
    }
    level = alarm_level_name(event->alarm_level);
    if (level == NULL) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    written = snprintf(out_buffer,
                       buffer_size,
                       "%s,ALARM,%s,%s,%s,%s\n",
                       L1_PROTOCOL_VERSION,
                       event->machine_id,
                       event->alarm_code,
                       level,
                       message);
    return finish_output(out_buffer, buffer_size, written, out_length);
}

L1ProtocolResult l1_protocol_build_machine_status(
    char *out_buffer,
    size_t buffer_size,
    const L1MachineStatusEvent *event,
    size_t *out_length)
{
    const char *status;
    const char *lot_no;
    const char *message;
    L1ProtocolResult result =
        prepare_output(out_buffer, buffer_size, out_length);
    int written;

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    if (event == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    if ((result = validate_machine_process(event->machine_id,
                                           event->process_code))
            != L1_PROTOCOL_OK
        || (result = normalize_optional_field(event->lot_no,
                                              L1_LOT_NO_CAPACITY,
                                              &lot_no))
            != L1_PROTOCOL_OK
        || (result = normalize_optional_field(event->message,
                                              L1_MESSAGE_CAPACITY,
                                              &message))
            != L1_PROTOCOL_OK) {
        return result;
    }
    status = machine_status_name(event->status);
    if (status == NULL) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    written = snprintf(out_buffer,
                       buffer_size,
                       "%s,MACHINE_STATUS,%s,%s,%s,%s,%s\n",
                       L1_PROTOCOL_VERSION,
                       event->machine_id,
                       status,
                       lot_no,
                       event->process_code,
                       message);
    return finish_output(out_buffer, buffer_size, written, out_length);
}

L1ProtocolResult l1_protocol_build_command_ack(
    char *out_buffer,
    size_t buffer_size,
    const L1CommandAckEvent *event,
    size_t *out_length)
{
    char command_id[21];
    const char *status;
    const char *message;
    L1ProtocolResult result =
        prepare_output(out_buffer, buffer_size, out_length);
    int written;

    if (result != L1_PROTOCOL_OK) {
        return result;
    }
    if (event == NULL) {
        return L1_PROTOCOL_NULL_ARGUMENT;
    }
    if ((result = validate_machine(event->machine_id)) != L1_PROTOCOL_OK
        || (result = normalize_optional_field(event->message,
                                              L1_MESSAGE_CAPACITY,
                                              &message))
            != L1_PROTOCOL_OK) {
        return result;
    }
    if (event->command_id <= 0) {
        return L1_PROTOCOL_OUT_OF_RANGE;
    }
    status = ack_status_name(event->status);
    if (status == NULL) {
        return L1_PROTOCOL_INVALID_VALUE;
    }
    positive_int64_to_text(event->command_id, command_id);
    written = snprintf(out_buffer,
                       buffer_size,
                       "%s,COMMAND_ACK,%s,%s,%s,%s\n",
                       L1_PROTOCOL_VERSION,
                       event->machine_id,
                       command_id,
                       status,
                       message);
    return finish_output(out_buffer, buffer_size, written, out_length);
}
