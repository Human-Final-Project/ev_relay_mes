#include "collector.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "api_client.h"
#include "connection_registry.h"
#include "thread_compat.h"

typedef struct {
    CollectorConnectionRegistry *registry;
    CollectorConnection *connection;
    int close_requested;
} CollectorWorkerContext;

static CollectorConnectionRegistry connection_registry;
static int connection_registry_ready;

static const char *message_machine_id(const ProtocolMessage *message)
{
    switch (message->type) {
    case PROTOCOL_EVENT_HELLO:
    case PROTOCOL_EVENT_HEARTBEAT:
        return message->data.connection.machine_id;
    case PROTOCOL_EVENT_PRODUCTION:
        return message->data.production.machine_id;
    case PROTOCOL_EVENT_INSPECTION:
        return message->data.inspection.machine_id;
    case PROTOCOL_EVENT_DEFECT:
        return message->data.defect.machine_id;
    case PROTOCOL_EVENT_ALARM:
        return message->data.alarm.machine_id;
    case PROTOCOL_EVENT_MACHINE_STATUS:
        return message->data.machine_status.machine_id;
    case PROTOCOL_EVENT_COMMAND_ACK:
        return message->data.command_ack.machine_id;
    case PROTOCOL_EVENT_COMMAND:
    case PROTOCOL_EVENT_UNKNOWN:
    default:
        return NULL;
    }
}

static void notify_error(const CollectorSessionHandlers *handlers,
                         CollectorSessionError error,
                         ProtocolResult protocol_result,
                         const char *line)
{
    if (handlers != NULL && handlers->on_error != NULL) {
        handlers->on_error(error,
                           protocol_result,
                           line,
                           handlers->context);
    }
}

static void notify_message(const CollectorSessionHandlers *handlers,
                           const ProtocolMessage *message)
{
    if (handlers != NULL && handlers->on_message != NULL) {
        handlers->on_message(message, handlers->context);
    }
}

static CollectorFeedResult process_complete_line(
    CollectorSession *session,
    const CollectorSessionHandlers *handlers)
{
    ProtocolMessage message;
    ProtocolResult result;
    const char *machine_id;

    result = protocol_parse_message(session->line_buffer, &message);
    if (result != PROTOCOL_RESULT_OK) {
        notify_error(handlers,
                     COLLECTOR_SESSION_ERROR_PROTOCOL,
                     result,
                     session->line_buffer);
        return session->hello_received
            ? COLLECTOR_FEED_OK
            : COLLECTOR_FEED_CLOSE_CONNECTION;
    }

    if (!session->hello_received) {
        if (message.type != PROTOCOL_EVENT_HELLO) {
            notify_error(handlers,
                         COLLECTOR_SESSION_ERROR_HELLO_REQUIRED,
                         PROTOCOL_RESULT_UNEXPECTED_EVENT,
                         session->line_buffer);
            return COLLECTOR_FEED_CLOSE_CONNECTION;
        }
        memcpy(session->machine_id,
               message.data.connection.machine_id,
               sizeof(session->machine_id));
        session->hello_received = 1;
        notify_message(handlers, &message);
        return COLLECTOR_FEED_OK;
    }

    if (message.type == PROTOCOL_EVENT_HELLO) {
        notify_error(handlers,
                     COLLECTOR_SESSION_ERROR_DUPLICATE_HELLO,
                     PROTOCOL_RESULT_UNEXPECTED_EVENT,
                     session->line_buffer);
        return COLLECTOR_FEED_OK;
    }

    machine_id = message_machine_id(&message);
    if (machine_id == NULL || strcmp(machine_id, session->machine_id) != 0) {
        notify_error(handlers,
                     COLLECTOR_SESSION_ERROR_MACHINE_ID_MISMATCH,
                     PROTOCOL_RESULT_INVALID_VALUE,
                     session->line_buffer);
        return COLLECTOR_FEED_OK;
    }

    notify_message(handlers, &message);
    return COLLECTOR_FEED_OK;
}

void collector_session_init(CollectorSession *session)
{
    if (session != NULL) {
        memset(session, 0, sizeof(*session));
    }
}

CollectorFeedResult collector_session_feed(
    CollectorSession *session,
    const void *data,
    size_t length,
    const CollectorSessionHandlers *handlers)
{
    const unsigned char *bytes = (const unsigned char *)data;
    size_t index;

    if (session == NULL || (data == NULL && length > 0)) {
        return COLLECTOR_FEED_CLOSE_CONNECTION;
    }

    for (index = 0; index < length; ++index) {
        unsigned char byte = bytes[index];

        if (session->discarding_oversized_message) {
            if (byte == '\n') {
                session->discarding_oversized_message = 0;
            }
            continue;
        }

        if (session->line_length >= COLLECTOR_MAX_MESSAGE_SIZE) {
            session->line_length = 0;
            session->line_buffer[0] = '\0';
            session->discarding_oversized_message = byte != '\n';
            notify_error(handlers,
                         COLLECTOR_SESSION_ERROR_MESSAGE_TOO_LONG,
                         PROTOCOL_RESULT_MESSAGE_TOO_LONG,
                         NULL);
            if (!session->hello_received) {
                return COLLECTOR_FEED_CLOSE_CONNECTION;
            }
            continue;
        }

        session->line_buffer[session->line_length++] = (char)byte;
        if (byte == '\n') {
            CollectorFeedResult result;

            session->line_buffer[session->line_length] = '\0';
            result = process_complete_line(session, handlers);
            session->line_length = 0;
            session->line_buffer[0] = '\0';
            if (result == COLLECTOR_FEED_CLOSE_CONNECTION) {
                return result;
            }
        }
    }
    return COLLECTOR_FEED_OK;
}

CollectorSendResult collector_send_command(const CollectorSession *session,
                                           NetSocket client_socket,
                                           const ProtocolCommand *command,
                                           ProtocolResult *protocol_result)
{
    char message[COLLECTOR_MAX_MESSAGE_SIZE + 1];
    size_t message_length = 0;
    ProtocolResult result;

    if (protocol_result != NULL) {
        *protocol_result = PROTOCOL_RESULT_OK;
    }
    if (session == NULL || command == NULL || !session->hello_received) {
        return COLLECTOR_SEND_NOT_REGISTERED;
    }
    result = protocol_build_command(message,
                                    sizeof(message),
                                    command,
                                    &message_length);
    if (result != PROTOCOL_RESULT_OK) {
        if (protocol_result != NULL) {
            *protocol_result = result;
        }
        return COLLECTOR_SEND_INVALID_COMMAND;
    }
    if (strcmp(command->machine_id, session->machine_id) != 0) {
        return COLLECTOR_SEND_MACHINE_ID_MISMATCH;
    }
    if (net_send_all(client_socket, message, message_length) != 0) {
        return COLLECTOR_SEND_NETWORK_ERROR;
    }
    return COLLECTOR_SEND_OK;
}

CollectorSendResult collector_send_command_to_machine(
    const ProtocolCommand *command,
    ProtocolResult *protocol_result)
{
    char message[COLLECTOR_MAX_MESSAGE_SIZE + 1];
    size_t message_length = 0;
    ProtocolResult result;
    ConnectionSendResult send_result;

    if (protocol_result != NULL) {
        *protocol_result = PROTOCOL_RESULT_OK;
    }
    if (command == NULL || !connection_registry_ready) {
        return COLLECTOR_SEND_NOT_REGISTERED;
    }
    result = protocol_build_command(message,
                                    sizeof(message),
                                    command,
                                    &message_length);
    if (result != PROTOCOL_RESULT_OK) {
        if (protocol_result != NULL) {
            *protocol_result = result;
        }
        return COLLECTOR_SEND_INVALID_COMMAND;
    }
    send_result = connection_registry_send_to_machine(
        &connection_registry,
        command->machine_id,
        message,
        message_length);
    if (send_result == CONNECTION_SEND_MACHINE_NOT_CONNECTED) {
        return COLLECTOR_SEND_NOT_REGISTERED;
    }
    if (send_result != CONNECTION_SEND_OK) {
        return COLLECTOR_SEND_NETWORK_ERROR;
    }
    return COLLECTOR_SEND_OK;
}

static const char *session_error_name(CollectorSessionError error)
{
    switch (error) {
    case COLLECTOR_SESSION_ERROR_PROTOCOL:
        return "PROTOCOL";
    case COLLECTOR_SESSION_ERROR_HELLO_REQUIRED:
        return "HELLO_REQUIRED";
    case COLLECTOR_SESSION_ERROR_DUPLICATE_HELLO:
        return "DUPLICATE_HELLO";
    case COLLECTOR_SESSION_ERROR_MACHINE_ID_MISMATCH:
        return "MACHINE_ID_MISMATCH";
    case COLLECTOR_SESSION_ERROR_MESSAGE_TOO_LONG:
        return "MESSAGE_TOO_LONG";
    default:
        return "UNKNOWN";
    }
}

static void log_received_message(const ProtocolMessage *message, void *context)
{
    const char *machine_id = message_machine_id(message);
    CollectorWorkerContext *worker = (CollectorWorkerContext *)context;

    if (message->type == PROTOCOL_EVENT_HELLO) {
        ConnectionRegisterResult register_result =
            connection_registry_register_machine(worker->registry,
                                                 worker->connection,
                                                 machine_id);

        if (register_result == CONNECTION_REGISTER_DUPLICATE_MACHINE) {
            fprintf(stderr,
                    "[L2] Duplicate machine connection rejected: machine=%s peer=%s:%u\n",
                    machine_id,
                    collector_connection_peer_address(worker->connection),
                    (unsigned int)collector_connection_peer_port(
                        worker->connection));
            fflush(stderr);
            worker->close_requested = 1;
            return;
        }
        if (register_result != CONNECTION_REGISTER_OK) {
            fprintf(stderr,
                    "[L2] Machine registration failed: machine=%s\n",
                    machine_id != NULL ? machine_id : "-");
            fflush(stderr);
            worker->close_requested = 1;
            return;
        }
        printf("[L2] L1 registered: machine=%s\n",
               machine_id != NULL ? machine_id : "-");
        printf("[L2] Registered connections: %u/%d\n",
               (unsigned int)connection_registry_registered_count(
                   worker->registry),
               COLLECTOR_MAX_L1_CONNECTIONS);
    } else if (!worker->close_requested) {
        ApiClientResult api_result;
        int http_status = 0;

        printf("[L1 -> L2] event=%s machine=%s\n",
               protocol_event_type_name(message->type),
               machine_id != NULL ? machine_id : "-");
        api_result = api_client_send_event(message, &http_status);
        if (api_result == API_CLIENT_OK) {
            printf("[L2 -> Backend] event=%s status=%d\n",
                   protocol_event_type_name(message->type),
                   http_status);
        } else if (api_result != API_CLIENT_SKIPPED) {
            fprintf(stderr,
                    "[L2 -> Backend] event=%s failed=%s status=%d retry=0\n",
                    protocol_event_type_name(message->type),
                    api_client_result_name(api_result),
                    http_status);
            fflush(stderr);
        }
    }
    fflush(stdout);
}

static void log_session_error(CollectorSessionError error,
                              ProtocolResult protocol_result,
                              const char *line,
                              void *context)
{
    (void)context;
    fprintf(stderr,
            "[L2] message rejected: error=%s detail=%s",
            session_error_name(error),
            protocol_result_name(protocol_result));
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

static void handle_client_worker(void *context)
{
    unsigned char receive_buffer[2048];
    CollectorSession session;
    CollectorSessionHandlers handlers;
    CollectorWorkerContext *worker = (CollectorWorkerContext *)context;
    CollectorConnection *connection = worker->connection;
    NetSocket client_socket = collector_connection_socket(connection);
    uint64_t connected_at;
    int blocking_mode_restored = 0;

    collector_session_init(&session);
    handlers.on_message = log_received_message;
    handlers.on_error = log_session_error;
    handlers.context = worker;
    connected_at = net_monotonic_milliseconds();

    printf("[L2] L1 connected: %s:%u\n",
           collector_connection_peer_address(connection),
           (unsigned int)collector_connection_peer_port(connection));
    fflush(stdout);

    for (;;) {
        int received;

        if (!session.hello_received) {
            uint64_t now = net_monotonic_milliseconds();
            uint64_t elapsed = now >= connected_at ? now - connected_at : 0;
            uint64_t timeout_ms =
                (uint64_t)COLLECTOR_HELLO_TIMEOUT_SECONDS * 1000U;

            if (elapsed >= timeout_ms) {
                fprintf(stderr,
                        "[L2] HELLO timeout after %d seconds.\n",
                        COLLECTOR_HELLO_TIMEOUT_SECONDS);
                fflush(stderr);
                break;
            }
            if (net_set_receive_timeout(
                    client_socket,
                    (uint32_t)(timeout_ms - elapsed)) != 0) {
                fprintf(stderr,
                        "[L2] Failed to set HELLO timeout: %s\n",
                        net_last_error_message());
                fflush(stderr);
                break;
            }
        } else if (!blocking_mode_restored) {
            if (net_set_receive_timeout(client_socket, 0) != 0) {
                fprintf(stderr,
                        "[L2] Failed to restore blocking mode: %s\n",
                        net_last_error_message());
                fflush(stderr);
                break;
            }
            blocking_mode_restored = 1;
        }

        received = net_receive(client_socket,
                               receive_buffer,
                               sizeof(receive_buffer));
        if (received == NET_RECEIVE_TIMEOUT) {
            fprintf(stderr,
                    "[L2] HELLO timeout after %d seconds.\n",
                    COLLECTOR_HELLO_TIMEOUT_SECONDS);
            fflush(stderr);
            break;
        }
        if (received == NET_RECEIVE_ERROR) {
            fprintf(stderr,
                    "[L2] Receive failed: %s\n",
                    net_last_error_message());
            fflush(stderr);
            break;
        }
        if (received == 0) {
            if (session.hello_received) {
                printf("[L2] L1 disconnected: machine=%s\n",
                       session.machine_id);
            } else {
                printf("[L2] Unregistered L1 disconnected.\n");
            }
            fflush(stdout);
            break;
        }
        if (collector_session_feed(&session,
                                   receive_buffer,
                                   (size_t)received,
                                   &handlers)
            == COLLECTOR_FEED_CLOSE_CONNECTION) {
            fprintf(stderr,
                    "[L2] Closing connection because the first message was not a valid HELLO.\n");
            fflush(stderr);
            break;
        }
        if (worker->close_requested) {
            break;
        }
    }

    if (session.line_length > 0) {
        fprintf(stderr,
                "[L2] Discarded %u bytes without LF at connection close.\n",
                (unsigned int)session.line_length);
        fflush(stderr);
    }
    client_socket = connection_registry_detach(worker->registry,
                                               connection);
    net_socket_close(client_socket);
    printf("[L2] Active connections: %u/%d\n",
           (unsigned int)connection_registry_active_count(worker->registry),
           COLLECTOR_MAX_L1_CONNECTIONS);
    fflush(stdout);
    free(worker);
}

int collector_run(void)
{
    NetSocket server_socket;

    if (connection_registry_init(&connection_registry) != 0) {
        fprintf(stderr, "[L2] Failed to initialize connection registry.\n");
        return -1;
    }
    connection_registry_ready = 1;

    server_socket = net_tcp_server_create(COLLECTOR_BIND_ADDRESS,
                                          COLLECTOR_TCP_PORT,
                                          COLLECTOR_MAX_L1_CONNECTIONS);
    if (server_socket == NET_INVALID_SOCKET) {
        fprintf(stderr,
                "[L2] Failed to listen on %s:%d: %s\n",
                COLLECTOR_BIND_ADDRESS,
                COLLECTOR_TCP_PORT,
                net_last_error_message());
        connection_registry_ready = 0;
        connection_registry_destroy(&connection_registry);
        return -1;
    }

    printf("EV Relay MES L2 collector\n");
    printf("[L2] Listening on %s:%d (max %d concurrent L1 connections)\n",
           COLLECTOR_BIND_ADDRESS,
           COLLECTOR_TCP_PORT,
           COLLECTOR_MAX_L1_CONNECTIONS);
    fflush(stdout);

    for (;;) {
        char peer_address[64];
        uint16_t peer_port = 0;
        NetSocket client_socket = net_accept_client(server_socket,
                                                    peer_address,
                                                    sizeof(peer_address),
                                                    &peer_port);

        if (client_socket == NET_INVALID_SOCKET) {
            fprintf(stderr,
                    "[L2] Accept failed: %s\n",
                    net_last_error_message());
            net_socket_close(server_socket);
            return -1;
        }
        {
            CollectorConnection *connection = connection_registry_acquire(
                &connection_registry,
                client_socket,
                peer_address,
                peer_port);
            CollectorWorkerContext *worker;

            if (connection == NULL) {
                fprintf(stderr,
                        "[L2] Connection rejected: maximum %d L1 connections reached.\n",
                        COLLECTOR_MAX_L1_CONNECTIONS);
                fflush(stderr);
                net_socket_close(client_socket);
                continue;
            }
            worker = (CollectorWorkerContext *)malloc(sizeof(*worker));
            if (worker == NULL) {
                fprintf(stderr, "[L2] Failed to allocate worker context.\n");
                client_socket = connection_registry_detach(
                    &connection_registry,
                    connection);
                net_socket_close(client_socket);
                continue;
            }
            worker->registry = &connection_registry;
            worker->connection = connection;
            worker->close_requested = 0;
            if (collector_thread_start_detached(handle_client_worker,
                                                worker) != 0) {
                fprintf(stderr, "[L2] Failed to start L1 worker thread.\n");
                free(worker);
                client_socket = connection_registry_detach(
                    &connection_registry,
                    connection);
                net_socket_close(client_socket);
            }
        }
    }
}
