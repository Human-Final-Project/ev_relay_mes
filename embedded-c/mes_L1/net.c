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

#ifdef _WIN32
#include <ws2tcpip.h>
#else
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
#endif

static char error_buffer[128];

static int l1_net_last_error_code(void)
{
#ifdef _WIN32
    return WSAGetLastError();
#else
    return errno;
#endif
}

static int l1_net_error_is_timeout(int error_code)
{
#ifdef _WIN32
    return error_code == WSAETIMEDOUT || error_code == WSAEWOULDBLOCK;
#else
    return error_code == EAGAIN || error_code == EWOULDBLOCK;
#endif
}

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
    int received;

    if (buffer == NULL || capacity == 0 || capacity > INT_MAX) {
        return L1_NET_RECEIVE_ERROR;
    }
#ifdef _WIN32
    received = recv(socket_handle, (char *)buffer, (int)capacity, 0);
#else
    received = (int)recv(socket_handle, buffer, capacity, 0);
#endif
    if (received < 0) {
        return l1_net_error_is_timeout(l1_net_last_error_code())
            ? L1_NET_RECEIVE_TIMEOUT
            : L1_NET_RECEIVE_ERROR;
    }
    return received;
}

int l1_net_set_receive_timeout(L1Socket socket_handle, uint32_t timeout_ms)
{
#ifdef _WIN32
    DWORD value = (DWORD)timeout_ms;

    return setsockopt(socket_handle,
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
    return setsockopt(socket_handle,
                      SOL_SOCKET,
                      SO_RCVTIMEO,
                      &value,
                      sizeof(value)) == 0
        ? 0
        : -1;
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

uint64_t l1_net_monotonic_milliseconds(void)
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
        return (uint64_t)time(NULL) * 1000U;
    }
    return (uint64_t)now.tv_sec * 1000U
        + (uint64_t)now.tv_nsec / 1000000U;
#endif
}

void l1_net_sleep_milliseconds(uint32_t duration_ms)
{
#ifdef _WIN32
    Sleep((DWORD)duration_ms);
#else
    struct timespec remaining;

    remaining.tv_sec = (time_t)(duration_ms / 1000U);
    remaining.tv_nsec = (long)(duration_ms % 1000U) * 1000000L;
    while (nanosleep(&remaining, &remaining) != 0 && errno == EINTR) {
    }
#endif
}
