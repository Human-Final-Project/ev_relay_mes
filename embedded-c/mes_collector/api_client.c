#include "api_client.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "config.h"
#include "net.h"

#define API_PATH_PRODUCTION "/api/collector/production-logs"
#define API_PATH_INSPECTION "/api/collector/inspections"
#define API_PATH_DEFECT "/api/collector/defects"
#define API_PATH_ALARM "/api/collector/machine-alarms"
#define API_PATH_MACHINE_STATUS "/api/collector/machine-statuses"
#define API_PATH_COMMAND_ACK "/api/collector/command-acks"

typedef struct {
    char *buffer;
    size_t capacity;
    size_t length;
    int failed;
} JsonBuilder;

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
    json_append_field_name(builder, name, first);
    json_append_format(builder, "%lld", (long long)value);
}

static void json_append_double_field(JsonBuilder *builder,
                                     const char *name,
                                     double value,
                                     int *first)
{
    json_append_field_name(builder, name, first);
    json_append_format(builder, "%.15g", value);
}

static void json_append_optional_double_field(JsonBuilder *builder,
                                              const char *name,
                                              int has_value,
                                              double value,
                                              int *first)
{
    json_append_field_name(builder, name, first);
    if (has_value) {
        json_append_format(builder, "%.15g", value);
    } else {
        json_append_format(builder, "null");
    }
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

ApiClientResult api_client_build_event_request(
    const ProtocolMessage *message,
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
        json_append_string_field(&builder, "inspectionItem", event->item, &first);
        json_append_double_field(&builder, "measuredValue", event->value, &first);
        json_append_optional_string_field(&builder, "unit", event->unit, &first);
        json_append_optional_double_field(&builder,
                                          "lowerLimit",
                                          event->has_lower_limit,
                                          event->lower_limit,
                                          &first);
        json_append_optional_double_field(&builder,
                                          "upperLimit",
                                          event->has_upper_limit,
                                          event->upper_limit,
                                          &first);
        json_append_string_field(&builder, "result", event->result, &first);
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

        path_result = copy_path(path, path_capacity, API_PATH_ALARM);
        json_append_string_field(&builder, "machineId", event->machine_id, &first);
        json_append_string_field(&builder, "alarmCode", event->alarm_code, &first);
        json_append_string_field(&builder, "alarmLevel", alarm_level, &first);
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

int api_client_init(void)
{
    return 0;
}

void api_client_cleanup(void)
{
}

ApiClientResult api_client_send_event(const ProtocolMessage *message,
                                      int *http_status)
{
    char path[API_CLIENT_PATH_CAPACITY];
    char json[API_CLIENT_JSON_CAPACITY];
    ApiClientResult result = api_client_build_event_request(message,
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
    return api_client_post_json(path, json, http_status);
}

const char *api_client_result_name(ApiClientResult result)
{
    switch (result) {
    case API_CLIENT_OK:
        return "OK";
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
    default:
        return "UNKNOWN";
    }
}
