#include "api_client.h"

#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#ifdef _WIN32
#include <process.h>
#include <windows.h>
#else
#include <unistd.h>
#endif

#include "config.h"
#include "net.h"
#include "thread_compat.h"

#define API_PATH_PRODUCTION "/api/collector/production-logs"
#define API_PATH_INSPECTION "/api/collector/inspections"
#define API_PATH_JUDGMENT "/api/collector/judgments"
#define API_PATH_DEFECT "/api/collector/defects"
#define API_PATH_ALARM "/api/collector/machine-alarms"
#define API_PATH_MACHINE_STATUS "/api/collector/machine-statuses"
#define API_PATH_COMMAND_ACK "/api/collector/command-acks"
#define API_PATH_PENDING_COMMANDS "/api/collector/commands/pending"
#define API_PATH_RELEASE_COMMAND_PREFIX "/api/collector/commands/"
#define API_PATH_COLLECTOR_HEARTBEAT "/api/collector/heartbeat"
#define API_QUEUE_LINE_CAPACITY (API_CLIENT_PATH_CAPACITY + API_CLIENT_JSON_CAPACITY + 16)
#define API_EVENT_ID_CAPACITY 100

static CollectorMutex queue_mutex;
static volatile int queue_running;
static volatile int queue_worker_active;
static int queue_initialized;
static unsigned long event_sequence;

static ApiClientResult api_client_post_with_retries(const char *path,
                                                     const char *json,
                                                     int *http_status);

typedef struct {
    char *buffer;
    size_t capacity;
    size_t length;
    int failed;
} JsonBuilder;

static int api_current_local_datetime(char *output, size_t output_capacity)
{
    if (output == NULL || output_capacity < 20U) {
        return -1;
    }

#ifdef _WIN32
    SYSTEMTIME local_time;
    int written;

    GetLocalTime(&local_time);

    written = snprintf(
        output,
        output_capacity,
        "%04u-%02u-%02uT%02u:%02u:%02u",
        (unsigned int)local_time.wYear,
        (unsigned int)local_time.wMonth,
        (unsigned int)local_time.wDay,
        (unsigned int)local_time.wHour,
        (unsigned int)local_time.wMinute,
        (unsigned int)local_time.wSecond
    );

    if (written < 0 || (size_t)written >= output_capacity) {
        return -1;
    }

    return 0;
#else
    time_t now;
    struct tm local_time;

    now = time(NULL);

    if (localtime_r(&now, &local_time) == NULL) {
        return -1;
    }

    return strftime(
        output,
        output_capacity,
        "%Y-%m-%dT%H:%M:%S",
        &local_time
    ) == 0 ? -1 : 0;
#endif
}

static int api_uint64_to_text(uint64_t value,
                              char *output,
                              size_t output_capacity)
{
    char reversed[20];
    size_t length = 0;
    size_t index;

    do {
        reversed[length++] = (char)('0' + (value % 10U));
        value /= 10U;
    } while (value > 0U);
    if (length + 1U > output_capacity) {
        return -1;
    }
    for (index = 0; index < length; ++index) {
        output[index] = reversed[length - index - 1U];
    }
    output[length] = '\0';
    return 0;
}

static int api_int64_to_text(int64_t value,
                             char *output,
                             size_t output_capacity)
{
    uint64_t magnitude;

    if (value >= 0) {
        return api_uint64_to_text((uint64_t)value,
                                  output,
                                  output_capacity);
    }
    if (output_capacity < 2U) {
        return -1;
    }
    output[0] = '-';
    magnitude = (uint64_t)(-(value + 1)) + 1U;
    return api_uint64_to_text(magnitude,
                              output + 1,
                              output_capacity - 1U);
}

static void json_append_format(JsonBuilder *builder, const char *format, ...)
{
    va_list arguments;
    int written;

    if (builder->failed) {
        return;
    }
    va_start(arguments, format);
    written = vsnprintf(builder->buffer + builder->length,
                        builder->capacity - builder->length,
                        format,
                        arguments);
    va_end(arguments);
    if (written < 0 || (size_t)written >= builder->capacity - builder->length) {
        builder->failed = 1;
        return;
    }
    builder->length += (size_t)written;
}

static void json_append_byte(JsonBuilder *builder, unsigned char byte)
{
    if (builder->failed) {
        return;
    }
    if (builder->length + 1 >= builder->capacity) {
        builder->failed = 1;
        return;
    }
    builder->buffer[builder->length++] = (char)byte;
    builder->buffer[builder->length] = '\0';
}

static void json_append_string(JsonBuilder *builder, const char *value)
{
    const unsigned char *cursor = (const unsigned char *)value;

    json_append_byte(builder, '"');
    while (!builder->failed && *cursor != '\0') {
        switch (*cursor) {
        case '"':
            json_append_format(builder, "\\\"");
            break;
        case '\\':
            json_append_format(builder, "\\\\");
            break;
        case '\b':
            json_append_format(builder, "\\b");
            break;
        case '\f':
            json_append_format(builder, "\\f");
            break;
        case '\n':
            json_append_format(builder, "\\n");
            break;
        case '\r':
            json_append_format(builder, "\\r");
            break;
        case '\t':
            json_append_format(builder, "\\t");
            break;
        default:
            if (*cursor < 0x20U) {
                json_append_format(builder, "\\u%04X", (unsigned int)*cursor);
            } else {
                json_append_byte(builder, *cursor);
            }
            break;
        }
        ++cursor;
    }
    json_append_byte(builder, '"');
}

static void json_append_field_name(JsonBuilder *builder,
                                   const char *name,
                                   int *first)
{
    if (!*first) {
        json_append_byte(builder, ',');
    }
    *first = 0;
    json_append_string(builder, name);
    json_append_byte(builder, ':');
}

static void json_append_string_field(JsonBuilder *builder,
                                     const char *name,
                                     const char *value,
                                     int *first)
{
    json_append_field_name(builder, name, first);
    json_append_string(builder, value);
}

static void json_append_optional_string_field(JsonBuilder *builder,
                                              const char *name,
                                              const char *value,
                                              int *first)
{
    json_append_field_name(builder, name, first);
    if (value == NULL || value[0] == '\0' || strcmp(value, "-") == 0) {
        json_append_format(builder, "null");
    } else {
        json_append_string(builder, value);
    }
}

static void json_append_int_field(JsonBuilder *builder,
                                  const char *name,
                                  int value,
                                  int *first)
{
    json_append_field_name(builder, name, first);
    json_append_format(builder, "%d", value);
}

static void json_append_int64_field(JsonBuilder *builder,
                                    const char *name,
                                    int64_t value,
                                    int *first)
{
    char value_text[22];

    json_append_field_name(builder, name, first);
    if (api_int64_to_text(value, value_text, sizeof(value_text)) != 0) {
        builder->failed = 1;
        return;
    }
    json_append_format(builder, "%s", value_text);
}

static void json_append_double_field(JsonBuilder *builder,
                                     const char *name,
                                     double value,
                                     int *first)
{
    json_append_field_name(builder, name, first);
    json_append_format(builder, "%.15g", value);
}

static ApiClientResult copy_path(char *path,
                                 size_t path_capacity,
                                 const char *value)
{
    int written;

    written = snprintf(path, path_capacity, "%s", value);
    if (written < 0 || (size_t)written >= path_capacity) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }
    return API_CLIENT_OK;
}

static ApiClientResult api_client_build_event_request_internal(
    const ProtocolMessage *message,
    const char *event_id,
    char *path,
    size_t path_capacity,
    char *json,
    size_t json_capacity)
{
    JsonBuilder builder;
    ApiClientResult path_result;
    int first = 1;

    if (message == NULL || path == NULL || path_capacity == 0
        || json == NULL || json_capacity == 0) {
        return API_CLIENT_INVALID_ARGUMENT;
    }
    path[0] = '\0';
    json[0] = '\0';
    builder.buffer = json;
    builder.capacity = json_capacity;
    builder.length = 0;
    builder.failed = 0;

    if (message->type == PROTOCOL_EVENT_HELLO
        || message->type == PROTOCOL_EVENT_HEARTBEAT) {
        return API_CLIENT_SKIPPED;
    }
    json_append_byte(&builder, '{');
    if (event_id != NULL && event_id[0] != '\0'
        && message->type != PROTOCOL_EVENT_COMMAND_ACK) {
        json_append_string_field(&builder, "eventId", event_id, &first);
    }
    switch (message->type) {
    case PROTOCOL_EVENT_PRODUCTION: {
        const ProtocolProductionEvent *event = &message->data.production;

        path_result = copy_path(path, path_capacity, API_PATH_PRODUCTION);
        json_append_string_field(&builder, "lotNo", event->lot_no, &first);
        json_append_string_field(&builder, "machineId", event->machine_id, &first);
        json_append_string_field(&builder, "processCode", event->process_code, &first);
        json_append_int_field(&builder, "inputQty", event->input_qty, &first);
        json_append_int_field(&builder, "okQty", event->ok_qty, &first);
        json_append_int_field(&builder, "ngQty", event->ng_qty, &first);
        json_append_string_field(&builder, "status", event->status, &first);
        break;
    }
    case PROTOCOL_EVENT_INSPECTION: {
        const ProtocolInspectionEvent *event = &message->data.inspection;

        path_result = copy_path(path, path_capacity, API_PATH_INSPECTION);
        json_append_string_field(&builder, "lotNo", event->lot_no, &first);
        json_append_string_field(&builder, "machineId", event->machine_id, &first);
        json_append_string_field(&builder, "processCode", event->process_code, &first);
        json_append_int_field(&builder, "unitSeq", event->unit_seq, &first);
        json_append_string_field(&builder, "inspectionItem", event->item, &first);
        json_append_double_field(&builder, "measuredValue", event->value, &first);
        json_append_optional_string_field(&builder, "unit", event->unit, &first);
        break;
    }
    case PROTOCOL_EVENT_JUDGMENT: {
        const ProtocolJudgmentEvent *event = &message->data.judgment;

        path_result = copy_path(path, path_capacity, API_PATH_JUDGMENT);
        json_append_string_field(&builder, "lotNo", event->lot_no, &first);
        json_append_string_field(&builder, "machineId", event->machine_id, &first);
        json_append_string_field(&builder, "processCode", event->process_code, &first);
        json_append_int_field(&builder, "unitSeq", event->unit_seq, &first);
        json_append_string_field(&builder, "result", event->result, &first);
        json_append_optional_string_field(&builder, "defectCode", event->defect_code, &first);
        json_append_optional_string_field(&builder, "message", event->message, &first);
        break;
    }
    case PROTOCOL_EVENT_DEFECT: {
        const ProtocolDefectEvent *event = &message->data.defect;

        path_result = copy_path(path, path_capacity, API_PATH_DEFECT);
        json_append_string_field(&builder, "lotNo", event->lot_no, &first);
        json_append_string_field(&builder, "machineId", event->machine_id, &first);
        json_append_string_field(&builder, "processCode", event->process_code, &first);
        json_append_string_field(&builder, "defectCode", event->defect_code, &first);
        json_append_int_field(&builder, "defectQty", event->defect_qty, &first);
        json_append_optional_string_field(&builder, "message", event->message, &first);
        break;
    }
    case PROTOCOL_EVENT_ALARM: {
        const ProtocolAlarmEvent *event = &message->data.alarm;
        const char *alarm_level = strcmp(event->alarm_level, "WARNING") == 0
            ? "WARN"
            : event->alarm_level;

        char occurred_at[32];

        path_result = copy_path(path, path_capacity, API_PATH_ALARM);
        json_append_string_field(&builder, "machineId", event->machine_id, &first);
        json_append_string_field(&builder, "alarmCode", event->alarm_code, &first);
        json_append_string_field(&builder, "alarmLevel", alarm_level, &first);
        if (api_current_local_datetime(occurred_at, sizeof(occurred_at)) == 0) {
            json_append_string_field(&builder, "occurredAt", occurred_at, &first);
        }
        json_append_optional_string_field(&builder, "message", event->message, &first);
        break;
    }
    case PROTOCOL_EVENT_MACHINE_STATUS: {
        const ProtocolMachineStatusEvent *event = &message->data.machine_status;

        path_result = copy_path(path, path_capacity, API_PATH_MACHINE_STATUS);
        json_append_string_field(&builder, "machineId", event->machine_id, &first);
        json_append_string_field(&builder, "status", event->status, &first);
        json_append_optional_string_field(&builder, "lotNo", event->lot_no, &first);
        json_append_optional_string_field(&builder,
                                          "processCode",
                                          event->process_code,
                                          &first);
        json_append_optional_string_field(&builder, "message", event->message, &first);
        break;
    }
    case PROTOCOL_EVENT_COMMAND_ACK: {
        const ProtocolCommandAckEvent *event = &message->data.command_ack;

        path_result = copy_path(path, path_capacity, API_PATH_COMMAND_ACK);
        json_append_string_field(&builder, "machineId", event->machine_id, &first);
        json_append_int64_field(&builder, "commandId", event->command_id, &first);
        json_append_string_field(&builder, "ackStatus", event->ack_status, &first);
        json_append_optional_string_field(&builder, "message", event->message, &first);
        break;
    }
    case PROTOCOL_EVENT_COMMAND:
    case PROTOCOL_EVENT_UNKNOWN:
    default:
        return API_CLIENT_INVALID_ARGUMENT;
    }
    json_append_byte(&builder, '}');
    if (path_result != API_CLIENT_OK) {
        return path_result;
    }
    return builder.failed ? API_CLIENT_BUFFER_TOO_SMALL : API_CLIENT_OK;
}

ApiClientResult api_client_build_event_request(
    const ProtocolMessage *message,
    char *path,
    size_t path_capacity,
    char *json,
    size_t json_capacity)
{
    return api_client_build_event_request_internal(message,
                                                   NULL,
                                                   path,
                                                   path_capacity,
                                                   json,
                                                   json_capacity);
}

static ApiClientResult api_client_post_json(const char *path,
                                            const char *json,
                                            int *http_status)
{
    char header[1024];
    char response[1024];
    size_t response_length = 0;
    NetSocket socket;
    int header_length;
    int status = 0;
    uint32_t connect_timeout_ms =
        (uint32_t)MES_HTTP_CONNECT_TIMEOUT_SECONDS * 1000U;
    uint32_t request_timeout_ms =
        (uint32_t)MES_HTTP_REQUEST_TIMEOUT_SECONDS * 1000U;

    if (http_status != NULL) {
        *http_status = 0;
    }
    header_length = snprintf(
        header,
        sizeof(header),
        "POST %s HTTP/1.1\r\n"
        "Host: %s:%d\r\n"
        "Content-Type: application/json; charset=UTF-8\r\n"
        "Accept: application/json\r\n"
        "Connection: close\r\n"
        "Content-Length: %lu\r\n\r\n",
        path,
        MES_BACKEND_ADDRESS,
        MES_BACKEND_PORT,
        (unsigned long)strlen(json));
    if (header_length < 0 || (size_t)header_length >= sizeof(header)) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }

    socket = net_tcp_client_connect(MES_BACKEND_ADDRESS,
                                    MES_BACKEND_PORT,
                                    connect_timeout_ms);
    if (socket == NET_INVALID_SOCKET) {
        return API_CLIENT_CONNECT_ERROR;
    }
    if (net_set_send_timeout(socket, request_timeout_ms) != 0
        || net_set_receive_timeout(socket, request_timeout_ms) != 0
        || net_send_all(socket, header, (size_t)header_length) != 0
        || net_send_all(socket, json, strlen(json)) != 0) {
        net_socket_close(socket);
        return API_CLIENT_IO_ERROR;
    }

    while (response_length + 1 < sizeof(response)) {
        int received = net_receive(socket,
                                   response + response_length,
                                   sizeof(response) - response_length - 1);

        if (received == NET_RECEIVE_ERROR || received == NET_RECEIVE_TIMEOUT) {
            net_socket_close(socket);
            return API_CLIENT_IO_ERROR;
        }
        if (received == 0) {
            break;
        }
        response_length += (size_t)received;
        response[response_length] = '\0';
        if (strstr(response, "\r\n") != NULL) {
            break;
        }
    }
    net_socket_close(socket);
    response[response_length] = '\0';
    if (sscanf(response, "HTTP/%*u.%*u %d", &status) != 1) {
        return API_CLIENT_INVALID_RESPONSE;
    }
    if (http_status != NULL) {
        *http_status = status;
    }
    if (status >= 200 && status < 300) {
        return API_CLIENT_OK;
    }
    if (status >= 400 && status < 500) {
        return API_CLIENT_HTTP_CLIENT_ERROR;
    }
    if (status >= 500 && status < 600) {
        return API_CLIENT_HTTP_SERVER_ERROR;
    }
    return API_CLIENT_HTTP_UNEXPECTED_STATUS;
}

static int ascii_equal_ignore_case(char left, char right)
{
    return tolower((unsigned char)left) == tolower((unsigned char)right);
}

static int header_contains_ignore_case(const char *header,
                                       size_t header_length,
                                       const char *needle)
{
    size_t needle_length;
    size_t index;

    if (header == NULL || needle == NULL) {
        return 0;
    }
    needle_length = strlen(needle);
    if (needle_length == 0 || needle_length > header_length) {
        return 0;
    }
    for (index = 0; index + needle_length <= header_length; ++index) {
        size_t offset;
        for (offset = 0; offset < needle_length; ++offset) {
            if (!ascii_equal_ignore_case(header[index + offset], needle[offset])) {
                break;
            }
        }
        if (offset == needle_length) {
            return 1;
        }
    }
    return 0;
}

static ApiClientResult api_result_from_http_status(int status)
{
    if (status >= 200 && status < 300) {
        return API_CLIENT_OK;
    }
    if (status >= 400 && status < 500) {
        return API_CLIENT_HTTP_CLIENT_ERROR;
    }
    if (status >= 500 && status < 600) {
        return API_CLIENT_HTTP_SERVER_ERROR;
    }
    return API_CLIENT_HTTP_UNEXPECTED_STATUS;
}

static ApiClientResult decode_chunked_body(const char *input,
                                            size_t input_length,
                                            char *output,
                                            size_t output_capacity,
                                            size_t *output_length)
{
    size_t input_offset = 0;
    size_t output_offset = 0;

    if (input == NULL || output == NULL || output_capacity == 0) {
        return API_CLIENT_INVALID_ARGUMENT;
    }
    while (input_offset < input_length) {
        const char *line_end;
        const char *chunk_start;
        char size_buffer[32];
        size_t size_length;
        unsigned long chunk_size;
        char *parse_end;

        line_end = strstr(input + input_offset, "\r\n");
        if (line_end == NULL || (size_t)(line_end - input) >= input_length) {
            return API_CLIENT_INVALID_RESPONSE;
        }
        size_length = (size_t)(line_end - (input + input_offset));
        if (size_length == 0 || size_length >= sizeof(size_buffer)) {
            return API_CLIENT_INVALID_RESPONSE;
        }
        {
            const char *extension = memchr(input + input_offset, ';', size_length);
            if (extension != NULL) {
                size_length = (size_t)(extension - (input + input_offset));
            }
        }
        memcpy(size_buffer, input + input_offset, size_length);
        size_buffer[size_length] = '\0';
        errno = 0;
        chunk_size = strtoul(size_buffer, &parse_end, 16);
        if (errno != 0 || parse_end == size_buffer || *parse_end != '\0') {
            return API_CLIENT_INVALID_RESPONSE;
        }
        input_offset = (size_t)(line_end - input) + 2;
        if (chunk_size == 0) {
            if (output_offset >= output_capacity) {
                return API_CLIENT_BUFFER_TOO_SMALL;
            }
            output[output_offset] = '\0';
            if (output_length != NULL) {
                *output_length = output_offset;
            }
            return API_CLIENT_OK;
        }
        if (chunk_size > input_length - input_offset
            || input_offset + (size_t)chunk_size + 2 > input_length) {
            return API_CLIENT_INVALID_RESPONSE;
        }
        if (output_offset + (size_t)chunk_size + 1 > output_capacity) {
            return API_CLIENT_BUFFER_TOO_SMALL;
        }
        chunk_start = input + input_offset;
        memcpy(output + output_offset, chunk_start, (size_t)chunk_size);
        output_offset += (size_t)chunk_size;
        input_offset += (size_t)chunk_size;
        if (input[input_offset] != '\r' || input[input_offset + 1] != '\n') {
            return API_CLIENT_INVALID_RESPONSE;
        }
        input_offset += 2;
    }
    return API_CLIENT_INVALID_RESPONSE;
}

static ApiClientResult api_client_get_json(const char *path,
                                           char *body,
                                           size_t body_capacity,
                                           int *http_status)
{
    char request[1024];
    char response[API_CLIENT_RESPONSE_CAPACITY];
    size_t response_length = 0;
    NetSocket socket;
    int request_length;
    int status = 0;
    char *header_end;
    const char *body_start;
    size_t header_length;
    size_t raw_body_length;
    ApiClientResult status_result;
    uint32_t connect_timeout_ms =
        (uint32_t)MES_HTTP_CONNECT_TIMEOUT_SECONDS * 1000U;
    uint32_t request_timeout_ms =
        (uint32_t)MES_HTTP_REQUEST_TIMEOUT_SECONDS * 1000U;

    if (http_status != NULL) {
        *http_status = 0;
    }
    if (path == NULL || body == NULL || body_capacity == 0) {
        return API_CLIENT_INVALID_ARGUMENT;
    }
    body[0] = '\0';
    request_length = snprintf(
        request,
        sizeof(request),
        "GET %s HTTP/1.0\r\n"
        "Host: %s:%d\r\n"
        "Accept: application/json\r\n"
        "Connection: close\r\n\r\n",
        path,
        MES_BACKEND_ADDRESS,
        MES_BACKEND_PORT);
    if (request_length < 0 || (size_t)request_length >= sizeof(request)) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }

    socket = net_tcp_client_connect(MES_BACKEND_ADDRESS,
                                    MES_BACKEND_PORT,
                                    connect_timeout_ms);
    if (socket == NET_INVALID_SOCKET) {
        return API_CLIENT_CONNECT_ERROR;
    }
    if (net_set_send_timeout(socket, request_timeout_ms) != 0
        || net_set_receive_timeout(socket, request_timeout_ms) != 0
        || net_send_all(socket, request, (size_t)request_length) != 0) {
        net_socket_close(socket);
        return API_CLIENT_IO_ERROR;
    }
    while (response_length + 1 < sizeof(response)) {
        int received = net_receive(socket,
                                   response + response_length,
                                   sizeof(response) - response_length - 1);
        if (received == NET_RECEIVE_ERROR || received == NET_RECEIVE_TIMEOUT) {
            net_socket_close(socket);
            return API_CLIENT_IO_ERROR;
        }
        if (received == 0) {
            break;
        }
        response_length += (size_t)received;
    }
    net_socket_close(socket);
    if (response_length + 1 >= sizeof(response)) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }
    response[response_length] = '\0';
    if (sscanf(response, "HTTP/%*u.%*u %d", &status) != 1) {
        return API_CLIENT_INVALID_RESPONSE;
    }
    if (http_status != NULL) {
        *http_status = status;
    }
    status_result = api_result_from_http_status(status);
    if (status_result != API_CLIENT_OK) {
        return status_result;
    }

    header_end = strstr(response, "\r\n\r\n");
    if (header_end == NULL) {
        return API_CLIENT_INVALID_RESPONSE;
    }
    body_start = header_end + 4;
    header_length = (size_t)(header_end - response);
    raw_body_length = response_length - (size_t)(body_start - response);
    if (header_contains_ignore_case(response,
                                    header_length,
                                    "transfer-encoding: chunked")) {
        return decode_chunked_body(body_start,
                                   raw_body_length,
                                   body,
                                   body_capacity,
                                   NULL);
    }
    if (raw_body_length + 1 > body_capacity) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }
    memcpy(body, body_start, raw_body_length);
    body[raw_body_length] = '\0';
    return API_CLIENT_OK;
}

static const char *json_skip_whitespace(const char *cursor)
{
    while (cursor != NULL && *cursor != '\0'
           && isspace((unsigned char)*cursor)) {
        ++cursor;
    }
    return cursor;
}

static const char *json_skip_string(const char *cursor)
{
    if (cursor == NULL || *cursor != '"') {
        return NULL;
    }
    ++cursor;
    while (*cursor != '\0') {
        if (*cursor == '\\') {
            if (cursor[1] == '\0') {
                return NULL;
            }
            cursor += 2;
            continue;
        }
        if (*cursor == '"') {
            return cursor + 1;
        }
        ++cursor;
    }
    return NULL;
}

static const char *json_skip_compound(const char *cursor,
                                      char open_character,
                                      char close_character)
{
    int depth = 0;
    int in_string = 0;
    int escaped = 0;

    while (cursor != NULL && *cursor != '\0') {
        char current = *cursor++;
        if (in_string) {
            if (escaped) {
                escaped = 0;
            } else if (current == '\\') {
                escaped = 1;
            } else if (current == '"') {
                in_string = 0;
            }
            continue;
        }
        if (current == '"') {
            in_string = 1;
        } else if (current == open_character) {
            ++depth;
        } else if (current == close_character) {
            --depth;
            if (depth == 0) {
                return cursor;
            }
        }
    }
    return NULL;
}

static const char *json_skip_value(const char *cursor)
{
    cursor = json_skip_whitespace(cursor);
    if (cursor == NULL || *cursor == '\0') {
        return NULL;
    }
    if (*cursor == '"') {
        return json_skip_string(cursor);
    }
    if (*cursor == '{') {
        return json_skip_compound(cursor, '{', '}');
    }
    if (*cursor == '[') {
        return json_skip_compound(cursor, '[', ']');
    }
    while (*cursor != '\0' && *cursor != ',' && *cursor != '}') {
        ++cursor;
    }
    return cursor;
}

static int json_read_string(const char *cursor,
                            char *output,
                            size_t output_capacity,
                            const char **after_value)
{
    size_t length = 0;

    cursor = json_skip_whitespace(cursor);
    if (cursor == NULL || *cursor != '"' || output == NULL
        || output_capacity == 0) {
        return -1;
    }
    ++cursor;
    while (*cursor != '\0' && *cursor != '"') {
        unsigned char value = (unsigned char)*cursor++;
        if (value == '\\') {
            value = (unsigned char)*cursor++;
            if (value == '\0') {
                return -1;
            }
            switch (value) {
            case '"': case '\\': case '/': break;
            case 'b': value = '\b'; break;
            case 'f': value = '\f'; break;
            case 'n': value = '\n'; break;
            case 'r': value = '\r'; break;
            case 't': value = '\t'; break;
            default: return -1;
            }
        }
        if (length + 1 >= output_capacity) {
            return -1;
        }
        output[length++] = (char)value;
    }
    if (*cursor != '"') {
        return -1;
    }
    output[length] = '\0';
    if (after_value != NULL) {
        *after_value = cursor + 1;
    }
    return 0;
}

static const char *json_find_field_value(const char *object,
                                         const char *field_name)
{
    const char *cursor;

    if (object == NULL || field_name == NULL) {
        return NULL;
    }
    cursor = json_skip_whitespace(object);
    if (*cursor != '{') {
        return NULL;
    }
    ++cursor;
    for (;;) {
        char key[128];
        const char *after_key;
        const char *after_value;

        cursor = json_skip_whitespace(cursor);
        if (*cursor == '}') {
            return NULL;
        }
        if (json_read_string(cursor, key, sizeof(key), &after_key) != 0) {
            return NULL;
        }
        cursor = json_skip_whitespace(after_key);
        if (*cursor != ':') {
            return NULL;
        }
        cursor = json_skip_whitespace(cursor + 1);
        if (strcmp(key, field_name) == 0) {
            return cursor;
        }
        after_value = json_skip_value(cursor);
        if (after_value == NULL) {
            return NULL;
        }
        cursor = json_skip_whitespace(after_value);
        if (*cursor == ',') {
            ++cursor;
            continue;
        }
        if (*cursor == '}') {
            return NULL;
        }
        return NULL;
    }
}

static int json_get_required_string(const char *object,
                                    const char *field_name,
                                    char *output,
                                    size_t output_capacity)
{
    const char *value = json_find_field_value(object, field_name);
    return value != NULL
        ? json_read_string(value, output, output_capacity, NULL)
        : -1;
}

static int json_get_required_int64(const char *object,
                                   const char *field_name,
                                   int64_t *output)
{
    const char *value = json_find_field_value(object, field_name);
    char *end;
    long long parsed;

    if (value == NULL || output == NULL) {
        return -1;
    }
    errno = 0;
    parsed = strtoll(value, &end, 10);
    if (errno != 0 || end == value) {
        return -1;
    }
    end = (char *)json_skip_whitespace(end);
    if (*end != ',' && *end != '}') {
        return -1;
    }
    *output = (int64_t)parsed;
    return 0;
}

static int json_get_required_int(const char *object,
                                 const char *field_name,
                                 int *output)
{
    int64_t parsed;
    if (json_get_required_int64(object, field_name, &parsed) != 0
        || parsed < INT_MIN || parsed > INT_MAX) {
        return -1;
    }
    *output = (int)parsed;
    return 0;
}

static ProtocolCommandType api_command_type_from_text(const char *value)
{
    if (strcmp(value, "START") == 0) {
        return PROTOCOL_COMMAND_START;
    }
    if (strcmp(value, "STOP") == 0) {
        return PROTOCOL_COMMAND_STOP;
    }
    if (strcmp(value, "RESUME") == 0) {
        return PROTOCOL_COMMAND_RESUME;
    }
    return PROTOCOL_COMMAND_UNKNOWN;
}

static ApiClientResult api_parse_command_object(const char *object,
                                                ProtocolCommand *command)
{
    char command_type[32];
    char status[32];

    memset(command, 0, sizeof(*command));
    if (json_get_required_int64(object, "commandId", &command->command_id) != 0
        || json_get_required_string(object,
                                    "commandType",
                                    command_type,
                                    sizeof(command_type)) != 0
        || json_get_required_string(object,
                                    "machineId",
                                    command->machine_id,
                                    sizeof(command->machine_id)) != 0
        || json_get_required_string(object,
                                    "processCode",
                                    command->process_code,
                                    sizeof(command->process_code)) != 0
        || json_get_required_string(object,
                                    "lotNo",
                                    command->lot_no,
                                    sizeof(command->lot_no)) != 0
        || json_get_required_int(object, "inputQty", &command->input_qty) != 0
        || json_get_required_string(object,
                                    "status",
                                    status,
                                    sizeof(status)) != 0) {
        return API_CLIENT_INVALID_RESPONSE;
    }
    command->type = api_command_type_from_text(command_type);
    if (command->command_id <= 0 || command->type == PROTOCOL_COMMAND_UNKNOWN
        || strcmp(status, "DISPATCHED") != 0
        || !protocol_machine_matches_process(command->machine_id,
                                             command->process_code)
        || command->lot_no[0] == '\0'
        || ((command->type == PROTOCOL_COMMAND_STOP && command->input_qty != 0)
            || (command->type != PROTOCOL_COMMAND_STOP
                && command->input_qty <= 0))) {
        return API_CLIENT_INVALID_RESPONSE;
    }
    return API_CLIENT_OK;
}

ApiClientResult api_client_parse_command_response(
    const char *json,
    ProtocolCommand *commands,
    size_t command_capacity,
    size_t *command_count)
{
    const char *cursor;
    size_t count = 0;

    if (command_count != NULL) {
        *command_count = 0;
    }
    if (json == NULL || commands == NULL || command_capacity == 0
        || command_count == NULL) {
        return API_CLIENT_INVALID_ARGUMENT;
    }
    cursor = json_skip_whitespace(json);
    if (*cursor != '[') {
        return API_CLIENT_INVALID_RESPONSE;
    }
    ++cursor;
    for (;;) {
        const char *object_end;
        size_t object_length;
        char object[API_CLIENT_JSON_CAPACITY];
        ApiClientResult parse_result;

        cursor = json_skip_whitespace(cursor);
        if (*cursor == ']') {
            cursor = json_skip_whitespace(cursor + 1);
            if (*cursor != '\0') {
                return API_CLIENT_INVALID_RESPONSE;
            }
            *command_count = count;
            return API_CLIENT_OK;
        }
        if (*cursor != '{') {
            return API_CLIENT_INVALID_RESPONSE;
        }
        if (count >= command_capacity) {
            return API_CLIENT_BUFFER_TOO_SMALL;
        }
        object_end = json_skip_compound(cursor, '{', '}');
        if (object_end == NULL) {
            return API_CLIENT_INVALID_RESPONSE;
        }
        object_length = (size_t)(object_end - cursor);
        if (object_length + 1 > sizeof(object)) {
            return API_CLIENT_BUFFER_TOO_SMALL;
        }
        memcpy(object, cursor, object_length);
        object[object_length] = '\0';
        parse_result = api_parse_command_object(object, &commands[count]);
        if (parse_result != API_CLIENT_OK) {
            return parse_result;
        }
        ++count;
        cursor = json_skip_whitespace(object_end);
        if (*cursor == ',') {
            ++cursor;
            continue;
        }
        if (*cursor != ']') {
            return API_CLIENT_INVALID_RESPONSE;
        }
    }
}

ApiClientResult api_client_fetch_pending_commands(
    const char *machine_id,
    ProtocolCommand *commands,
    size_t command_capacity,
    size_t *command_count,
    int *http_status)
{
    char path[API_CLIENT_PATH_CAPACITY];
    char response_body[API_CLIENT_RESPONSE_CAPACITY];
    int written;
    size_t index;
    ApiClientResult result;

    if (command_count != NULL) {
        *command_count = 0;
    }
    if (machine_id == NULL || machine_id[0] == '\0'
        || commands == NULL || command_capacity == 0
        || command_count == NULL) {
        return API_CLIENT_INVALID_ARGUMENT;
    }
    written = snprintf(path,
                       sizeof(path),
                       "%s?machineId=%s",
                       API_PATH_PENDING_COMMANDS,
                       machine_id);
    if (written < 0 || (size_t)written >= sizeof(path)) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }
    result = api_client_get_json(path,
                                 response_body,
                                 sizeof(response_body),
                                 http_status);
    if (result != API_CLIENT_OK) {
        return result;
    }
    result = api_client_parse_command_response(response_body,
                                               commands,
                                               command_capacity,
                                               command_count);
    if (result != API_CLIENT_OK) {
        return result;
    }
    for (index = 0; index < *command_count; ++index) {
        if (strcmp(commands[index].machine_id, machine_id) != 0) {
            *command_count = 0;
            return API_CLIENT_INVALID_RESPONSE;
        }
    }
    return API_CLIENT_OK;
}

ApiClientResult api_client_release_command(int64_t command_id,
                                           const char *machine_id,
                                           int *http_status)
{
    char path[API_CLIENT_PATH_CAPACITY];
    char command_id_text[21];
    int written;

    if (command_id <= 0 || machine_id == NULL || machine_id[0] == '\0') {
        return API_CLIENT_INVALID_ARGUMENT;
    }
    if (api_int64_to_text(command_id,
                          command_id_text,
                          sizeof(command_id_text)) != 0) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }
    written = snprintf(path,
                       sizeof(path),
                       "%s%s/release?machineId=%s",
                       API_PATH_RELEASE_COMMAND_PREFIX,
                       command_id_text,
                       machine_id);
    if (written < 0 || (size_t)written >= sizeof(path)) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }
    return api_client_post_with_retries(path, "{}", http_status);
}

ApiClientResult api_client_send_collector_heartbeat(
    const char *const *connected_machine_ids,
    size_t connected_count,
    size_t total_capacity,
    int *http_status)
{
    char json[API_CLIENT_JSON_CAPACITY];
    size_t offset = 0;
    size_t index;
    int written;

    if ((connected_count > 0 && connected_machine_ids == NULL)
        || connected_count > total_capacity) {
        return API_CLIENT_INVALID_ARGUMENT;
    }
    written = snprintf(json,
                       sizeof(json),
                       "{\"connectedMachineIds\":[");
    if (written < 0 || (size_t)written >= sizeof(json)) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }
    offset = (size_t)written;
    for (index = 0; index < connected_count; ++index) {
        const char *machine_id = connected_machine_ids[index];

        if (machine_id == NULL
            || strchr(machine_id, '"') != NULL
            || strchr(machine_id, '\\') != NULL) {
            return API_CLIENT_INVALID_ARGUMENT;
        }
        written = snprintf(json + offset,
                           sizeof(json) - offset,
                           "%s\"%s\"",
                           index == 0 ? "" : ",",
                           machine_id);
        if (written < 0 || (size_t)written >= sizeof(json) - offset) {
            return API_CLIENT_BUFFER_TOO_SMALL;
        }
        offset += (size_t)written;
    }
    written = snprintf(json + offset,
                       sizeof(json) - offset,
                       "],\"totalCapacity\":%lu}",
                       (unsigned long)total_capacity);
    if (written < 0 || (size_t)written >= sizeof(json) - offset) {
        return API_CLIENT_BUFFER_TOO_SMALL;
    }
    return api_client_post_with_retries(
        API_PATH_COLLECTOR_HEARTBEAT,
        json,
        http_status);
}


static void api_sleep_milliseconds(unsigned int milliseconds)
{
#ifdef _WIN32
    Sleep(milliseconds);
#else
    struct timespec request;

    request.tv_sec = (time_t)(milliseconds / 1000U);
    request.tv_nsec = (long)(milliseconds % 1000U) * 1000000L;
    nanosleep(&request, NULL);
#endif
}

static int api_result_is_retryable(ApiClientResult result)
{
    return result == API_CLIENT_CONNECT_ERROR
        || result == API_CLIENT_IO_ERROR
        || result == API_CLIENT_INVALID_RESPONSE
        || result == API_CLIENT_HTTP_SERVER_ERROR;
}

static ApiClientResult api_client_post_with_retries(const char *path,
                                                     const char *json,
                                                     int *http_status)
{
    ApiClientResult result = API_CLIENT_INVALID_ARGUMENT;
    int attempt;

    for (attempt = 0; attempt <= MES_HTTP_MAX_RETRIES; ++attempt) {
        result = api_client_post_json(path, json, http_status);
        if (result == API_CLIENT_OK || !api_result_is_retryable(result)) {
            return result;
        }
        if (attempt < MES_HTTP_MAX_RETRIES) {
            api_sleep_milliseconds(MES_HTTP_RETRY_DELAY_MILLISECONDS);
        }
    }
    return result;
}

static int api_append_record(const char *file_name,
                             const char *path,
                             const char *json)
{
    FILE *file = fopen(file_name, "ab");

    if (file == NULL) {
        return -1;
    }
    if (fprintf(file, "%s\t%s\n", path, json) < 0 || fflush(file) != 0) {
        fclose(file);
        return -1;
    }
    return fclose(file) == 0 ? 0 : -1;
}

static int api_queue_append(const char *path, const char *json)
{
    int result;

    if (!queue_initialized) {
        return -1;
    }
    collector_mutex_lock(&queue_mutex);
    result = api_append_record(MES_HTTP_RETRY_QUEUE_FILE, path, json);
    collector_mutex_unlock(&queue_mutex);
    return result;
}

static void api_dead_letter_append(const char *path, const char *json)
{
    if (!queue_initialized) {
        return;
    }
    collector_mutex_lock(&queue_mutex);
    if (api_append_record(MES_HTTP_DEAD_LETTER_FILE, path, json) != 0) {
        fprintf(stderr,
                "[L2 HTTP Queue] failed to write dead-letter file=%s\n",
                MES_HTTP_DEAD_LETTER_FILE);
    }
    collector_mutex_unlock(&queue_mutex);
}

static void api_process_queue_once(void)
{
    FILE *source;
    FILE *target;
    char line[API_QUEUE_LINE_CAPACITY];
    char temp_file[256];
    int remaining = 0;
    int delivered = 0;
    int dead_lettered = 0;

    if (!queue_initialized) {
        return;
    }
    collector_mutex_lock(&queue_mutex);
    source = fopen(MES_HTTP_RETRY_QUEUE_FILE, "rb");
    if (source == NULL) {
        collector_mutex_unlock(&queue_mutex);
        return;
    }
    if (snprintf(temp_file,
                 sizeof(temp_file),
                 "%s.tmp",
                 MES_HTTP_RETRY_QUEUE_FILE) < 0) {
        fclose(source);
        collector_mutex_unlock(&queue_mutex);
        return;
    }
    target = fopen(temp_file, "wb");
    if (target == NULL) {
        fclose(source);
        collector_mutex_unlock(&queue_mutex);
        return;
    }

    while (fgets(line, sizeof(line), source) != NULL) {
        char *separator = strchr(line, '\t');
        char *json;
        size_t length;
        int http_status = 0;
        ApiClientResult result;

        if (separator == NULL) {
            api_append_record(MES_HTTP_DEAD_LETTER_FILE, "INVALID", line);
            ++dead_lettered;
            continue;
        }
        *separator = '\0';
        json = separator + 1;
        length = strlen(json);
        while (length > 0 && (json[length - 1] == '\n' || json[length - 1] == '\r')) {
            json[--length] = '\0';
        }
        result = api_client_post_with_retries(line, json, &http_status);
        if (result == API_CLIENT_OK) {
            ++delivered;
            continue;
        }
        if (result == API_CLIENT_HTTP_CLIENT_ERROR
            || result == API_CLIENT_HTTP_UNEXPECTED_STATUS) {
            api_append_record(MES_HTTP_DEAD_LETTER_FILE, line, json);
            ++dead_lettered;
            continue;
        }
        fprintf(target, "%s\t%s\n", line, json);
        ++remaining;
    }
    fclose(source);
    fclose(target);

    if (remaining > 0) {
#ifdef _WIN32
        if (!MoveFileExA(temp_file,
                         MES_HTTP_RETRY_QUEUE_FILE,
                         MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH)) {
#else
        if (rename(temp_file, MES_HTTP_RETRY_QUEUE_FILE) != 0) {
#endif
            fprintf(stderr,
                    "[L2 HTTP Queue] failed to replace queue file. temp=%s\n",
                    temp_file);
        }
    } else {
        remove(MES_HTTP_RETRY_QUEUE_FILE);
        remove(temp_file);
    }
    collector_mutex_unlock(&queue_mutex);

    if (delivered > 0 || dead_lettered > 0) {
        printf("[L2 HTTP Queue] delivered=%d dead-letter=%d remaining=%d\n",
               delivered,
               dead_lettered,
               remaining);
        fflush(stdout);
    }
}

static void api_queue_worker(void *context)
{
    unsigned int waited;

    (void)context;
    while (queue_running) {
        waited = 0;
        while (queue_running
               && waited < MES_HTTP_QUEUE_RETRY_INTERVAL_SECONDS * 1000U) {
            api_sleep_milliseconds(100U);
            waited += 100U;
        }
        if (queue_running) {
            api_process_queue_once();
        }
    }
    queue_worker_active = 0;
}

static const char *api_message_machine_id(const ProtocolMessage *message)
{
    switch (message->type) {
    case PROTOCOL_EVENT_PRODUCTION:
        return message->data.production.machine_id;
    case PROTOCOL_EVENT_INSPECTION:
        return message->data.inspection.machine_id;
    case PROTOCOL_EVENT_JUDGMENT:
        return message->data.judgment.machine_id;
    case PROTOCOL_EVENT_DEFECT:
        return message->data.defect.machine_id;
    case PROTOCOL_EVENT_ALARM:
        return message->data.alarm.machine_id;
    case PROTOCOL_EVENT_MACHINE_STATUS:
        return message->data.machine_status.machine_id;
    case PROTOCOL_EVENT_COMMAND_ACK:
        return message->data.command_ack.machine_id;
    default:
        return "UNKNOWN";
    }
}

static int api_generate_event_id(const ProtocolMessage *message,
                                 char *event_id,
                                 size_t capacity)
{
    unsigned long sequence;
    uint64_t monotonic_ms;
    long process_id;
    time_t now = time(NULL);
    char now_text[22];
    char monotonic_text[21];
    int written;

    if (message == NULL || event_id == NULL || capacity == 0) {
        return -1;
    }
    collector_mutex_lock(&queue_mutex);
    sequence = ++event_sequence;
    collector_mutex_unlock(&queue_mutex);
    monotonic_ms = (uint64_t)net_monotonic_milliseconds();
    if (api_int64_to_text((int64_t)now,
                          now_text,
                          sizeof(now_text)) != 0
        || api_uint64_to_text(monotonic_ms,
                              monotonic_text,
                              sizeof(monotonic_text)) != 0) {
        return -1;
    }
#ifdef _WIN32
    process_id = (long)_getpid();
#else
    process_id = (long)getpid();
#endif
    written = snprintf(event_id,
                       capacity,
                       "L2-%s-%s-%s-%s-%ld-%lu",
                       api_message_machine_id(message),
                       protocol_event_type_name(message->type),
                       now_text,
                       monotonic_text,
                       process_id,
                       sequence);
    return written >= 0 && (size_t)written < capacity ? 0 : -1;
}

int api_client_init(void)
{
    if (queue_initialized) {
        return 0;
    }
    if (collector_mutex_init(&queue_mutex) != 0) {
        return -1;
    }
    queue_initialized = 1;
    queue_running = 1;
    queue_worker_active = 1;
    event_sequence = 0;
    if (collector_thread_start_detached(api_queue_worker, NULL) != 0) {
        queue_running = 0;
        queue_worker_active = 0;
        queue_initialized = 0;
        collector_mutex_destroy(&queue_mutex);
        return -1;
    }
    return 0;
}

void api_client_cleanup(void)
{
    if (!queue_initialized) {
        return;
    }
    queue_running = 0;
    while (queue_worker_active) {
        api_sleep_milliseconds(100U);
    }
    collector_mutex_destroy(&queue_mutex);
    queue_initialized = 0;
}

ApiClientResult api_client_send_event(const ProtocolMessage *message,
                                      int *http_status)
{
    char path[API_CLIENT_PATH_CAPACITY];
    char json[API_CLIENT_JSON_CAPACITY];
    char event_id[API_EVENT_ID_CAPACITY];
    const char *event_id_value = NULL;
    ApiClientResult result;

    if (!queue_initialized && api_client_init() != 0) {
        return API_CLIENT_QUEUE_ERROR;
    }
    if (message != NULL
        && message->type != PROTOCOL_EVENT_COMMAND_ACK
        && message->type != PROTOCOL_EVENT_HELLO
        && message->type != PROTOCOL_EVENT_HEARTBEAT) {
        if (api_generate_event_id(message, event_id, sizeof(event_id)) != 0) {
            return API_CLIENT_BUFFER_TOO_SMALL;
        }
        event_id_value = event_id;
    }
    result = api_client_build_event_request_internal(message,
                                                     event_id_value,
                                                     path,
                                                     sizeof(path),
                                                     json,
                                                     sizeof(json));
    if (http_status != NULL) {
        *http_status = 0;
    }
    if (result != API_CLIENT_OK) {
        return result;
    }

    result = api_client_post_with_retries(path, json, http_status);
    if (result == API_CLIENT_OK) {
        return result;
    }
    if (api_result_is_retryable(result)) {
        if (api_queue_append(path, json) == 0) {
            return API_CLIENT_QUEUED;
        }
        return API_CLIENT_QUEUE_ERROR;
    }
    if (result == API_CLIENT_HTTP_CLIENT_ERROR
        || result == API_CLIENT_HTTP_UNEXPECTED_STATUS) {
        api_dead_letter_append(path, json);
    }
    return result;
}

const char *api_client_result_name(ApiClientResult result)
{
    switch (result) {
    case API_CLIENT_OK:
        return "OK";
    case API_CLIENT_QUEUED:
        return "QUEUED";
    case API_CLIENT_SKIPPED:
        return "SKIPPED";
    case API_CLIENT_INVALID_ARGUMENT:
        return "INVALID_ARGUMENT";
    case API_CLIENT_BUFFER_TOO_SMALL:
        return "BUFFER_TOO_SMALL";
    case API_CLIENT_CONNECT_ERROR:
        return "CONNECT_ERROR";
    case API_CLIENT_IO_ERROR:
        return "IO_ERROR";
    case API_CLIENT_INVALID_RESPONSE:
        return "INVALID_RESPONSE";
    case API_CLIENT_HTTP_CLIENT_ERROR:
        return "HTTP_CLIENT_ERROR";
    case API_CLIENT_HTTP_SERVER_ERROR:
        return "HTTP_SERVER_ERROR";
    case API_CLIENT_HTTP_UNEXPECTED_STATUS:
        return "HTTP_UNEXPECTED_STATUS";
    case API_CLIENT_QUEUE_ERROR:
        return "QUEUE_ERROR";
    default:
        return "UNKNOWN";
    }
}
