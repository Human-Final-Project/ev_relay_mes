#ifndef MES_COLLECTOR_API_CLIENT_H
#define MES_COLLECTOR_API_CLIENT_H

#include <stddef.h>

#include "protocol.h"

#define API_CLIENT_PATH_CAPACITY 64
#define API_CLIENT_JSON_CAPACITY 4096

typedef enum {
    API_CLIENT_OK = 0,
    API_CLIENT_SKIPPED,
    API_CLIENT_INVALID_ARGUMENT,
    API_CLIENT_BUFFER_TOO_SMALL,
    API_CLIENT_CONNECT_ERROR,
    API_CLIENT_IO_ERROR,
    API_CLIENT_INVALID_RESPONSE,
    API_CLIENT_HTTP_CLIENT_ERROR,
    API_CLIENT_HTTP_SERVER_ERROR,
    API_CLIENT_HTTP_UNEXPECTED_STATUS
} ApiClientResult;

/* Prepares and releases the L2 -> Backend HTTP client. */
int api_client_init(void);
void api_client_cleanup(void);

/* Converts one validated L1 event into its Backend path and JSON body. */
ApiClientResult api_client_build_event_request(
    const ProtocolMessage *message,
    char *path,
    size_t path_capacity,
    char *json,
    size_t json_capacity);

/* Sends one event once. HELLO and HEARTBEAT return API_CLIENT_SKIPPED. */
ApiClientResult api_client_send_event(const ProtocolMessage *message,
                                      int *http_status);

const char *api_client_result_name(ApiClientResult result);

#endif
