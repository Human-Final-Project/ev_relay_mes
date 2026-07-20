#ifndef MES_COLLECTOR_API_CLIENT_H
#define MES_COLLECTOR_API_CLIENT_H

#include <stddef.h>
#include <stdint.h>

#include "protocol.h"

#define API_CLIENT_PATH_CAPACITY 128
#define API_CLIENT_JSON_CAPACITY 4096
#define API_CLIENT_RESPONSE_CAPACITY 32768
#define API_CLIENT_MAX_COMMANDS 6

typedef enum {
    API_CLIENT_OK = 0,
    API_CLIENT_QUEUED,
    API_CLIENT_SKIPPED,
    API_CLIENT_INVALID_ARGUMENT,
    API_CLIENT_BUFFER_TOO_SMALL,
    API_CLIENT_CONNECT_ERROR,
    API_CLIENT_IO_ERROR,
    API_CLIENT_INVALID_RESPONSE,
    API_CLIENT_HTTP_CLIENT_ERROR,
    API_CLIENT_HTTP_SERVER_ERROR,
    API_CLIENT_HTTP_UNEXPECTED_STATUS,
    API_CLIENT_QUEUE_ERROR
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

/* Sends with retry. Retryable failures are persisted for background replay. */
ApiClientResult api_client_send_event(const ProtocolMessage *message,
                                      int *http_status);

/* Parses the JSON array returned by GET /commands/pending. */
ApiClientResult api_client_parse_command_response(
    const char *json,
    ProtocolCommand *commands,
    size_t command_capacity,
    size_t *command_count);

/* Claims pending Backend commands for one connected MACHINE_ID. */
ApiClientResult api_client_fetch_pending_commands(
    const char *machine_id,
    ProtocolCommand *commands,
    size_t command_capacity,
    size_t *command_count,
    int *http_status);

/* Returns a DISPATCHED command to PENDING when L2 could not send it to L1. */
ApiClientResult api_client_release_command(int64_t command_id,
                                           const char *machine_id,
                                           int *http_status);

const char *api_client_result_name(ApiClientResult result);

#endif
