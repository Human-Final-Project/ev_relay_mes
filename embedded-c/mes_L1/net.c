#include "net.h"

#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

#ifdef _WIN32
#include <ws2tcpip.h>
#else
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>
#endif

static char error_buffer[128];

int l1_net_runtime_init(void)
{
#ifdef _WIN32
    WSADATA winsock_data;
    int result = WSAStartup(MAKEWORD(2, 2), &winsock_data);

    if (result != 0) {
        snprintf(error_buffer,
                 sizeof(error_buffer),
                 "WSAStartup failed: %d",
                 result);
        return -1;
    }
#endif
    return 0;
}

void l1_net_runtime_cleanup(void)
{
#ifdef _WIN32
    WSACleanup();
#endif
}

L1Socket l1_net_connect(const char *server_address, uint16_t server_port)
{
    L1Socket socket_handle;
    struct sockaddr_in address;

    if (server_address == NULL) {
        return L1_INVALID_SOCKET;
    }
    socket_handle = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (socket_handle == L1_INVALID_SOCKET) {
        return L1_INVALID_SOCKET;
    }

    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = htons(server_port);
#ifdef _WIN32
    address.sin_addr.s_addr = inet_addr(server_address);
    if (address.sin_addr.s_addr == INADDR_NONE
        && strcmp(server_address, "255.255.255.255") != 0) {
        l1_net_close(socket_handle);
        return L1_INVALID_SOCKET;
    }
#else
    if (inet_pton(AF_INET, server_address, &address.sin_addr) != 1) {
        l1_net_close(socket_handle);
        return L1_INVALID_SOCKET;
    }
#endif

    if (connect(socket_handle,
                (const struct sockaddr *)&address,
                (int)sizeof(address)) != 0) {
        l1_net_close(socket_handle);
        return L1_INVALID_SOCKET;
    }
    return socket_handle;
}

int l1_net_send_all(L1Socket socket_handle,
                    const void *buffer,
                    size_t length)
{
    const unsigned char *bytes = (const unsigned char *)buffer;
    size_t total_sent = 0;

    if (buffer == NULL || length > INT_MAX) {
        return -1;
    }
    while (total_sent < length) {
        int sent;

#ifdef _WIN32
        sent = send(socket_handle,
                    (const char *)(bytes + total_sent),
                    (int)(length - total_sent),
                    0);
#else
#ifdef MSG_NOSIGNAL
        sent = (int)send(socket_handle,
                         bytes + total_sent,
                         length - total_sent,
                         MSG_NOSIGNAL);
#else
        sent = (int)send(socket_handle,
                         bytes + total_sent,
                         length - total_sent,
                         0);
#endif
#endif
        if (sent <= 0) {
            return -1;
        }
        total_sent += (size_t)sent;
    }
    return 0;
}

int l1_net_receive(L1Socket socket_handle, void *buffer, size_t capacity)
{
    if (buffer == NULL || capacity == 0 || capacity > INT_MAX) {
        return -1;
    }
#ifdef _WIN32
    return recv(socket_handle, (char *)buffer, (int)capacity, 0);
#else
    return (int)recv(socket_handle, buffer, capacity, 0);
#endif
}

void l1_net_close(L1Socket socket_handle)
{
    if (socket_handle == L1_INVALID_SOCKET) {
        return;
    }
#ifdef _WIN32
    closesocket(socket_handle);
#else
    close(socket_handle);
#endif
}

const char *l1_net_last_error_message(void)
{
#ifdef _WIN32
    snprintf(error_buffer,
             sizeof(error_buffer),
             "Winsock error %d",
             WSAGetLastError());
#else
    snprintf(error_buffer,
             sizeof(error_buffer),
             "%s",
             strerror(errno));
#endif
    return error_buffer;
}
