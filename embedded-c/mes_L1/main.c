/*
 * EV Relay MES L1 설비 시뮬레이터 (사양서 v2026-07-13 완벽 준수 버전)
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

typedef struct {
    const char *proc_code;
    const char *mach_id;
} MachineConfig;

static const MachineConfig PROCESS_CHAIN[] = {
    { PROC_COIL_WINDING, MACH_COIL_WINDING }, // OP20
    { PROC_WELDING,      MACH_WELDING },      // OP30
    { PROC_ASSEMBLY,     MACH_ASSEMBLY },     // OP40_OP50
    { PROC_SEALING,      MACH_SEALING },      // OP60
    { PROC_INSPECTION,   MACH_INSPECTION },   // OP70
    { PROC_PACKING,      MACH_PACKING }       // OP80
};
#define TOTAL_PROCESS_STEPS 6

typedef struct {
    socket_t sock;
    volatile int connected;
    pthread_mutex_t lock;
    volatile int running; // 작업명령에 의한 가동 상태 제어 플래그
} L1DeviceState;

/* 플랫폼 독립적 sleep 함수 */
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

static int get_min(int a, int b) {
    return (a < b) ? a : b;
}

/* 데이터 전송 래퍼 */
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

/* [공통 로직] 개별 단일 공정 시뮬레이션 처리 함수 */
static void simulate_single_step(L1DeviceState *state, const MachineConfig *cfg, const char *lot_no, int input, int ng, int ok) {
    char buffer[1024];
    
    printf("\n>> Processing: %s [%s] (Input Qty: %d)\n", cfg->proc_code, cfg->mach_id, input);

    // 1. RUNNING 상태 보고
    pthread_mutex_lock(&state->lock);
    build_status_msg(buffer, sizeof(buffer), cfg->mach_id, STATUS_RUNNING, lot_no, cfg->proc_code, "START");
    send_mes_message(state->sock, buffer);
    pthread_mutex_unlock(&state->lock);

    sleep_ms(1500); // 가동 시간 시뮬레이션

    // 2. DEFECT 발생 시 보고
    if (ng > 0) {
        pthread_mutex_lock(&state->lock);
        const char *defect_code = "STANDARD_NG";
        if (strcmp(cfg->proc_code, PROC_COIL_WINDING) == 0)      defect_code = DEFECT_COIL_SHORT;
        else if (strcmp(cfg->proc_code, PROC_WELDING) == 0) defect_code = DEFECT_WELD_STRENGTH;
        
        build_defect_msg(buffer, sizeof(buffer), cfg->mach_id, cfg->proc_code, lot_no, defect_code, ng);
        send_mes_message(state->sock, buffer);
        pthread_mutex_unlock(&state->lock);
    }

    // 3. 임의의 확률로 ALARM 보고
    if (rand() % 5 == 0) {
        pthread_mutex_lock(&state->lock);
        build_alarm_msg(buffer, sizeof(buffer), cfg->mach_id, ALARM_MOTOR_OVERLOAD, ALARM_LVL_ERROR);
        send_mes_message(state->sock, buffer);
        pthread_mutex_unlock(&state->lock);
    }

    // 4. PRODUCTION 실적 완료 요약 보고
    pthread_mutex_lock(&state->lock);
    build_production_msg(buffer, sizeof(buffer), cfg->mach_id, cfg->proc_code, lot_no, ok, ng, PROD_COMPLETED);
    send_mes_message(state->sock, buffer);
    pthread_mutex_unlock(&state->lock);

    // 5. IDLE 상태 복귀 보고
    pthread_mutex_lock(&state->lock);
    build_status_msg(buffer, sizeof(buffer), cfg->mach_id, STATUS_IDLE, NULL, cfg->proc_code, "END");
    send_mes_message(state->sock, buffer);
    pthread_mutex_unlock(&state->lock);
}

/* [스레드 2] 메인 명령 수신 및 전체 시뮬레이션 제어 루프 */
static void run_simulation_loop(socket_t sock) {
    L1DeviceState state;
    state.sock = sock;
    state.connected = 1;
    state.running = 0;
    pthread_mutex_init(&state.lock, NULL);

    char buffer[1024];
    char recv_buf[1024];

    // [사양서] 연결 직후 HELLO 1회 전송
    build_hello_msg(buffer, sizeof(buffer), MACH_COIL_WINDING);
    if (send_mes_message(state.sock, buffer) < 0) {
        net_close(sock);
        pthread_mutex_destroy(&state.lock);
        return;
    }

    pthread_t hb_tid;
    pthread_create(&hb_tid, NULL, heartbeat_thread_func, &state);

    printf("\n[L1 State] Waiting for START command from L2 Collector...\n");

    // 명령 수신 대기 루프
    while (state.connected) {
        memset(recv_buf, 0, sizeof(recv_buf));
        int len = net_recv_exact(state.sock, (uint8_t *)recv_buf, sizeof(recv_buf) - 1);
        
        if (len <= 0) {
            printf("[L1 -> ERROR] Connection lost while waiting for command.\n");
            state.connected = 0;
            break;
        }

        recv_buf[len] = '\0';
        printf("[L2 -> L1] RX: %s", recv_buf);

        // 사양서 규격 파싱 검증: V1,COMMAND,cmdId,COMMAND_NAME,...
        // 예시: V1,COMMAND,101,START,EQ-WIND-01,OP20,LOT-001,100
        if (strstr(recv_buf, ",COMMAND,") != NULL) {
            char cmd_id[16] = {0};
            char cmd_name[16] = {0};
            char mach_id[32] = {0};
            char proc_code[16] = {0};
            char lot_no[64] = {0};
            int target_qty = 0;

            // 안전한 토큰 분리 분석
            sscanf(recv_buf, "V1,COMMAND,%[^,],%[^,],%[^,],%[^,],%[^,],%d", 
                   cmd_id, cmd_name, mach_id, proc_code, lot_no, &target_qty);

            if (strcmp(cmd_name, "START") == 0) {
                printf("[L1 -> CMD] START Command Accepted. (Lot: %s, Qty: %d)\n", lot_no, target_qty);
                
                // [사양서 규칙] 수신 즉시 COMMAND_ACK 송신
                pthread_mutex_lock(&state.lock);
                snprintf(buffer, sizeof(buffer), "V1,COMMAND_ACK,%s,%s,ACCEPTED,-\n", mach_id, cmd_id);
                send_mes_message(state.sock, buffer);
                pthread_mutex_unlock(&state.lock);

                state.running = 1; // 가동 시작

                // --- [사양서 반영] 시뮬레이션 수량 연학 시작 ---
                int op20_input = target_qty;
                int op20_ng    = rand() % 3;
                int op20_ok    = op20_input - op20_ng;

                int op30_input = target_qty;
                int op30_ng    = rand() % 3;
                int op30_ok    = op30_input - op30_ng;

                printf("\n--- [%s] Production Pipeline Multi-Route Activated ---\n", lot_no);
                
                // 1. OP20 및 OP30 병렬 실행 시뮬레이션
                simulate_single_step(&state, &PROCESS_CHAIN[0], lot_no, op20_input, op20_ng, op20_ok);
                simulate_single_step(&state, &PROCESS_CHAIN[1], lot_no, op30_input, op30_ng, op30_ok);

                // 2. OP40_OP50 합류 (수량 제어 법칙 적용)
                int op40_input = get_min(op20_ok, op30_ok);
                int op40_ng    = rand() % 2;
                int op40_ok    = op40_input - op40_ng;
                simulate_single_step(&state, &PROCESS_CHAIN[2], lot_no, op40_input, op40_ng, op40_ok);

                // 3. OP60 직렬 전이
                int op60_input = op40_ok;
                int op60_ng    = 0;
                int op60_ok    = op60_input - op60_ng;
                simulate_single_step(&state, &PROCESS_CHAIN[3], lot_no, op60_input, op60_ng, op60_ok);

                // 4. OP70 검사 공정 (불량 유도)
                int op70_input = op60_ok;
                int op70_ng    = rand() % 3;
                int op70_ok    = op70_input - op70_ng;
                simulate_single_step(&state, &PROCESS_CHAIN[4], lot_no, op70_input, op70_ng, op70_ok);

                // 5. OP80 포장 및 최종 완제품 도출
                int op80_input = op70_ok;
                int op80_ng    = 0;
                int op80_ok    = op80_input - op80_ng;
                simulate_single_step(&state, &PROCESS_CHAIN[5], lot_no, op80_input, op80_ng, op80_ok);

                printf("\n🎉 [%s] Pipeline Finished. Final Finished Yield (OP80 okQty): %d EA\n", lot_no, op80_ok);
                printf("[L1 State] Returning to standby mode. Waiting for next batch...\n");
                
                state.running = 0;
            }
            else if (strcmp(cmd_name, "STOP") == 0) {
                // 단순 토글 처리 방어 코드
                pthread_mutex_lock(&state.lock);
                snprintf(buffer, sizeof(buffer), "V1,COMMAND_ACK,%s,%s,ACCEPTED,-\n", mach_id, cmd_id);
                send_mes_message(state.sock, buffer);
                pthread_mutex_unlock(&state.lock);
                state.running = 0;
                printf("[L1 -> CMD] STOP Command Processed.\n");
            }
        }
    }

    state.connected = 0; 
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
    printf("     EV Relay MES L1 Y-Merge Pipeline Simulator   \n");
    printf("     Compliance Version [Revised: 2026-07-13]     \n");
    printf("==================================================\n");

    while (1) {
        printf("[L1 Connect] Connecting to L2 Server (%s:%d)...\n", L2_SERVER_IP, L2_SERVER_PORT);
        socket_t sock = net_connect(L2_SERVER_IP, L2_SERVER_PORT);
        
        if (sock == SOCKET_INVALID) {
            printf("[L1 Reconnect] Connection failed. Retrying in 3 seconds...\n");
            sleep_ms(3000); 
            continue;
        }

        printf("[L1 Connect] Connected to L2 successfully!\n");
        run_simulation_loop(sock);
        
        printf("[L1 Connect] Session closed. Reconnecting in 3 seconds...\n");
        sleep_ms(3000);
    }

    net_cleanup();
    return 0;
}