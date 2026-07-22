#include "scheduler.h"

#include <stdio.h>
#include <time.h>

#ifdef _WIN32
#include <windows.h>
#endif

#include "api_client.h"
#include "collector.h"
#include "config.h"
#include "protocol.h"
#include "thread_compat.h"

static const char *configured_machines[COLLECTOR_MAX_L1_CONNECTIONS] = {
    "EQ-WIND-01",
    "EQ-WELD-01",
    "EQ-ASSY-01",
    "EQ-SEAL-01",
    "EQ-TEST-01",
    "EQ-PACK-01"
};

static volatile int scheduler_running;
static volatile int scheduler_worker_active;
static int scheduler_initialized;

static void report_connection_status(void)
{
    size_t connected = collector_connected_machine_count();
    int http_status = 0;
    ApiClientResult result = api_client_send_connection_status(
        COLLECTOR_ID,
        connected,
        COLLECTOR_MAX_L1_CONNECTIONS,
        &http_status);

    if (result != API_CLIENT_OK) {
        fprintf(stderr,
                "[L2 Status] report failed result=%s http=%d connected=%u/%d\n",
                api_client_result_name(result),
                http_status,
                (unsigned int)connected,
                COLLECTOR_MAX_L1_CONNECTIONS);
        fflush(stderr);
    }
}

static void command_id_to_text(int64_t value, char output[21])
{
    char reversed[20];
    size_t length = 0;
    size_t index;

    do {
        reversed[length++] = (char)('0' + (value % 10));
        value /= 10;
    } while (value > 0);
    for (index = 0; index < length; ++index) {
        output[index] = reversed[length - index - 1U];
    }
    output[length] = '\0';
}

static void scheduler_sleep_milliseconds(unsigned int milliseconds)
{
#ifdef _WIN32
    Sleep(milliseconds);
#else
    struct timespec request;

    request.tv_sec = (time_t)(milliseconds / 1000U);
    request.tv_nsec = (long)(milliseconds % 1000U) * 1000000L;
    nanosleep(&request, NULL);
#endif
}

static void release_failed_command(const ProtocolCommand *command,
                                   CollectorSendResult send_result)
{
    int http_status = 0;
    char command_id_text[21];
    ApiClientResult release_result = api_client_release_command(
        command->command_id,
        command->machine_id,
        &http_status);

    command_id_to_text(command->command_id, command_id_text);

    if (release_result == API_CLIENT_OK) {
        fprintf(stderr,
                "[L2 Polling] L1 send failed; command returned to PENDING "
                "id=%s machine=%s send=%d\n",
                command_id_text,
                command->machine_id,
                (int)send_result);
    } else {
        fprintf(stderr,
                "[L2 Polling] CRITICAL: L1 send failed and command release failed "
                "id=%s machine=%s send=%d release=%s http=%d\n",
                command_id_text,
                command->machine_id,
                (int)send_result,
                api_client_result_name(release_result),
                http_status);
    }
    fflush(stderr);
}

static void dispatch_commands_for_machine(const char *machine_id)
{
    ProtocolCommand commands[API_CLIENT_MAX_COMMANDS];
    size_t command_count = 0;
    size_t index;
    int http_status = 0;
    ApiClientResult fetch_result;

    if (!collector_is_machine_connected(machine_id)) {
        return;
    }
    fetch_result = api_client_fetch_pending_commands(
        machine_id,
        commands,
        API_CLIENT_MAX_COMMANDS,
        &command_count,
        &http_status);
    if (fetch_result != API_CLIENT_OK) {
        fprintf(stderr,
                "[L2 Polling] Backend poll failed machine=%s result=%s http=%d\n",
                machine_id,
                api_client_result_name(fetch_result),
                http_status);
        fflush(stderr);
        return;
    }

    for (index = 0; index < command_count; ++index) {
        ProtocolResult protocol_result = PROTOCOL_RESULT_OK;
        CollectorSendResult send_result;
        ProtocolCommand *command = &commands[index];
        char command_id_text[21];

        command_id_to_text(command->command_id, command_id_text);

        if (!collector_is_machine_connected(command->machine_id)) {
            release_failed_command(command, COLLECTOR_SEND_NOT_REGISTERED);
            continue;
        }
        send_result = collector_send_command_to_machine(command,
                                                        &protocol_result);
        if (send_result != COLLECTOR_SEND_OK) {
            fprintf(stderr,
                    "[L2 Polling] command send failed id=%s machine=%s "
                    "send=%d protocol=%s\n",
                    command_id_text,
                    command->machine_id,
                    (int)send_result,
                    protocol_result_name(protocol_result));
            release_failed_command(command, send_result);
            continue;
        }
        printf("[L2 Polling] command sent id=%s type=%s machine=%s "
               "process=%s lot=%s inputQty=%d\n",
               command_id_text,
               protocol_command_type_name(command->type),
               command->machine_id,
               command->process_code,
               command->lot_no,
               command->input_qty);
        fflush(stdout);
    }
}

static void scheduler_worker(void *context)
{
    time_t last_status_report = 0;

    (void)context;
    while (scheduler_running) {
        size_t index;
        unsigned int waited = 0;
        time_t now = time(NULL);

        if (last_status_report == 0
            || now - last_status_report >= COLLECTOR_STATUS_INTERVAL_SECONDS) {
            report_connection_status();
            last_status_report = now;
        }

        for (index = 0;
             scheduler_running && index < COLLECTOR_MAX_L1_CONNECTIONS;
             ++index) {
            dispatch_commands_for_machine(configured_machines[index]);
        }
        while (scheduler_running
               && waited < COMMAND_POLL_INTERVAL_SECONDS * 1000U) {
            scheduler_sleep_milliseconds(100U);
            waited += 100U;
        }
    }
    scheduler_worker_active = 0;
}

int scheduler_init(void)
{
    if (scheduler_initialized) {
        return 0;
    }
    scheduler_running = 1;
    scheduler_worker_active = 1;
    scheduler_initialized = 1;
    if (collector_thread_start_detached(scheduler_worker, NULL) != 0) {
        scheduler_running = 0;
        scheduler_worker_active = 0;
        scheduler_initialized = 0;
        return -1;
    }
    return 0;
}

void scheduler_cleanup(void)
{
    if (!scheduler_initialized) {
        return;
    }
    scheduler_running = 0;
    while (scheduler_worker_active) {
        scheduler_sleep_milliseconds(100U);
    }
    scheduler_initialized = 0;
}
