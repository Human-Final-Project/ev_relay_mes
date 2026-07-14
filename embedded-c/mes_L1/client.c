#include "client.h"

#include <stdio.h>
#include <string.h>

#include "config.h"
#include "net.h"

typedef struct {
    L1Socket socket;
    const L1DeviceConfig *device;
    int send_failed;
} RuntimeContext;

static void report_error(const L1ClientHandlers *handlers,
                         L1ClientFeedResult error,
                         L1ProtocolResult protocol_result,
                         const char *line)
{
    if (handlers != NULL && handlers->on_error != NULL) {
        handlers->on_error(error,
                           protocol_result,
                           line,
                           handlers->context);
    }
}

void l1_client_session_init(L1ClientSession *session,
                            const L1DeviceConfig *device)
{
    if (session == NULL) {
        return;
    }
    memset(session, 0, sizeof(*session));
    session->device = device;
}

static L1ClientFeedResult handle_complete_line(
    L1ClientSession *session,
    const L1ClientHandlers *handlers)
{
    L1Command command;
    L1ProtocolResult protocol_result =
        l1_protocol_parse_command(session->line_buffer, &command);

    if (protocol_result != L1_PROTOCOL_OK) {
        report_error(handlers,
                     L1_CLIENT_FEED_PROTOCOL_ERROR,
                     protocol_result,
                     session->line_buffer);
        return L1_CLIENT_FEED_PROTOCOL_ERROR;
    }
    if (strcmp(command.machine_id, session->device->machine_id) != 0
        || strcmp(command.process_code, session->device->process_code) != 0) {
        report_error(handlers,
                     L1_CLIENT_FEED_MACHINE_MISMATCH,
                     L1_PROTOCOL_OK,
                     session->line_buffer);
        return L1_CLIENT_FEED_MACHINE_MISMATCH;
    }
    if (handlers != NULL && handlers->on_command != NULL) {
        handlers->on_command(&command, handlers->context);
    }
    return L1_CLIENT_FEED_OK;
}

L1ClientFeedResult l1_client_session_feed(
    L1ClientSession *session,
    const void *data,
    size_t length,
    const L1ClientHandlers *handlers)
{
    const unsigned char *bytes = (const unsigned char *)data;
    L1ClientFeedResult overall_result = L1_CLIENT_FEED_OK;
    size_t index;

    if (session == NULL || session->device == NULL
        || (data == NULL && length > 0)) {
        return L1_CLIENT_FEED_INVALID_ARGUMENT;
    }

    for (index = 0; index < length; ++index) {
        unsigned char byte = bytes[index];

        if (session->discarding_oversized_message) {
            if (byte == '\n') {
                session->discarding_oversized_message = 0;
                report_error(handlers,
                             L1_CLIENT_FEED_MESSAGE_TOO_LONG,
                             L1_PROTOCOL_MESSAGE_TOO_LONG,
                             NULL);
                overall_result = L1_CLIENT_FEED_MESSAGE_TOO_LONG;
            }
            continue;
        }

        if (session->line_length >= L1_PROTOCOL_MAX_MESSAGE_SIZE) {
            session->line_length = 0;
            session->discarding_oversized_message = 1;
            if (byte == '\n') {
                session->discarding_oversized_message = 0;
                report_error(handlers,
                             L1_CLIENT_FEED_MESSAGE_TOO_LONG,
                             L1_PROTOCOL_MESSAGE_TOO_LONG,
                             NULL);
                overall_result = L1_CLIENT_FEED_MESSAGE_TOO_LONG;
            }
            continue;
        }

        session->line_buffer[session->line_length++] = (char)byte;
        session->line_buffer[session->line_length] = '\0';

        if (byte == '\n') {
            L1ClientFeedResult line_result =
                handle_complete_line(session, handlers);

            session->line_length = 0;
            session->line_buffer[0] = '\0';
            if (line_result != L1_CLIENT_FEED_OK) {
                overall_result = line_result;
            }
        }
    }
    return overall_result;
}

static int send_protocol_message(L1Socket socket,
                                 const char *message,
                                 size_t length)
{
    printf("[L1 -> L2] %.*s", (int)length, message);
    fflush(stdout);

    if (l1_net_send_all(socket, message, length) != 0) {
        fprintf(stderr,
                "[L1] Send failed: %s\n",
                l1_net_last_error_message());
        return -1;
    }
    return 0;
}

static void handle_runtime_command(const L1Command *command, void *context)
{
    RuntimeContext *runtime = (RuntimeContext *)context;
    L1CommandAckEvent ack;
    char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t output_length = 0;
    L1ProtocolResult result;

    printf("[L2 -> L1] COMMAND id=%lld type=%s machine=%s process=%s lot=%s inputQty=%d\n",
           (long long)command->command_id,
           l1_command_type_name(command->type),
           command->machine_id,
           command->process_code,
           command->lot_no,
           command->input_qty);

    memset(&ack, 0, sizeof(ack));
    strcpy(ack.machine_id, runtime->device->machine_id);
    ack.command_id = command->command_id;
    ack.status = L1_ACK_ACCEPTED;
    strcpy(ack.message, "command_received");

    result = l1_protocol_build_command_ack(output,
                                           sizeof(output),
                                           &ack,
                                           &output_length);
    if (result != L1_PROTOCOL_OK) {
        fprintf(stderr,
                "[L1] Failed to build COMMAND_ACK: %s\n",
                l1_protocol_result_name(result));
        runtime->send_failed = 1;
        return;
    }
    if (send_protocol_message(runtime->socket,
                              output,
                              output_length) != 0) {
        runtime->send_failed = 1;
    }
}

static void handle_runtime_error(L1ClientFeedResult error,
                                 L1ProtocolResult protocol_result,
                                 const char *line,
                                 void *context)
{
    (void)context;
    fprintf(stderr,
            "[L1] Discarded L2 message: %s",
            l1_client_feed_result_name(error));
    if (protocol_result != L1_PROTOCOL_OK) {
        fprintf(stderr,
                " (%s)",
                l1_protocol_result_name(protocol_result));
    }
    if (line != NULL) {
        fprintf(stderr, " line=%s", line);
        if (strchr(line, '\n') == NULL) {
            fputc('\n', stderr);
        }
    } else {
        fputc('\n', stderr);
    }
    fflush(stderr);
}

int l1_client_run(const L1DeviceConfig *device,
                  const char *server_address,
                  uint16_t server_port)
{
    L1Socket socket;
    char hello[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t hello_length = 0;
    L1ProtocolResult protocol_result;
    L1ClientSession session;
    L1ClientHandlers handlers;
    RuntimeContext runtime;
    unsigned char receive_buffer[L1_RECEIVE_BUFFER_SIZE];
    int exit_code = -1;

    if (device == NULL || server_address == NULL || server_port == 0) {
        return -1;
    }

    printf("[L1] Connecting to %s:%u...\n",
           server_address,
           (unsigned int)server_port);
    socket = l1_net_connect(server_address, server_port);
    if (socket == L1_INVALID_SOCKET) {
        fprintf(stderr,
                "[L1] Connection failed: %s\n",
                l1_net_last_error_message());
        return -1;
    }
    printf("[L1] Connected. machine=%s process=%s\n",
           device->machine_id,
           device->process_code);

    protocol_result = l1_protocol_build_hello(hello,
                                              sizeof(hello),
                                              device->machine_id,
                                              &hello_length);
    if (protocol_result != L1_PROTOCOL_OK) {
        fprintf(stderr,
                "[L1] Failed to build HELLO: %s\n",
                l1_protocol_result_name(protocol_result));
        goto cleanup;
    }
    if (send_protocol_message(socket, hello, hello_length) != 0) {
        goto cleanup;
    }

    l1_client_session_init(&session, device);
    memset(&runtime, 0, sizeof(runtime));
    runtime.socket = socket;
    runtime.device = device;
    handlers.on_command = handle_runtime_command;
    handlers.on_error = handle_runtime_error;
    handlers.context = &runtime;

    for (;;) {
        int received = l1_net_receive(socket,
                                      receive_buffer,
                                      sizeof(receive_buffer));

        if (received < 0) {
            fprintf(stderr,
                    "[L1] Receive failed: %s\n",
                    l1_net_last_error_message());
            break;
        }
        if (received == 0) {
            printf("[L1] L2 closed the connection.\n");
            exit_code = 0;
            break;
        }

        l1_client_session_feed(&session,
                               receive_buffer,
                               (size_t)received,
                               &handlers);
        if (runtime.send_failed) {
            break;
        }
    }

    if (session.line_length > 0) {
        fprintf(stderr,
                "[L1] Discarded %u bytes without LF at connection close.\n",
                (unsigned int)session.line_length);
    }

cleanup:
    l1_net_close(socket);
    return exit_code;
}

const char *l1_client_feed_result_name(L1ClientFeedResult result)
{
    switch (result) {
    case L1_CLIENT_FEED_OK:
        return "OK";
    case L1_CLIENT_FEED_INVALID_ARGUMENT:
        return "INVALID_ARGUMENT";
    case L1_CLIENT_FEED_PROTOCOL_ERROR:
        return "PROTOCOL_ERROR";
    case L1_CLIENT_FEED_MACHINE_MISMATCH:
        return "MACHINE_MISMATCH";
    case L1_CLIENT_FEED_MESSAGE_TOO_LONG:
        return "MESSAGE_TOO_LONG";
    default:
        return "UNKNOWN";
    }
}
