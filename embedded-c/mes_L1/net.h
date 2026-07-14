#ifndef EV_RELAY_L1_NET_H
#define EV_RELAY_L1_NET_H

#include <stddef.h>
#include <stdint.h>

#ifdef _WIN32
#include <winsock2.h>
typedef SOCKET L1Socket;
#define L1_INVALID_SOCKET INVALID_SOCKET
#else
typedef int L1Socket;
#define L1_INVALID_SOCKET (-1)
#endif

int l1_net_runtime_init(void);
void l1_net_runtime_cleanup(void);

L1Socket l1_net_connect(const char *server_address, uint16_t server_port);
int l1_net_send_all(L1Socket socket, const void *buffer, size_t length);
int l1_net_receive(L1Socket socket, void *buffer, size_t capacity);
void l1_net_close(L1Socket socket);
const char *l1_net_last_error_message(void);

#endif
