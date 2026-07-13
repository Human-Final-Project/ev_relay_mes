/*
 * EV Relay MES L1 설비 시뮬레이터 (공정 체인 및 수량 전이 검증 버전)
 * 콘솔 출력 언어: 영어 (UTF-8 환경 대응) / 주석: 한글 유지
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
#include "protocol.h" 

#define L2_SERVER_IP "127.0.0.1"
#define L2_SERVER_PORT 9000  

// 6단계 전체 공정 정의 (사양서 기준)
const char* PROCESS_CHAIN[] = {
    "OP20", 
    "OP30", 
    "OP40_OP50", 
    "OP60", 
    "OP70", 
    "OP80"
};
#define TOTAL_PROCESS_STEPS 6

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
        printf("[L1 -> ERROR] Failed to send message.\n");
        return -1;
    }
    printf("[L1 -> L2] TX: %s", msg); 
    return sent;
}

/* [스레드 1] Heartbeat 전송 전용 스레드 (5초 주기) */
static void *heartbeat_thread_func(void *arg) {
    L1DeviceState *state = (L1DeviceState *)arg;
    char buffer[1024];

    sleep_ms(5000);

    while (state->connected) {
        pthread_mutex_lock(&state->lock);
        
        // HELLO와 HEARTBEAT는 DB에 저장되지 않고 L2 연결 상태 확인용으로만 전송됨 (사양서 규칙 4)
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

/* [스레드 2] 메인 시뮬레이션 루프 (전체 공정 파이프라인 및 수량 법칙 검증) */
static void run_simulation_loop(socket_t sock) {
    L1DeviceState state;
    state.sock = sock;
    state.connected = 1;
    pthread_mutex_init(&state.lock, NULL);

    char buffer[1024];

    // [사양서] 연결 직후 L2 확인용 HELLO 1회 전송 (DB 저장 안 됨)
    build_hello_msg(buffer, sizeof(buffer), MACH_COIL_WINDING);
    if (send_mes_message(state.sock, buffer) < 0) {
        net_close(sock);
        pthread_mutex_destroy(&state.lock);
        return;
    }

    // 하트비트 전송 스레드 기동
    pthread_t hb_tid;
    pthread_create(&hb_tid, NULL, heartbeat_thread_func, &state);

    const char *lot_no = "EVR-LOT-20260713-001"; 
    
    // 최초 투입 수량 설정 (원하는 어떤 숫자가 들어와도 로직이 성립함)
    int current_input_qty = 100; 
    char last_sent_status[16] = ""; // 중복 상태 전송 방지용 버퍼

    printf("\n--- [%s] Production Pipeline Simulation Started (Initial Input: %d) ---\n", lot_no, current_input_qty);

    // 6단계 공정을 순차적으로 수행
    for (int i = 0; i < TOTAL_PROCESS_STEPS; i++) {
        if (!state.connected) break;

        const char *current_op = PROCESS_CHAIN[i];
        printf("\n>> Processing: %s (Input Qty: %d)\n", current_op, current_input_qty);

        // 1. [규칙 3] 설비 가동 상태로 변경 (실제 변경 시점에만 1회 전송)
        if (strcmp(last_sent_status, STATUS_RUNNING) != 0) {
            pthread_mutex_lock(&state.lock);
            build_status_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, STATUS_RUNNING, lot_no, current_op, "START");
            if (send_mes_message(state.sock, buffer) < 0) state.connected = 0;
            pthread_mutex_unlock(&state.lock);
            strcpy(last_sent_status, STATUS_RUNNING);
        }

        sleep_ms(1500); // 공정 처리 대기 시뮬레이션

        // 2. 불량 수량 동적 산출 (사양서 시나리오를 검증하기 위해 0~3개 무작위 발생)
        // MVP 규칙 7: 불량품은 재작업 없이 즉시 폐기 처리되므로 차감 대상이 됨
        int defect_1 = rand() % 2; // COIL_RESISTANCE_NG 발생 수량
        int defect_2 = rand() % 2; // COIL_SHORT_NG 발생 수량
        int total_ng_qty = defect_1 + defect_2;

        // 만약 투입 수량보다 불량이 많이 나오면 보정
        if (total_ng_qty > current_input_qty) {
            total_ng_qty = current_input_qty;
            defect_1 = total_ng_qty;
            defect_2 = 0;
        }

        int ok_qty = current_input_qty - total_ng_qty;

        // 3. [규칙 2] 불량이 발생했을 때만 defect_histories 상세 적재용 DEFECT 전송
        if (defect_1 > 0) {
            pthread_mutex_lock(&state.lock);
            build_defect_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, current_op, lot_no, "COIL_RESISTANCE_NG", defect_1);
            send_mes_message(state.sock, buffer);
            pthread_mutex_unlock(&state.lock);
        }
        if (defect_2 > 0) {
            pthread_mutex_lock(&state.lock);
            build_defect_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, current_op, lot_no, "COIL_SHORT_NG", defect_2);
            send_mes_message(state.sock, buffer);
            pthread_mutex_unlock(&state.lock);
        }

        // 4. [규칙 2] 임의의 확률(20%)로 설비 이상이 발생했을 때만 알람 전송
        if (rand() % 5 == 0) {
            pthread_mutex_lock(&state.lock);
            build_alarm_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, "MOTOR_OVERLOAD", ALARM_LVL_ERROR);
            send_mes_message(state.sock, buffer);
            pthread_mutex_unlock(&state.lock);
        }

        // 5. [규칙 1] 공정 완료 요약은 무조건 production_logs에 저장 (PRODUCTION 전송)
        pthread_mutex_lock(&state.lock);
        build_production_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, current_op, lot_no, ok_qty, total_ng_qty, PROD_COMPLETED);
        if (send_mes_message(state.sock, buffer) < 0) state.connected = 0;
        pthread_mutex_unlock(&state.lock);

        // 6. [규칙 3] 공정 종료 후 대기 상태 복귀 (실제 변경 시점에만 1회 전송)
        if (strcmp(last_sent_status, STATUS_IDLE) != 0) {
            pthread_mutex_lock(&state.lock);
            build_status_msg(buffer, sizeof(buffer), MACH_COIL_WINDING, STATUS_IDLE, NULL, current_op, "END");
            if (send_mes_message(state.sock, buffer) < 0) state.connected = 0;
            pthread_mutex_unlock(&state.lock);
            strcpy(last_sent_status, STATUS_IDLE);
        }

        // 7. [규칙 5, 6] 다음 공정 투입량 설정 (inputQty = 이전 공정 okQty)
        if (strcmp(current_op, "OP80") == 0) {
            // [규칙 6] 마지막 공정인 OP80의 정상 수량이 최종 완제품 수량이 됨
            printf("\n🎉 [%s] All Steps Completed! Final Finished Products (OP80 okQty): %d pcs\n", lot_no, ok_qty);
        } else {
            current_input_qty = ok_qty; // 연속성 유지
        }

        sleep_ms(1000); // 공정 간 전환 텀
    }

    state.connected = 0; // 루프가 정상 종료되면 하트비트 스레드도 함께 종료되도록 유도
    pthread_join(hb_tid, NULL);
    pthread_mutex_destroy(&state.lock);
    net_close(sock);
}

int main(void) {
    srand((unsigned int)time(NULL));

    if (net_init() != 0) {
        fprintf(stderr, "Network infrastructure initialization failed.\n");
        return 1;
    }

    printf("==================================================\n");
    printf("     EV Relay MES L1 6-Step Pipeline Simulator\n");
    printf("     Quantity Transition Rule Verification Mode\n");
    printf("==================================================\n");

    while (1) {
        printf("[L1 Connect] Connecting to L2 Server (%s:%d)...\n", L2_SERVER_IP, L2_SERVER_PORT);
        
        socket_t sock = net_connect(L2_SERVER_IP, L2_SERVER_PORT);
        
        if (sock == SOCKET_INVALID) {
            printf("[L1 Reconnect] Connection failed. Retrying in 3 seconds...\n");
            sleep_ms(3000); 
            continue;
        }

        printf("[L1 Connect] Connected to L2 successfully! Starting simulation...\n");
        run_simulation_loop(sock);
        
        printf("[L1 Connect] Session closed. Reconnecting in 3 seconds...\n");
        sleep_ms(3000);
    }

    net_cleanup();
    return 0;
}