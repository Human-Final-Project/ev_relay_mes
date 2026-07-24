#include "connection_registry.h"

#include <stdio.h>
#include <string.h>

static int connection_belongs_to_registry(
    const CollectorConnectionRegistry *registry,
    const CollectorConnection *connection)
{
    size_t index;

    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        if (&registry->connections[index] == connection) {
            return 1;
        }
    }
    return 0;
}

int connection_registry_init(CollectorConnectionRegistry *registry)
{
    size_t index;

    if (registry == NULL) {
        return -1;
    }
    memset(registry, 0, sizeof(*registry));
    if (collector_mutex_init(&registry->mutex) != 0) {
        return -1;
    }
    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        registry->connections[index].socket = NET_INVALID_SOCKET;
        if (collector_mutex_init(
                &registry->connections[index].send_mutex) != 0) {
            while (index > 0) {
                --index;
                collector_mutex_destroy(
                    &registry->connections[index].send_mutex);
            }
            collector_mutex_destroy(&registry->mutex);
            return -1;
        }
    }
    registry->initialized = 1;
    return 0;
}

void connection_registry_destroy(CollectorConnectionRegistry *registry)
{
    size_t index;

    if (registry == NULL || !registry->initialized) {
        return;
    }
    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        collector_mutex_destroy(&registry->connections[index].send_mutex);
    }
    collector_mutex_destroy(&registry->mutex);
    registry->initialized = 0;
}

CollectorConnection *connection_registry_acquire(
    CollectorConnectionRegistry *registry,
    NetSocket socket,
    const char *peer_address,
    uint16_t peer_port)
{
    CollectorConnection *result = NULL;
    size_t index;

    if (registry == NULL || !registry->initialized
        || socket == NET_INVALID_SOCKET) {
        return NULL;
    }
    collector_mutex_lock(&registry->mutex);
    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        CollectorConnection *connection = &registry->connections[index];

        if (connection->active) {
            continue;
        }
        collector_mutex_lock(&connection->send_mutex);
        connection->active = 1;
        connection->registered = 0;
        connection->socket = socket;
        connection->machine_id[0] = '\0';
        snprintf(connection->peer_address,
                 sizeof(connection->peer_address),
                 "%s",
                 peer_address != NULL && peer_address[0] != '\0'
                     ? peer_address
                     : "unknown");
        connection->peer_port = peer_port;
        collector_mutex_unlock(&connection->send_mutex);
        result = connection;
        break;
    }
    collector_mutex_unlock(&registry->mutex);
    return result;
}

ConnectionRegisterResult connection_registry_register_machine(
    CollectorConnectionRegistry *registry,
    CollectorConnection *connection,
    const char *machine_id)
{
    ConnectionRegisterResult result = CONNECTION_REGISTER_OK;
    size_t index;

    if (registry == NULL || !registry->initialized || connection == NULL
        || machine_id == NULL || machine_id[0] == '\0'
        || strlen(machine_id) >= PROTOCOL_MACHINE_ID_CAPACITY
        || !connection_belongs_to_registry(registry, connection)) {
        return CONNECTION_REGISTER_INVALID;
    }

    collector_mutex_lock(&registry->mutex);
    if (!connection->active) {
        result = CONNECTION_REGISTER_INVALID;
    } else if (connection->registered) {
        result = CONNECTION_REGISTER_ALREADY_REGISTERED;
    } else {
        for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
            CollectorConnection *other = &registry->connections[index];

            if (other != connection && other->active && other->registered
                && strcmp(other->machine_id, machine_id) == 0) {
                result = CONNECTION_REGISTER_DUPLICATE_MACHINE;
                break;
            }
        }
        if (result == CONNECTION_REGISTER_OK) {
            snprintf(connection->machine_id,
                     sizeof(connection->machine_id),
                     "%s",
                     machine_id);
            connection->registered = 1;
        }
    }
    collector_mutex_unlock(&registry->mutex);
    return result;
}

NetSocket connection_registry_detach(
    CollectorConnectionRegistry *registry,
    CollectorConnection *connection)
{
    NetSocket socket = NET_INVALID_SOCKET;

    if (registry == NULL || !registry->initialized || connection == NULL
        || !connection_belongs_to_registry(registry, connection)) {
        return NET_INVALID_SOCKET;
    }
    collector_mutex_lock(&registry->mutex);
    collector_mutex_lock(&connection->send_mutex);
    if (connection->active) {
        socket = connection->socket;
        connection->active = 0;
        connection->registered = 0;
        connection->socket = NET_INVALID_SOCKET;
        connection->machine_id[0] = '\0';
        connection->peer_address[0] = '\0';
        connection->peer_port = 0;
    }
    collector_mutex_unlock(&connection->send_mutex);
    collector_mutex_unlock(&registry->mutex);
    return socket;
}

ConnectionSendResult connection_registry_send_to_machine(
    CollectorConnectionRegistry *registry,
    const char *machine_id,
    const void *buffer,
    size_t length)
{
    CollectorConnection *target = NULL;
    size_t index;
    int send_result;

    if (registry == NULL || !registry->initialized || machine_id == NULL
        || machine_id[0] == '\0' || buffer == NULL || length == 0) {
        return CONNECTION_SEND_INVALID_ARGUMENT;
    }

    collector_mutex_lock(&registry->mutex);
    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        CollectorConnection *connection = &registry->connections[index];

        if (connection->active && connection->registered
            && strcmp(connection->machine_id, machine_id) == 0) {
            target = connection;
            collector_mutex_lock(&target->send_mutex);
            break;
        }
    }
    if (target == NULL) {
        collector_mutex_unlock(&registry->mutex);
        return CONNECTION_SEND_MACHINE_NOT_CONNECTED;
    }
    collector_mutex_unlock(&registry->mutex);

    send_result = net_send_all(target->socket, buffer, length);
    collector_mutex_unlock(&target->send_mutex);
    return send_result == 0
        ? CONNECTION_SEND_OK
        : CONNECTION_SEND_NETWORK_ERROR;
}

size_t connection_registry_active_count(
    CollectorConnectionRegistry *registry)
{
    size_t count = 0;
    size_t index;

    if (registry == NULL || !registry->initialized) {
        return 0;
    }
    collector_mutex_lock(&registry->mutex);
    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        if (registry->connections[index].active) {
            ++count;
        }
    }
    collector_mutex_unlock(&registry->mutex);
    return count;
}

size_t connection_registry_registered_count(
    CollectorConnectionRegistry *registry)
{
    size_t count = 0;
    size_t index;

    if (registry == NULL || !registry->initialized) {
        return 0;
    }
    collector_mutex_lock(&registry->mutex);
    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        if (registry->connections[index].active
            && registry->connections[index].registered) {
            ++count;
        }
    }
    collector_mutex_unlock(&registry->mutex);
    return count;
}

int connection_registry_is_machine_registered(
    CollectorConnectionRegistry *registry,
    const char *machine_id)
{
    size_t index;
    int found = 0;

    if (registry == NULL || !registry->initialized
        || machine_id == NULL || machine_id[0] == '\0') {
        return 0;
    }
    collector_mutex_lock(&registry->mutex);
    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        CollectorConnection *connection = &registry->connections[index];
        if (connection->active && connection->registered
            && strcmp(connection->machine_id, machine_id) == 0) {
            found = 1;
            break;
        }
    }
    collector_mutex_unlock(&registry->mutex);
    return found;
}

NetSocket collector_connection_socket(const CollectorConnection *connection)
{
    return connection != NULL ? connection->socket : NET_INVALID_SOCKET;
}

const char *collector_connection_peer_address(
    const CollectorConnection *connection)
{
    return connection != NULL ? connection->peer_address : "unknown";
}

uint16_t collector_connection_peer_port(const CollectorConnection *connection)
{
    return connection != NULL ? connection->peer_port : 0;
}
