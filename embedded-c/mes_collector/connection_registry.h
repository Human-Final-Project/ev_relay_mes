#ifndef MES_COLLECTOR_CONNECTION_REGISTRY_H
#define MES_COLLECTOR_CONNECTION_REGISTRY_H

#include <stddef.h>
#include <stdint.h>

#include "config.h"
#include "net.h"
#include "protocol.h"
#include "thread_compat.h"

#define COLLECTOR_PEER_ADDRESS_CAPACITY 64

typedef enum {
    CONNECTION_REGISTER_OK = 0,
    CONNECTION_REGISTER_INVALID,
    CONNECTION_REGISTER_ALREADY_REGISTERED,
    CONNECTION_REGISTER_DUPLICATE_MACHINE
} ConnectionRegisterResult;

typedef enum {
    CONNECTION_SEND_OK = 0,
    CONNECTION_SEND_INVALID_ARGUMENT,
    CONNECTION_SEND_MACHINE_NOT_CONNECTED,
    CONNECTION_SEND_NETWORK_ERROR
} ConnectionSendResult;

typedef struct {
    int active;
    int registered;
    NetSocket socket;
    char machine_id[PROTOCOL_MACHINE_ID_CAPACITY];
    char peer_address[COLLECTOR_PEER_ADDRESS_CAPACITY];
    uint16_t peer_port;
    CollectorMutex send_mutex;
} CollectorConnection;

typedef struct {
    CollectorConnection connections[COLLECTOR_MAX_L1_CONNECTIONS];
    CollectorMutex mutex;
    int initialized;
} CollectorConnectionRegistry;

int connection_registry_init(CollectorConnectionRegistry *registry);
void connection_registry_destroy(CollectorConnectionRegistry *registry);

CollectorConnection *connection_registry_acquire(
    CollectorConnectionRegistry *registry,
    NetSocket socket,
    const char *peer_address,
    uint16_t peer_port);

ConnectionRegisterResult connection_registry_register_machine(
    CollectorConnectionRegistry *registry,
    CollectorConnection *connection,
    const char *machine_id);

/* Removes a slot and returns its socket. The caller closes the returned socket. */
NetSocket connection_registry_detach(
    CollectorConnectionRegistry *registry,
    CollectorConnection *connection);

ConnectionSendResult connection_registry_send_to_machine(
    CollectorConnectionRegistry *registry,
    const char *machine_id,
    const void *buffer,
    size_t length);

size_t connection_registry_active_count(
    CollectorConnectionRegistry *registry);
size_t connection_registry_registered_count(
    CollectorConnectionRegistry *registry);

int connection_registry_is_machine_registered(
    CollectorConnectionRegistry *registry,
    const char *machine_id);

NetSocket collector_connection_socket(const CollectorConnection *connection);
const char *collector_connection_peer_address(
    const CollectorConnection *connection);
uint16_t collector_connection_peer_port(const CollectorConnection *connection);

#endif
