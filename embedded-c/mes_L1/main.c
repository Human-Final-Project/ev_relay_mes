/*
 * EV Relay MES L1 설비 시뮬레이터 (프로토콜 명세서 v0.2 준수 및 모듈화 완료 버전)
 */

#ifndef _WIN32
    #define _POSIX_C_SOURCE 199309L
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>

#ifdef _WIN32
    #include <windows.h>
#else
    #include <time.h>
#endif

#include "net.h"
#include "protocol.h" // ? 프로토콜 헤더 추가

#define L2_SERVER_IP "127.0.0.1"
#define L2_SERVER_PORT 9000  

// 설비 공유 상태 구조체
typedef struct {
    socket_t sock;
    volatile int connected;
    pthread_mutex_t lock;
} L1DeviceState;

/* 플랫폼 독립적 sleep 함수 (밀리초 단위) */
static void sleep_ms(int milliseconds) {
#ifdef _WIN32
    Sleep(milliseconds);
#else
    struct timespec ts;
    ts.tv_sec = milliseconds / 1000;
    ts.tv_nsec = (milliseconds % 1000) * 1000000L;
    nanosleep(&ts, NULL);
#endif
}

/* 데이터 전송 래퍼 함수 */
static int send_mes_message(socket_t sock, const char *msg) {
    int len = strlen(msg);
    if (len <= 0) return 0;

    int sent = net_send(sock, (const uint8_t *)msg, len);
    if (sent < 0) {
        printf("[L1 -> ERROR] 메시지 전송 실패\n");
        return -1;
    }
    printf("[L1 -> L2] 송신: %s", msg); 
    return sent;
}

/* [스레드 1] Heartbeat 전송 전용 스레드 (5초 주기) */
static void *heartbeat_thread_func(void *arg) {
    L1DeviceState *state = (L1DeviceState *)arg;
    char buffer[1024];

    sleep_ms(5000);

    while (state->connected) {
        pthread_mutex_lock(&state->lock);
        
        // ? protocol 모듈 함수 사용
        build_heartbeat_msg(buffer, sizeof(buffer), MACH_COIL_WINDING);
        
        if (send_mes_message(state->sock, buffer) < 0) {
            state->connected = 0;
            pthread_mutex_unlock(&state->lock);
            break;
        }
        pthread_mutex_unlock(&state->lock);

        sleep_ms(5000);
    }
    return NULL;
}

/* [스레드 2] 메인 시뮬레이션 루프 */
static void run_simulation_loop(socket_t sock) {
    L1DeviceState state;
    state.sock = sock;
    state.connected = 1;
    pthread_mutex_init(&state.lock, NULL);

    char buffer[1024];

    // 연결 직후 HELLO 전송 
    build_hello_msg(buffer, sizeof(buffer), MACH_COIL_WINDING);
    if (send_mes_message(state.sock, buffer) < 0) {
        net_close(sock);
        pthread_mutex_destroy(&state.lock);
        return;
    }

    // 하트비트 전송 스레드 기동
    pthread_t hb_tid;
    pthread_create(&hb_tid, NULL, heartbeat_thread_func, &state);

    int step = 0;
    const char *lot_no = "EVR-LOT-20260708-001"; 

    while (state.connected) {
        sleep_ms(1000);
        step++;

        if (step % 10 == 1) {
            // [이벤트 1] 설비 상태 변경: 가동 (RUNNING)
            pthread_mutex_lock(&state.lock);
            build_status_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, STATUS_RUNNING, lot_no, PROC_COIL_WINDING, "production_started");
            if (send_mes_message(state.sock, buffer) < 0) state.connected = 0;
            pthread_mutex_unlock(&state.lock);
        } 
       else if (step % 10 == 4) {
            // [이벤트 2] 제품 불량 발생 시뮬레이션
             pthread_mutex_lock(&state.lock);
    
            // ★ DEFECT_WELD_STRENGTH 대신 코일 설비에 맞는 DEFECT_COIL_SHORT 사용!
            build_defect_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, PROC_COIL_WINDING, lot_no, DEFECT_COIL_SHORT, 3);
    
            if (send_mes_message(state.sock, buffer) < 0) state.connected = 0;
            pthread_mutex_unlock(&state.lock);
        }
        else if (step % 10 == 6) {
            // [이벤트 3] 임의의 설비 알람 발생
            pthread_mutex_lock(&state.lock);
            // ★ 하드코딩 제거: 확장한 모듈 함수 적용
            build_alarm_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, ALARM_MOTOR_OVERLOAD, ALARM_LVL_ERROR);
            if (send_mes_message(state.sock, buffer) < 0) state.connected = 0;
            pthread_mutex_unlock(&state.lock);
        }
        else if (step % 10 == 8) {
            // [이벤트 4] 생산 실적 전송
            pthread_mutex_lock(&state.lock);
            int ok_qty = 97;
            int ng_qty = 3;
            build_production_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, PROC_COIL_WINDING, lot_no, ok_qty, ng_qty, PROD_COMPLETED);
            if (send_mes_message(state.sock, buffer) < 0) state.connected = 0;
            pthread_mutex_unlock(&state.lock);
        }
        else if (step % 10 == 9) {
            // [이벤트 5] 설비 상태 변경: 대기 (IDLE)로 복귀
            pthread_mutex_lock(&state.lock);
            build_status_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, STATUS_IDLE, NULL, PROC_COIL_WINDING, "production_finished");
            if (send_mes_message(state.sock, buffer) < 0) state.connected = 0;
            pthread_mutex_unlock(&state.lock);
        }
    }

    pthread_join(hb_tid, NULL);
    pthread_mutex_destroy(&state.lock);
    net_close(sock);
}

int main(void) {
    srand((unsigned int)time(NULL));

    if (net_init() != 0) {
        fprintf(stderr, "네트워크 인프라 초기화 실패\n");
        return 1;
    }

    printf("==================================================\n");
    printf("    EV Relay MES L1 설비 가동 및 L2 연결 시도\n");
    printf("    설비 코드: %s\n", MACH_COIL_WINDING);
    printf("==================================================\n");

    while (1) {
        printf("[L1 연결] L2 서버 수집기(%s:%d) 접속 중...\n", L2_SERVER_IP, L2_SERVER_PORT);
        
        socket_t sock = net_connect(L2_SERVER_IP, L2_SERVER_PORT);
        
        if (sock == SOCKET_INVALID) {
            printf("[L1 재연결] 연결 실패. 3초 후 재시도 합니다...\n");
            sleep_ms(3000); 
            continue;
        }

        printf("[L1 연결] L2 수집기 연결 성공! 시뮬레이션을 시작합니다.\n");
        run_simulation_loop(sock);
        
        printf("[L1 연결] 서버와의 세션 종료. 3초 후 재접속 시도합니다.\n");
        sleep_ms(3000);
    }

    net_cleanup();
    return 0;
}