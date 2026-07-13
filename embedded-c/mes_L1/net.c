#include "net.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    static char err_buf[256];
#else
    #include <unistd.h>
    #include <sys/socket.h>
    #include <arpa/inet.h>
    #include <errno.h>
#endif

int net_init(void) {
#ifdef _WIN32
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        return -1;
    }
#endif
    return 0;
}

void net_cleanup(void) {
#ifdef _WIN32
    WSACleanup();
#endif
}

// L1 클라이언트용 TCP 연결 기능 구현
socket_t net_connect(const char *ip, int port) {
    socket_t sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock == SOCKET_INVALID) {
        return SOCKET_INVALID;
    }

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);

    // IP 주소 문자열 변환
    if (inet_pton(AF_INET, ip, &server_addr.sin_addr) <= 0) {
        net_close(sock);
        return SOCKET_INVALID;
    }

    // L2 서버 수집기에 연결 시도
    if (connect(sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        net_close(sock);
        return SOCKET_INVALID;
    }

    return sock;
}

int net_send(socket_t sock, const uint8_t *buf, size_t len) {
    size_t total_sent = 0;
    while (total_sent < len) {
#ifdef _WIN32
        int sent = send(sock, (const char *)(buf + total_sent), (int)(len - total_sent), 0);
#else
        // Linux 환경에서 상대방이 소켓을 닫았을 때 프로세스가 시그널(SIGPIPE)로 죽는 것을 방지
        int sent = send(sock, buf + total_sent, len - total_sent, MSG_NOSIGNAL);
#endif
        if (sent <= 0) {
            return -1;
        }
        total_sent += sent;
    }
    return (int)total_sent;
}

// 명세서 v0.2 수신 대응을 위한 단순 수신 인터페이스
int net_recv_exact(socket_t sock, uint8_t *buf, size_t len) {
#ifdef _WIN32
    int n = recv(sock, (char *)buf, (int)len, 0);
#else
    int n = recv(sock, buf, len, 0);
#endif
    return n;
}

void net_close(socket_t sock) {
    if (sock != SOCKET_INVALID) {
#ifdef _WIN32
        closesocket(sock);
#else
        close(sock);
#endif
    }
}

const char* net_last_error(void) {
#ifdef _WIN32
    int err = WSAGetLastError();
    snprintf(err_buf, sizeof(err_buf), "WSA Error Code: %d", err);
    return err_buf;
#else
    return strerror(errno);
#endif
}