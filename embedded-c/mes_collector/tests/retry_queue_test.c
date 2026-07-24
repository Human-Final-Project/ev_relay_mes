#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <sys/select.h>
#endif

#include "api_client.h"
#include "config.h"
#include "net.h"
#include "protocol.h"
#include "thread_compat.h"

typedef struct {
    NetSocket server_socket;
    volatile int request_received;
} RetryMockServer;

static void sleep_milliseconds(unsigned int milliseconds)
{
#ifdef _WIN32
    Sleep(milliseconds);
#else
    struct timeval timeout;

    timeout.tv_sec = (long)(milliseconds / 1000U);
    timeout.tv_usec = (long)(milliseconds % 1000U) * 1000L;
    select(0, NULL, NULL, NULL, &timeout);
#endif
}

static long file_size(const char *path)
{
    FILE *file = fopen(path, "rb");
    long size;

    if (file == NULL) {
        return -1;
    }
    if (fseek(file, 0, SEEK_END) != 0) {
        fclose(file);
        return -1;
    }
    size = ftell(file);
    fclose(file);
    return size;
}

static int file_contains(const char *path, const char *needle)
{
    FILE *file = fopen(path, "rb");
    char buffer[8192];
    size_t length;

    if (file == NULL) {
        return 0;
    }
    length = fread(buffer, 1, sizeof(buffer) - 1, file);
    fclose(file);
    buffer[length] = '\0';
    return strstr(buffer, needle) != NULL;
}

static void run_mock_server(void *context)
{
    RetryMockServer *server = (RetryMockServer *)context;
    NetSocket client = net_accept_client(server->server_socket, NULL, 0, NULL);
    char request[8192];
    size_t total = 0;
    const char response[] =
        "HTTP/1.1 201 Created\r\n"
        "Content-Length: 2\r\n"
        "Connection: close\r\n\r\n{}";

    if (client == NET_INVALID_SOCKET) {
        net_socket_close(server->server_socket);
        return;
    }
    while (total + 1 < sizeof(request)) {
        int received = net_receive(client, request + total, sizeof(request) - total - 1);
        if (received <= 0) {
            break;
        }
        total += (size_t)received;
        request[total] = '\0';
        if (strstr(request, "\r\n\r\n") != NULL
            && strstr(request, "\"eventId\":\"L2-") != NULL) {
            break;
        }
    }
    server->request_received = 1;
    net_send_all(client, response, sizeof(response) - 1);
    net_socket_close(client);
    net_socket_close(server->server_socket);
}

int main(void)
{
    ProtocolMessage message;
    RetryMockServer server;
    ApiClientResult result;
    int status = 0;
    unsigned int waited = 0;

    remove(MES_HTTP_RETRY_QUEUE_FILE);
    remove(MES_HTTP_DEAD_LETTER_FILE);
    memset(&server, 0, sizeof(server));

    if (net_runtime_init() != 0 || api_client_init() != 0) {
        fprintf(stderr, "FAIL: initialization\n");
        return EXIT_FAILURE;
    }
    if (protocol_parse_message(
            "V1,PRODUCTION,EQ-WIND-01,OP20,LOT-RETRY-001,10,9,1,COMPLETED\n",
            &message) != PROTOCOL_RESULT_OK) {
        fprintf(stderr, "FAIL: protocol parse\n");
        return EXIT_FAILURE;
    }

    result = api_client_send_event(&message, &status);
    if (result != API_CLIENT_QUEUED
        || file_size(MES_HTTP_RETRY_QUEUE_FILE) <= 0
        || !file_contains(MES_HTTP_RETRY_QUEUE_FILE, "\"eventId\":\"L2-")
        || !file_contains(MES_HTTP_RETRY_QUEUE_FILE, "\"lotNo\":\"LOT-RETRY-001\"")
        || !file_contains(MES_HTTP_RETRY_QUEUE_FILE, "\"machineId\":\"EQ-WIND-01\"")
        || !file_contains(MES_HTTP_RETRY_QUEUE_FILE, "\"processCode\":\"OP20\"")) {
        fprintf(stderr,
                "FAIL: event was not durably queued result=%s status=%d\n",
                api_client_result_name(result),
                status);
        return EXIT_FAILURE;
    }

    server.server_socket = net_tcp_server_create(MES_BACKEND_ADDRESS,
                                                 MES_BACKEND_PORT,
                                                 1);
    if (server.server_socket == NET_INVALID_SOCKET
        || collector_thread_start_detached(run_mock_server, &server) != 0) {
        fprintf(stderr, "FAIL: mock backend start\n");
        return EXIT_FAILURE;
    }

    while (waited < 10000U) {
        if (server.request_received && file_size(MES_HTTP_RETRY_QUEUE_FILE) <= 0) {
            break;
        }
        sleep_milliseconds(100U);
        waited += 100U;
    }

    api_client_cleanup();
    net_runtime_cleanup();
    remove(MES_HTTP_RETRY_QUEUE_FILE);
    remove(MES_HTTP_DEAD_LETTER_FILE);

    if (!server.request_received || waited >= 10000U) {
        fprintf(stderr, "FAIL: queued event was not replayed\n");
        return EXIT_FAILURE;
    }
    printf("PASS: durable HTTP queue replayed the event\n");
    return EXIT_SUCCESS;
}
