#include "client.h"

#include <stdio.h>
#include <string.h>

#include "config.h"
#include "machine_runtime.h"
#include "net.h"

typedef struct {
    L1Socket socket;
    const L1DeviceConfig *device;
    L1MachineRuntime *machine;
    uint64_t next_production_at;
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

static L1ProtocolResult build_runtime_action(
    const L1RuntimeAction *action,
    char *output,
    size_t output_capacity,
    size_t *output_length)
{
    switch (action->type) {
    case L1_RUNTIME_ACTION_COMMAND_ACK:
        return l1_protocol_build_command_ack(
            output,
            output_capacity,
            &action->data.command_ack,
            output_length);
    case L1_RUNTIME_ACTION_PRODUCTION:
        return l1_protocol_build_production(
            output,
            output_capacity,
            &action->data.production,
            output_length);
    case L1_RUNTIME_ACTION_INSPECTION:
        return l1_protocol_build_inspection(
            output,
            output_capacity,
            &action->data.inspection,
            output_length);
    case L1_RUNTIME_ACTION_ALARM:
        return l1_protocol_build_alarm(output,
                                       output_capacity,
                                       &action->data.alarm,
                                       output_length);
    case L1_RUNTIME_ACTION_MACHINE_STATUS:
        return l1_protocol_build_machine_status(
            output,
            output_capacity,
            &action->data.machine_status,
            output_length);
    default:
        return L1_PROTOCOL_INVALID_VALUE;
    }
}

static int execute_runtime_actions(RuntimeContext *runtime,
                                   const L1RuntimeActions *actions)
{
    size_t index;

    for (index = 0; index < actions->count; ++index) {
        const L1RuntimeAction *action = &actions->actions[index];
        char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
        size_t output_length = 0;
        L1ProtocolResult result = build_runtime_action(action,
                                                       output,
                                                       sizeof(output),
                                                       &output_length);

        if (result != L1_PROTOCOL_OK) {
            fprintf(stderr,
                    "[L1] Failed to build runtime event: %s\n",
                    l1_protocol_result_name(result));
            return -1;
        }
        if (send_protocol_message(runtime->socket,
                                  output,
                                  output_length) != 0) {
            return -1;
        }
        if (((action->type == L1_RUNTIME_ACTION_PRODUCTION
              && l1_machine_runtime_mark_reported(
                     runtime->machine,
                     action->reported_quantity) != 0)
             || (action->type == L1_RUNTIME_ACTION_INSPECTION
                 && action->completes_unit
                 && l1_machine_runtime_mark_reported(runtime->machine, 1) != 0))) {
            fprintf(stderr, "[L1] Failed to update reported quantity.\n");
            return -1;
        }
    }
    return 0;
}

static void handle_runtime_command(const L1Command *command, void *context)
{
    RuntimeContext *runtime = (RuntimeContext *)context;
    L1RuntimeActions actions;

    printf("[L2 -> L1] COMMAND id=%lld type=%s machine=%s process=%s lot=%s inputQty=%d\n",
           (long long)command->command_id,
           l1_command_type_name(command->type),
           command->machine_id,
           command->process_code,
           command->lot_no,
           command->input_qty);

    if (l1_machine_runtime_handle_command(runtime->machine,
                                          command,
                                          &actions) != 0) {
        fprintf(stderr, "[L1] Failed to handle COMMAND.\n");
        runtime->send_failed = 1;
        return;
    }
    if (execute_runtime_actions(runtime, &actions) != 0) {
        runtime->send_failed = 1;
        return;
    }
    if (runtime->machine->state == L1_RUNTIME_RUNNING) {
        runtime->next_production_at =
            l1_net_monotonic_milliseconds() + L1_PRODUCTION_TICK_MS;
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

static void run_connected_session(L1Socket socket,
                                  const L1DeviceConfig *device,
                                  L1MachineRuntime *machine,
                                  const char *heartbeat,
                                  size_t heartbeat_length)
{
    L1ClientSession session;
    L1ClientHandlers handlers;
    RuntimeContext runtime;
    unsigned char receive_buffer[L1_RECEIVE_BUFFER_SIZE];
    uint64_t next_heartbeat_at =
        l1_net_monotonic_milliseconds() + L1_HEARTBEAT_INTERVAL_MS;

    l1_client_session_init(&session, device);
    memset(&runtime, 0, sizeof(runtime));
    runtime.socket = socket;
    runtime.device = device;
    runtime.machine = machine;
    runtime.next_production_at =
        l1_net_monotonic_milliseconds() + L1_PRODUCTION_TICK_MS;
    handlers.on_command = handle_runtime_command;
    handlers.on_error = handle_runtime_error;
    handlers.context = &runtime;

    for (;;) {
        uint64_t now = l1_net_monotonic_milliseconds();
        uint64_t wait_ms;
        int received;

        if (machine->state == L1_RUNTIME_RUNNING
            && now >= runtime.next_production_at) {
            L1RuntimeActions actions;

            if (l1_machine_runtime_tick(machine, &actions) != 0
                || execute_runtime_actions(&runtime, &actions) != 0) {
                runtime.send_failed = 1;
                break;
            }
            runtime.next_production_at =
                l1_net_monotonic_milliseconds() + L1_PRODUCTION_TICK_MS;
            now = l1_net_monotonic_milliseconds();
        }

        if (now >= next_heartbeat_at) {
            if (send_protocol_message(socket,
                                      heartbeat,
                                      heartbeat_length) != 0) {
                break;
            }
            next_heartbeat_at =
                l1_net_monotonic_milliseconds()
                + L1_HEARTBEAT_INTERVAL_MS;
            now = l1_net_monotonic_milliseconds();
        }

        wait_ms = next_heartbeat_at > now
            ? next_heartbeat_at - now
            : 1U;
        if (wait_ms > L1_HEARTBEAT_INTERVAL_MS) {
            wait_ms = L1_HEARTBEAT_INTERVAL_MS;
        }
        if (machine->state == L1_RUNTIME_RUNNING) {
            uint64_t production_wait = runtime.next_production_at > now
                ? runtime.next_production_at - now
                : 1U;

            if (production_wait < wait_ms) {
                wait_ms = production_wait;
            }
        }
        if (l1_net_set_receive_timeout(socket, (uint32_t)wait_ms) != 0) {
            fprintf(stderr,
                    "[L1] Failed to set receive timeout: %s\n",
                    l1_net_last_error_message());
            break;
        }

        received = l1_net_receive(socket,
                                  receive_buffer,
                                  sizeof(receive_buffer));
        if (received == L1_NET_RECEIVE_TIMEOUT) {
            continue;
        }
        if (received == L1_NET_RECEIVE_ERROR) {
            fprintf(stderr,
                    "[L1] Receive failed: %s\n",
                    l1_net_last_error_message());
            break;
        }
        if (received == 0) {
            printf("[L1] L2 closed the connection.\n");
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
}

int l1_client_run(const L1DeviceConfig *device,
                  const char *server_address,
                  uint16_t server_port,
                  int error_after_qty)
{
    char hello[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    char heartbeat[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t hello_length = 0;
    size_t heartbeat_length = 0;
    L1ProtocolResult protocol_result;
    L1MachineRuntime machine;

    if (device == NULL || server_address == NULL || server_port == 0) {
        return -1;
    }
    l1_machine_runtime_init(&machine, device, error_after_qty);

    protocol_result = l1_protocol_build_hello(hello,
                                              sizeof(hello),
                                              device->machine_id,
                                              &hello_length);
    if (protocol_result != L1_PROTOCOL_OK) {
        fprintf(stderr,
                "[L1] Failed to build HELLO: %s\n",
                l1_protocol_result_name(protocol_result));
        return -1;
    }
    protocol_result = l1_protocol_build_heartbeat(heartbeat,
                                                  sizeof(heartbeat),
                                                  device->machine_id,
                                                  &heartbeat_length);
    if (protocol_result != L1_PROTOCOL_OK) {
        fprintf(stderr,
                "[L1] Failed to build HEARTBEAT: %s\n",
                l1_protocol_result_name(protocol_result));
        return -1;
    }

    for (;;) {
        L1Socket socket;

        printf("[L1] Connecting to %s:%u...\n",
               server_address,
               (unsigned int)server_port);
        socket = l1_net_connect(server_address, server_port);
        if (socket == L1_INVALID_SOCKET) {
            fprintf(stderr,
                    "[L1] Connection failed: %s\n",
                    l1_net_last_error_message());
        } else {
            printf("[L1] Connected. machine=%s process=%s\n",
                   device->machine_id,
                   device->process_code);
            if (send_protocol_message(socket, hello, hello_length) == 0) {
                run_connected_session(socket,
                                      device,
                                      &machine,
                                      heartbeat,
                                      heartbeat_length);
            }
            l1_net_close(socket);
        }

        printf("[L1] Reconnecting in %u seconds.\n",
               (unsigned int)(L1_RECONNECT_DELAY_MS / 1000U));
        fflush(stdout);
        l1_net_sleep_milliseconds(L1_RECONNECT_DELAY_MS);
    }
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
