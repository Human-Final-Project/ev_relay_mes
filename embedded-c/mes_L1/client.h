#ifndef EV_RELAY_L1_CLIENT_H
#define EV_RELAY_L1_CLIENT_H

#include <stddef.h>
#include <stdint.h>

#include "device_config.h"
#include "protocol.h"

typedef enum {
    L1_CLIENT_FEED_OK = 0,
    L1_CLIENT_FEED_INVALID_ARGUMENT,
    L1_CLIENT_FEED_PROTOCOL_ERROR,
    L1_CLIENT_FEED_MACHINE_MISMATCH,
    L1_CLIENT_FEED_MESSAGE_TOO_LONG
} L1ClientFeedResult;

typedef void (*L1ClientCommandHandler)(const L1Command *command,
                                       void *context);
typedef void (*L1ClientErrorHandler)(L1ClientFeedResult error,
                                     L1ProtocolResult protocol_result,
                                     const char *line,
                                     void *context);

typedef struct {
    L1ClientCommandHandler on_command;
    L1ClientErrorHandler on_error;
    void *context;
} L1ClientHandlers;

typedef struct {
    const L1DeviceConfig *device;
    char line_buffer[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t line_length;
    int discarding_oversized_message;
} L1ClientSession;

void l1_client_session_init(L1ClientSession *session,
                            const L1DeviceConfig *device);

/*
 * Adds arbitrary TCP bytes to the session. A recv() call may contain a
 * partial COMMAND or several COMMAND messages, so LF framing is handled here.
 */
L1ClientFeedResult l1_client_session_feed(
    L1ClientSession *session,
    const void *data,
    size_t length,
    const L1ClientHandlers *handlers);

/* Keeps one selected L1 device connected to L2 until externally stopped. */
int l1_client_run(const L1DeviceConfig *device,
                  const char *server_address,
                  uint16_t server_port,
                  int error_after_qty);

const char *l1_client_feed_result_name(L1ClientFeedResult result);

#endif
