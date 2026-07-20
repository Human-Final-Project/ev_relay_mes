#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "api_client.h"
#include "config.h"
#include "net.h"
#include "thread_compat.h"

static int checks_run;
static int checks_failed;

typedef struct {
    NetSocket server_socket;
    CollectorMutex mutex;
    char request[8192];
    size_t request_length;
    const char *response;
} MockHttpServer;

#define CHECK(condition)                                                     \
    do {                                                                     \
        ++checks_run;                                                        \
        if (!(condition)) {                                                  \
            ++checks_failed;                                                 \
            fprintf(stderr,                                                  \
                    "FAIL %s:%d: %s\n",                                    \
                    __FILE__,                                                \
                    __LINE__,                                                \
                    #condition);                                             \
        }                                                                    \
    } while (0)

static ApiClientResult build(const ProtocolMessage *message,
                             char *path,
                             char *json)
{
    return api_client_build_event_request(message,
                                          path,
                                          API_CLIENT_PATH_CAPACITY,
                                          json,
                                          API_CLIENT_JSON_CAPACITY);
}

static void run_mock_http_server(void *context)
{
    MockHttpServer *server = (MockHttpServer *)context;
    NetSocket server_socket = server->server_socket;
    NetSocket client_socket = net_accept_client(server_socket,
                                                NULL,
                                                0,
                                                NULL);
    char request[8192];
    size_t received_total = 0;
    size_t expected_total = 0;
    const char default_response[] =
        "HTTP/1.1 201 Created\r\n"
        "Content-Length: 2\r\n"
        "Connection: close\r\n\r\n{}";
    const char *response = server->response != NULL
        ? server->response
        : default_response;

    if (client_socket == NET_INVALID_SOCKET) {
        net_socket_close(server_socket);
        return;
    }
    net_socket_close(server_socket);
    while (received_total + 1 < sizeof(request)) {
        int received = net_receive(client_socket,
                                   request + received_total,
                                   sizeof(request) - received_total - 1);

        if (received <= 0) {
            break;
        }
        received_total += (size_t)received;
        request[received_total] = '\0';
        if (expected_total == 0) {
            char *header_end = strstr(request, "\r\n\r\n");
            char *content_length = strstr(request, "Content-Length:");

            if (header_end != NULL) {
                expected_total = (size_t)(header_end + 4 - request);
                if (content_length != NULL) {
                    unsigned long body_length = strtoul(
                        content_length + strlen("Content-Length:"),
                        NULL,
                        10);

                    expected_total += (size_t)body_length;
                }
            }
        }
        if (expected_total > 0 && received_total >= expected_total) {
            break;
        }
    }

    collector_mutex_lock(&server->mutex);
    if (received_total >= sizeof(server->request)) {
        received_total = sizeof(server->request) - 1;
    }
    memcpy(server->request, request, received_total);
    server->request[received_total] = '\0';
    server->request_length = received_total;
    collector_mutex_unlock(&server->mutex);

    net_send_all(client_socket, response, strlen(response));
    net_socket_close(client_socket);
}

static void test_production_json(void)
{
    ProtocolMessage message;
    char path[API_CLIENT_PATH_CAPACITY];
    char json[API_CLIENT_JSON_CAPACITY];

    memset(&message, 0, sizeof(message));
    message.type = PROTOCOL_EVENT_PRODUCTION;
    strcpy(message.data.production.lot_no, "EVR-LOT-001");
    strcpy(message.data.production.machine_id, "EQ-WIND-01");
    strcpy(message.data.production.process_code, "OP20");
    message.data.production.input_qty = 100;
    message.data.production.ok_qty = 97;
    message.data.production.ng_qty = 3;
    strcpy(message.data.production.status, "COMPLETED");

    CHECK(build(&message, path, json) == API_CLIENT_OK);
    CHECK(strcmp(path, "/api/collector/production-logs") == 0);
    CHECK(strcmp(json,
                 "{\"lotNo\":\"EVR-LOT-001\","
                 "\"machineId\":\"EQ-WIND-01\","
                 "\"processCode\":\"OP20\","
                 "\"inputQty\":100,\"okQty\":97,\"ngQty\":3,"
                 "\"status\":\"COMPLETED\"}") == 0);
}

static void test_inspection_json_with_null_limit(void)
{
    ProtocolMessage message;
    char path[API_CLIENT_PATH_CAPACITY];
    char json[API_CLIENT_JSON_CAPACITY];

    memset(&message, 0, sizeof(message));
    message.type = PROTOCOL_EVENT_INSPECTION;
    strcpy(message.data.inspection.lot_no, "EVR-LOT-001");
    strcpy(message.data.inspection.machine_id, "EQ-TEST-01");
    strcpy(message.data.inspection.process_code, "OP70");
    strcpy(message.data.inspection.item, "operationVoltage");
    message.data.inspection.value = 12.0;
    strcpy(message.data.inspection.unit, "V");
    message.data.inspection.has_lower_limit = 0;
    message.data.inspection.has_upper_limit = 1;
    message.data.inspection.upper_limit = 14.0;
    strcpy(message.data.inspection.result, "OK");

    CHECK(build(&message, path, json) == API_CLIENT_OK);
    CHECK(strcmp(path, "/api/collector/inspections") == 0);
    CHECK(strstr(json, "\"inspectionItem\":\"operationVoltage\"") != NULL);
    CHECK(strstr(json, "\"measuredValue\":12") != NULL);
    CHECK(strstr(json, "\"lowerLimit\":null") != NULL);
    CHECK(strstr(json, "\"upperLimit\":14") != NULL);
}

static void test_defect_json_escapes_message(void)
{
    ProtocolMessage message;
    char path[API_CLIENT_PATH_CAPACITY];
    char json[API_CLIENT_JSON_CAPACITY];

    memset(&message, 0, sizeof(message));
    message.type = PROTOCOL_EVENT_DEFECT;
    strcpy(message.data.defect.lot_no, "EVR-LOT-001");
    strcpy(message.data.defect.machine_id, "EQ-WELD-01");
    strcpy(message.data.defect.process_code, "OP30");
    strcpy(message.data.defect.defect_code, "WELD_STRENGTH_NG");
    message.data.defect.defect_qty = 3;
    strcpy(message.data.defect.message, "quote_\"_slash_\\");

    CHECK(build(&message, path, json) == API_CLIENT_OK);
    CHECK(strcmp(path, "/api/collector/defects") == 0);
    CHECK(strstr(json, "\"defectQty\":3") != NULL);
    CHECK(strstr(json, "quote_\\\"_slash_\\\\") != NULL);
}

static void test_alarm_warning_maps_to_backend_warn(void)
{
    ProtocolMessage message;
    char path[API_CLIENT_PATH_CAPACITY];
    char json[API_CLIENT_JSON_CAPACITY];

    memset(&message, 0, sizeof(message));
    message.type = PROTOCOL_EVENT_ALARM;
    strcpy(message.data.alarm.machine_id, "EQ-WIND-01");
    strcpy(message.data.alarm.alarm_code, "WIRE_BREAK");
    strcpy(message.data.alarm.alarm_level, "WARNING");
    strcpy(message.data.alarm.message, "-");

    CHECK(build(&message, path, json) == API_CLIENT_OK);
    CHECK(strcmp(path, "/api/collector/machine-alarms") == 0);
    CHECK(strstr(json, "\"alarmLevel\":\"WARN\"") != NULL);
    CHECK(strstr(json, "\"message\":null") != NULL);
}

static void test_machine_status_hyphen_becomes_null(void)
{
    ProtocolMessage message;
    char path[API_CLIENT_PATH_CAPACITY];
    char json[API_CLIENT_JSON_CAPACITY];

    memset(&message, 0, sizeof(message));
    message.type = PROTOCOL_EVENT_MACHINE_STATUS;
    strcpy(message.data.machine_status.machine_id, "EQ-WIND-01");
    strcpy(message.data.machine_status.status, "IDLE");
    strcpy(message.data.machine_status.lot_no, "-");
    strcpy(message.data.machine_status.process_code, "OP20");
    strcpy(message.data.machine_status.message, "production_finished");

    CHECK(build(&message, path, json) == API_CLIENT_OK);
    CHECK(strcmp(path, "/api/collector/machine-statuses") == 0);
    CHECK(strstr(json, "\"lotNo\":null") != NULL);
    CHECK(strstr(json, "\"processCode\":\"OP20\"") != NULL);
}

static void test_command_ack_json(void)
{
    ProtocolMessage message;
    char path[API_CLIENT_PATH_CAPACITY];
    char json[API_CLIENT_JSON_CAPACITY];

    memset(&message, 0, sizeof(message));
    message.type = PROTOCOL_EVENT_COMMAND_ACK;
    strcpy(message.data.command_ack.machine_id, "EQ-WIND-01");
    message.data.command_ack.command_id = 101;
    strcpy(message.data.command_ack.ack_status, "ACCEPTED");
    strcpy(message.data.command_ack.message, "-");

    CHECK(build(&message, path, json) == API_CLIENT_OK);
    CHECK(strcmp(path, "/api/collector/command-acks") == 0);
    CHECK(strstr(json, "\"commandId\":101") != NULL);
    CHECK(strstr(json, "\"message\":null") != NULL);
}

static void test_connection_events_are_skipped(void)
{
    ProtocolMessage message;
    char path[API_CLIENT_PATH_CAPACITY];
    char json[API_CLIENT_JSON_CAPACITY];

    memset(&message, 0, sizeof(message));
    message.type = PROTOCOL_EVENT_HELLO;
    CHECK(build(&message, path, json) == API_CLIENT_SKIPPED);
    CHECK(path[0] == '\0');
    CHECK(json[0] == '\0');

    message.type = PROTOCOL_EVENT_HEARTBEAT;
    CHECK(build(&message, path, json) == API_CLIENT_SKIPPED);
}

static void test_small_buffer_is_rejected(void)
{
    ProtocolMessage message;
    char path[4];
    char json[4];

    memset(&message, 0, sizeof(message));
    message.type = PROTOCOL_EVENT_PRODUCTION;
    CHECK(api_client_build_event_request(&message,
                                         path,
                                         sizeof(path),
                                         json,
                                         sizeof(json))
          == API_CLIENT_BUFFER_TOO_SMALL);
}

static void test_http_post_receives_created_response(void)
{
    ProtocolMessage message;
    MockHttpServer server;
    CollectorThread server_thread = {0};
    int http_status = 0;
    ApiClientResult result;

    memset(&server, 0, sizeof(server));
    CHECK(protocol_parse_message(
              "V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-001,10,9,1,COMPLETED\n",
              &message) == PROTOCOL_RESULT_OK);

    CHECK(collector_mutex_init(&server.mutex) == 0);
    server.server_socket = net_tcp_server_create(MES_BACKEND_ADDRESS,
                                                 MES_BACKEND_PORT,
                                                 1);
    CHECK(server.server_socket != NET_INVALID_SOCKET);
    if (server.server_socket == NET_INVALID_SOCKET) {
        collector_mutex_destroy(&server.mutex);
        return;
    }
    CHECK(collector_thread_start(&server_thread,
                                 run_mock_http_server,
                                 &server) == 0);
    result = api_client_send_event(&message, &http_status);
    CHECK(result == API_CLIENT_OK);
    CHECK(http_status == 201);
    CHECK(collector_thread_join(&server_thread) == 0);

    collector_mutex_lock(&server.mutex);
    CHECK(server.request_length > 0);
    CHECK(strstr(server.request,
                 "POST /api/collector/production-logs HTTP/1.1\r\n")
          == server.request);
    CHECK(strstr(server.request,
                 "Content-Type: application/json; charset=UTF-8\r\n")
          != NULL);
    CHECK(strstr(server.request,
                 "\r\n\r\n{\"lotNo\":\"EVR-LOT-001\"")
          != NULL);
    collector_mutex_unlock(&server.mutex);
    collector_mutex_destroy(&server.mutex);
}

static void test_http_get_pending_commands(void)
{
    const char body[] =
        "[{\"commandId\":101,\"commandType\":\"START\","
        "\"machineId\":\"EQ-WIND-01\",\"processCode\":\"OP20\","
        "\"lotNo\":\"EVR-LOT-001\",\"inputQty\":100,"
        "\"status\":\"DISPATCHED\"}]";
    char response[4096];
    char json[API_CLIENT_PENDING_JSON_CAPACITY];
    size_t json_length = 0;
    MockHttpServer server;
    CollectorThread server_thread = {0};
    int http_status = 0;
    ApiClientResult result;

    memset(&server, 0, sizeof(server));
    snprintf(response,
             sizeof(response),
             "HTTP/1.1 200 OK\r\n"
             "Content-Type: application/json\r\n"
             "Content-Length: %u\r\n"
             "Connection: close\r\n\r\n%s",
             (unsigned int)strlen(body),
             body);
    server.response = response;
    CHECK(collector_mutex_init(&server.mutex) == 0);
    server.server_socket = net_tcp_server_create(MES_BACKEND_ADDRESS,
                                                 MES_BACKEND_PORT,
                                                 1);
    CHECK(server.server_socket != NET_INVALID_SOCKET);
    if (server.server_socket == NET_INVALID_SOCKET) {
        collector_mutex_destroy(&server.mutex);
        return;
    }
    CHECK(collector_thread_start(&server_thread,
                                 run_mock_http_server,
                                 &server) == 0);
    result = api_client_get_pending_commands(json,
                                             sizeof(json),
                                             &json_length,
                                             &http_status);
    CHECK(result == API_CLIENT_OK);
    CHECK(http_status == 200);
    CHECK(json_length == strlen(body));
    CHECK(strcmp(json, body) == 0);
    CHECK(collector_thread_join(&server_thread) == 0);

    collector_mutex_lock(&server.mutex);
    CHECK(strstr(server.request,
                 "GET /api/collector/commands/pending HTTP/1.1\r\n")
          == server.request);
    CHECK(strstr(server.request, "Accept: application/json\r\n") != NULL);
    collector_mutex_unlock(&server.mutex);
    collector_mutex_destroy(&server.mutex);
}

static void test_http_get_decodes_chunked_body(void)
{
    const char response[] =
        "HTTP/1.1 200 OK\r\n"
        "Transfer-Encoding: chunked\r\n"
        "Connection: close\r\n\r\n"
        "1\r\n[\r\n"
        "1\r\n]\r\n"
        "0\r\n\r\n";
    char json[16];
    size_t json_length = 0;
    MockHttpServer server;
    CollectorThread server_thread = {0};
    int http_status = 0;

    memset(&server, 0, sizeof(server));
    server.response = response;
    CHECK(collector_mutex_init(&server.mutex) == 0);
    server.server_socket = net_tcp_server_create(MES_BACKEND_ADDRESS,
                                                 MES_BACKEND_PORT,
                                                 1);
    CHECK(server.server_socket != NET_INVALID_SOCKET);
    if (server.server_socket == NET_INVALID_SOCKET) {
        collector_mutex_destroy(&server.mutex);
        return;
    }
    CHECK(collector_thread_start(&server_thread,
                                 run_mock_http_server,
                                 &server) == 0);
    CHECK(api_client_get_pending_commands(json,
                                          sizeof(json),
                                          &json_length,
                                          &http_status) == API_CLIENT_OK);
    CHECK(http_status == 200);
    CHECK(json_length == 2);
    CHECK(strcmp(json, "[]") == 0);
    CHECK(collector_thread_join(&server_thread) == 0);
    collector_mutex_destroy(&server.mutex);
}

int main(void)
{
    if (net_runtime_init() != 0) {
        fprintf(stderr, "Failed to initialize network runtime.\n");
        return EXIT_FAILURE;
    }
    test_production_json();
    test_inspection_json_with_null_limit();
    test_defect_json_escapes_message();
    test_alarm_warning_maps_to_backend_warn();
    test_machine_status_hyphen_becomes_null();
    test_command_ack_json();
    test_connection_events_are_skipped();
    test_small_buffer_is_rejected();
    test_http_post_receives_created_response();
    test_http_get_pending_commands();
    test_http_get_decodes_chunked_body();

    net_runtime_cleanup();

    if (checks_failed != 0) {
        fprintf(stderr,
                "%d of %d API client checks failed.\n",
                checks_failed,
                checks_run);
        return EXIT_FAILURE;
    }
    printf("PASS: %d API client checks\n", checks_run);
    return EXIT_SUCCESS;
}
