#ifdef _WIN32
#include <windows.h>
#else
#define _POSIX_C_SOURCE 200809L
#endif

#include "net.h"

#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

#ifndef _WIN32
#include <arpa/inet.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
#endif

static char net_error_buffer[128];

static int net_last_error_code(void)
{
#ifdef _WIN32
    return WSAGetLastError();
#else
    return errno;
#endif
}

static void net_set_last_error_code(int error_code)
{
#ifdef _WIN32
    WSASetLastError(error_code);
#else
    errno = error_code;
#endif
}

static int net_error_is_timeout(int error_code)
{
#ifdef _WIN32
    return error_code == WSAETIMEDOUT || error_code == WSAEWOULDBLOCK;
#else
    return error_code == EAGAIN || error_code == EWOULDBLOCK;
#endif
}

static int net_error_is_connect_pending(int error_code)
{
#ifdef _WIN32
    return error_code == WSAEINPROGRESS
        || error_code == WSAEWOULDBLOCK
        || error_code == WSAEINVAL;
#else
    return error_code == EINPROGRESS;
#endif
}

static int net_set_blocking(NetSocket socket_handle, int blocking)
{
#ifdef _WIN32
    u_long mode = blocking ? 0UL : 1UL;

    return ioctlsocket(socket_handle, FIONBIO, &mode) == 0 ? 0 : -1;
#else
    int flags = fcntl(socket_handle, F_GETFL, 0);

    if (flags < 0) {
        return -1;
    }
    if (blocking) {
        flags &= ~O_NONBLOCK;
    } else {
        flags |= O_NONBLOCK;
    }
    return fcntl(socket_handle, F_SETFL, flags) == 0 ? 0 : -1;
#endif
}

int net_runtime_init(void)
{
#ifdef _WIN32
    WSADATA winsock_data;
    int result = WSAStartup(MAKEWORD(2, 2), &winsock_data);

    if (result != 0) {
        snprintf(net_error_buffer,
                 sizeof(net_error_buffer),
                 "WSAStartup failed: %d",
                 result);
        return -1;
    }
#endif
    return 0;
}

void net_runtime_cleanup(void)
{
#ifdef _WIN32
    WSACleanup();
#endif
}

NetSocket net_tcp_server_create(const char *bind_address,
                                uint16_t port,
                                int backlog)
{
    NetSocket server_socket;
    struct sockaddr_in address;
    int reuse_address = 1;
#ifdef _WIN32
    unsigned long numeric_address;
#endif

    if (bind_address == NULL || backlog < 1) {
        return NET_INVALID_SOCKET;
    }
    server_socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (server_socket == NET_INVALID_SOCKET) {
        return NET_INVALID_SOCKET;
    }

#ifdef _WIN32
    if (setsockopt(server_socket,
                   SOL_SOCKET,
                   SO_REUSEADDR,
                   (const char *)&reuse_address,
                   (int)sizeof(reuse_address)) != 0) {
#else
    if (setsockopt(server_socket,
                   SOL_SOCKET,
                   SO_REUSEADDR,
                   &reuse_address,
                   sizeof(reuse_address)) != 0) {
#endif
        net_socket_close(server_socket);
        return NET_INVALID_SOCKET;
    }

    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = htons(port);
#ifdef _WIN32
    numeric_address = inet_addr(bind_address);
    if (numeric_address == INADDR_NONE
        && strcmp(bind_address, "255.255.255.255") != 0) {
        net_socket_close(server_socket);
        return NET_INVALID_SOCKET;
    }
    address.sin_addr.s_addr = numeric_address;
#else
    if (inet_pton(AF_INET, bind_address, &address.sin_addr) != 1) {
        net_socket_close(server_socket);
        return NET_INVALID_SOCKET;
    }
#endif
    if (bind(server_socket,
             (const struct sockaddr *)&address,
             (int)sizeof(address)) != 0) {
        net_socket_close(server_socket);
        return NET_INVALID_SOCKET;
    }
    if (listen(server_socket, backlog) != 0) {
        net_socket_close(server_socket);
        return NET_INVALID_SOCKET;
    }
    return server_socket;
}

NetSocket net_tcp_client_connect(const char *server_address,
                                 uint16_t port,
                                 uint32_t timeout_ms)
{
    NetSocket client_socket;
    struct sockaddr_in address;
    int connect_result;
    int error_code;

    if (server_address == NULL || port == 0 || timeout_ms == 0) {
        return NET_INVALID_SOCKET;
    }
    client_socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (client_socket == NET_INVALID_SOCKET) {
        return NET_INVALID_SOCKET;
    }

    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = htons(port);
#ifdef _WIN32
    address.sin_addr.s_addr = inet_addr(server_address);
    if (address.sin_addr.s_addr == INADDR_NONE
        && strcmp(server_address, "255.255.255.255") != 0) {
        net_socket_close(client_socket);
        WSASetLastError(WSAEINVAL);
        return NET_INVALID_SOCKET;
    }
#else
    if (inet_pton(AF_INET, server_address, &address.sin_addr) != 1) {
        net_socket_close(client_socket);
        errno = EINVAL;
        return NET_INVALID_SOCKET;
    }
#endif

    if (net_set_blocking(client_socket, 0) != 0) {
        error_code = net_last_error_code();
        net_socket_close(client_socket);
        net_set_last_error_code(error_code);
        return NET_INVALID_SOCKET;
    }
    connect_result = connect(client_socket,
                             (const struct sockaddr *)&address,
                             (int)sizeof(address));
    if (connect_result != 0) {
        fd_set write_set;
        struct timeval timeout;
        int selected;
        int socket_error = 0;
#ifdef _WIN32
        int socket_error_length = (int)sizeof(socket_error);
#else
        socklen_t socket_error_length = (socklen_t)sizeof(socket_error);
#endif

        error_code = net_last_error_code();
        if (!net_error_is_connect_pending(error_code)) {
            net_socket_close(client_socket);
            net_set_last_error_code(error_code);
            return NET_INVALID_SOCKET;
        }

        FD_ZERO(&write_set);
        FD_SET(client_socket, &write_set);
        timeout.tv_sec = (long)(timeout_ms / 1000U);
        timeout.tv_usec = (long)((timeout_ms % 1000U) * 1000U);
        selected = select((int)(client_socket + 1),
                          NULL,
                          &write_set,
                          NULL,
                          &timeout);
        if (selected <= 0) {
#ifdef _WIN32
            error_code = selected == 0 ? WSAETIMEDOUT : net_last_error_code();
#else
            error_code = selected == 0 ? ETIMEDOUT : net_last_error_code();
#endif
            net_socket_close(client_socket);
            net_set_last_error_code(error_code);
            return NET_INVALID_SOCKET;
        }
        if (getsockopt(client_socket,
                       SOL_SOCKET,
                       SO_ERROR,
#ifdef _WIN32
                       (char *)&socket_error,
#else
                       &socket_error,
#endif
                       &socket_error_length) != 0
            || socket_error != 0) {
            error_code = socket_error != 0
                ? socket_error
                : net_last_error_code();
            net_socket_close(client_socket);
            net_set_last_error_code(error_code);
            return NET_INVALID_SOCKET;
        }
    }

    if (net_set_blocking(client_socket, 1) != 0) {
        error_code = net_last_error_code();
        net_socket_close(client_socket);
        net_set_last_error_code(error_code);
        return NET_INVALID_SOCKET;
    }
    return client_socket;
}

NetSocket net_accept_client(NetSocket server_socket,
                            char *peer_address,
                            size_t address_capacity,
                            uint16_t *peer_port)
{
    struct sockaddr_in address;
#ifdef _WIN32
    int address_length = (int)sizeof(address);
#else
    socklen_t address_length = (socklen_t)sizeof(address);
#endif
    NetSocket client_socket;

    memset(&address, 0, sizeof(address));
    client_socket = accept(server_socket,
                           (struct sockaddr *)&address,
                           &address_length);
    if (client_socket == NET_INVALID_SOCKET) {
        return NET_INVALID_SOCKET;
    }
    if (peer_address != NULL && address_capacity > 0) {
#ifdef _WIN32
        const char *printable_address = inet_ntoa(address.sin_addr);

        if (printable_address == NULL) {
            peer_address[0] = '\0';
        } else {
            snprintf(peer_address, address_capacity, "%s", printable_address);
        }
#else
        if (inet_ntop(AF_INET,
                      &address.sin_addr,
                      peer_address,
                      address_capacity) == NULL) {
            peer_address[0] = '\0';
        }
#endif
    }
    if (peer_port != NULL) {
        *peer_port = ntohs(address.sin_port);
    }
    return client_socket;
}

int net_receive(NetSocket socket, void *buffer, size_t capacity)
{
    int received;

    if (buffer == NULL || capacity == 0 || capacity > INT_MAX) {
        return NET_RECEIVE_ERROR;
    }
#ifdef _WIN32
    received = recv(socket, (char *)buffer, (int)capacity, 0);
#else
    received = (int)recv(socket, buffer, capacity, 0);
#endif
    if (received < 0) {
        return net_error_is_timeout(net_last_error_code())
            ? NET_RECEIVE_TIMEOUT
            : NET_RECEIVE_ERROR;
    }
    return received;
}

int net_send_all(NetSocket socket, const void *buffer, size_t length)
{
    const unsigned char *bytes = (const unsigned char *)buffer;
    size_t total_sent = 0;

    if (buffer == NULL || length > INT_MAX) {
        return -1;
    }
    while (total_sent < length) {
        int sent;

#ifdef _WIN32
        sent = send(socket,
                    (const char *)(bytes + total_sent),
                    (int)(length - total_sent),
                    0);
#else
#ifdef MSG_NOSIGNAL
        sent = (int)send(socket,
                         bytes + total_sent,
                         length - total_sent,
                         MSG_NOSIGNAL);
#else
        sent = (int)send(socket,
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

int net_set_receive_timeout(NetSocket socket, uint32_t timeout_ms)
{
#ifdef _WIN32
    DWORD value = (DWORD)timeout_ms;

    return setsockopt(socket,
                      SOL_SOCKET,
                      SO_RCVTIMEO,
                      (const char *)&value,
                      (int)sizeof(value)) == 0
        ? 0
        : -1;
#else
    struct timeval value;

    value.tv_sec = (time_t)(timeout_ms / 1000U);
    value.tv_usec = (suseconds_t)((timeout_ms % 1000U) * 1000U);
    return setsockopt(socket,
                      SOL_SOCKET,
                      SO_RCVTIMEO,
                      &value,
                      sizeof(value)) == 0
        ? 0
        : -1;
#endif
}

int net_set_send_timeout(NetSocket socket, uint32_t timeout_ms)
{
#ifdef _WIN32
    DWORD value = (DWORD)timeout_ms;

    return setsockopt(socket,
                      SOL_SOCKET,
                      SO_SNDTIMEO,
                      (const char *)&value,
                      (int)sizeof(value)) == 0
        ? 0
        : -1;
#else
    struct timeval value;

    value.tv_sec = (time_t)(timeout_ms / 1000U);
    value.tv_usec = (suseconds_t)((timeout_ms % 1000U) * 1000U);
    return setsockopt(socket,
                      SOL_SOCKET,
                      SO_SNDTIMEO,
                      &value,
                      sizeof(value)) == 0
        ? 0
        : -1;
#endif
}

void net_socket_close(NetSocket socket)
{
    if (socket == NET_INVALID_SOCKET) {
        return;
    }
#ifdef _WIN32
    closesocket(socket);
#else
    close(socket);
#endif
}

const char *net_last_error_message(void)
{
    int error_code = net_last_error_code();

#ifdef _WIN32
    snprintf(net_error_buffer,
             sizeof(net_error_buffer),
             "Winsock error %d",
             error_code);
#else
    snprintf(net_error_buffer,
             sizeof(net_error_buffer),
             "%s",
             strerror(error_code));
#endif
    return net_error_buffer;
}

uint64_t net_monotonic_milliseconds(void)
{
#ifdef _WIN32
    LARGE_INTEGER counter;
    LARGE_INTEGER frequency;
    uint64_t seconds;
    uint64_t remainder;

    if (!QueryPerformanceFrequency(&frequency)
        || !QueryPerformanceCounter(&counter)
        || frequency.QuadPart <= 0) {
        return (uint64_t)GetTickCount();
    }
    seconds = (uint64_t)(counter.QuadPart / frequency.QuadPart);
    remainder = (uint64_t)(counter.QuadPart % frequency.QuadPart);
    return seconds * 1000U
        + remainder * 1000U / (uint64_t)frequency.QuadPart;
#else
    struct timespec now;

    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) {
        return 0;
    }
    return (uint64_t)now.tv_sec * 1000U
        + (uint64_t)now.tv_nsec / 1000000U;
#endif
}
