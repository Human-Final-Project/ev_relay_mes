#ifndef NET_H
#define NET_H

#include <stdint.h>
#include <stddef.h>

#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    typedef SOCKET socket_t;
    #define SOCKET_INVALID INVALID_SOCKET
#else
    typedef int socket_t;
    #define SOCKET_INVALID (-1)
#endif

// 네트워크 인프라 초기화 및 해제 (Windows 필수, Linux 호환용)
int net_init(void);
void net_cleanup(void);

// L1 클라이언트용: L2 서버 주소로 TCP 연결 시도
socket_t net_connect(const char *ip, int port);

// 데이터 전송 및 수신
int net_send(socket_t sock, const uint8_t *buf, size_t len);
int net_recv_exact(socket_t sock, uint8_t *buf, size_t len);

// 소켓 닫기 및 에러 메시지 확인
void net_close(socket_t sock);
const char* net_last_error(void);

#endif // NET_H