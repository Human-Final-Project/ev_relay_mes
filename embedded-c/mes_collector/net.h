#ifndef MES_COLLECTOR_NET_H
#define MES_COLLECTOR_NET_H

#include <stddef.h>
#include <stdint.h>

#ifdef _WIN32
#include <winsock2.h>
#include <ws2tcpip.h>
typedef SOCKET NetSocket;
#define NET_INVALID_SOCKET INVALID_SOCKET
#else
typedef int NetSocket;
#define NET_INVALID_SOCKET (-1)
#endif

#define NET_RECEIVE_ERROR (-1)
#define NET_RECEIVE_TIMEOUT (-2)

/* Prepares and releases platform networking resources. */
int net_runtime_init(void);
void net_runtime_cleanup(void);

/* Creates an IPv4 TCP server socket that is already bound and listening. */
NetSocket net_tcp_server_create(const char *bind_address,
                                uint16_t port,
                                int backlog);

/* Accepts one client and optionally returns its printable address and port. */
NetSocket net_accept_client(NetSocket server_socket,
                            char *peer_address,
                            size_t address_capacity,
                            uint16_t *peer_port);

/* Receives available bytes. Returns bytes, 0 for close, or NET_RECEIVE_*. */
int net_receive(NetSocket socket, void *buffer, size_t capacity);

/* Sends the entire buffer unless a socket error occurs. */
int net_send_all(NetSocket socket, const void *buffer, size_t length);

/* Sets the receive timeout. Zero restores blocking mode. */
int net_set_receive_timeout(NetSocket socket, uint32_t timeout_ms);

void net_socket_close(NetSocket socket);
const char *net_last_error_message(void);
uint64_t net_monotonic_milliseconds(void);

#endif
