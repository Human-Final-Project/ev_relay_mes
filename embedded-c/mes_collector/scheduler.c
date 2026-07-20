#include "scheduler.h"

#include <stdio.h>
#include <string.h>

#include "api_client.h"
#include "collector.h"
#include "command_json.h"
#include "config.h"
#include "thread_compat.h"

#define SCHEDULER_COMMAND_QUEUE_CAPACITY 64
#define SCHEDULER_STOP_CHECK_INTERVAL_MS 100U

typedef struct {
    ProtocolCommand commands[SCHEDULER_COMMAND_QUEUE_CAPACITY];
    size_t count;
} SchedulerCommandQueue;

typedef struct {
    CollectorMutex mutex;
    CollectorThread thread;
    int initialized;
    int stop_requested;
    SchedulerCommandQueue queue;
} SchedulerRuntime;

static SchedulerRuntime scheduler_runtime;

static int stop_is_requested(SchedulerRuntime *runtime)
{
    int stop_requested;

    collector_mutex_lock(&runtime->mutex);
    stop_requested = runtime->stop_requested;
    collector_mutex_unlock(&runtime->mutex);
    return stop_requested;
}

static int queue_contains(const SchedulerCommandQueue *queue,
                          int64_t command_id)
{
    size_t index;

    for (index = 0; index < queue->count; ++index) {
        if (queue->commands[index].command_id == command_id) {
            return 1;
        }
    }
    return 0;
}

static int queue_add(SchedulerCommandQueue *queue,
                     const ProtocolCommand *command)
{
    if (queue_contains(queue, command->command_id)) {
        return 1;
    }
    if (queue->count >= SCHEDULER_COMMAND_QUEUE_CAPACITY) {
        return -1;
    }
    queue->commands[queue->count++] = *command;
    return 0;
}

static void queue_remove(SchedulerCommandQueue *queue, size_t index)
{
    if (index >= queue->count) {
        return;
    }
    if (index + 1U < queue->count) {
        memmove(&queue->commands[index],
                &queue->commands[index + 1U],
                (queue->count - index - 1U) * sizeof(queue->commands[0]));
    }
    --queue->count;
}

static void fetch_pending_commands(SchedulerRuntime *runtime)
{
    char json[API_CLIENT_PENDING_JSON_CAPACITY];
    ProtocolCommand commands[SCHEDULER_COMMAND_QUEUE_CAPACITY];
    size_t json_length = 0;
    size_t command_count = 0;
    size_t index;
    int http_status = 0;
    ApiClientResult api_result = api_client_get_pending_commands(
        json,
        sizeof(json),
        &json_length,
        &http_status);
    CommandJsonResult parse_result;

    if (api_result != API_CLIENT_OK) {
        fprintf(stderr,
                "[L2 Polling] GET pending commands failed=%s status=%d retry=%d\n",
                api_client_result_name(api_result),
                http_status,
                MES_HTTP_MAX_RETRIES);
        fflush(stderr);
        return;
    }
    parse_result = command_json_parse_pending(
        json,
        json_length,
        commands,
        SCHEDULER_COMMAND_QUEUE_CAPACITY,
        &command_count);
    if (parse_result != COMMAND_JSON_OK) {
        fprintf(stderr,
                "[L2 Polling] Invalid pending command response: %s\n",
                command_json_result_name(parse_result));
        fflush(stderr);
        return;
    }
    for (index = 0; index < command_count; ++index) {
        int add_result = queue_add(&runtime->queue, &commands[index]);

        if (add_result < 0) {
            fprintf(stderr,
                    "[L2 Polling] Command queue full: commandId=%lld capacity=%d\n",
                    (long long)commands[index].command_id,
                    SCHEDULER_COMMAND_QUEUE_CAPACITY);
            fflush(stderr);
        } else if (add_result == 0) {
            printf("[Backend -> L2] queued commandId=%lld type=%s machine=%s inputQty=%d\n",
                   (long long)commands[index].command_id,
                   protocol_command_type_name(commands[index].type),
                   commands[index].machine_id,
                   commands[index].input_qty);
            fflush(stdout);
        }
    }
}

static void dispatch_queued_commands(SchedulerRuntime *runtime)
{
    size_t index = 0;

    while (index < runtime->queue.count) {
        ProtocolCommand *command = &runtime->queue.commands[index];
        ProtocolResult protocol_result = PROTOCOL_RESULT_OK;
        CollectorSendResult send_result = collector_send_command_to_machine(
            command,
            &protocol_result);

        if (send_result == COLLECTOR_SEND_OK) {
            printf("[L2 -> L1] commandId=%lld type=%s machine=%s dispatched\n",
                   (long long)command->command_id,
                   protocol_command_type_name(command->type),
                   command->machine_id);
            fflush(stdout);
            queue_remove(&runtime->queue, index);
            continue;
        }
        if (send_result != COLLECTOR_SEND_NOT_REGISTERED) {
            fprintf(stderr,
                    "[L2 -> L1] commandId=%lld machine=%s failed=%d protocol=%s; kept in memory queue\n",
                    (long long)command->command_id,
                    command->machine_id,
                    (int)send_result,
                    protocol_result_name(protocol_result));
            fflush(stderr);
        }
        ++index;
    }
}

static void wait_for_next_poll(SchedulerRuntime *runtime)
{
    unsigned int remaining = COMMAND_POLL_INTERVAL_SECONDS * 1000U;

    while (remaining > 0U && !stop_is_requested(runtime)) {
        unsigned int slice = remaining > SCHEDULER_STOP_CHECK_INTERVAL_MS
            ? SCHEDULER_STOP_CHECK_INTERVAL_MS
            : remaining;

        collector_thread_sleep_milliseconds(slice);
        remaining -= slice;
    }
}

static void scheduler_worker(void *context)
{
    SchedulerRuntime *runtime = (SchedulerRuntime *)context;

    while (!stop_is_requested(runtime)) {
        fetch_pending_commands(runtime);
        dispatch_queued_commands(runtime);
        wait_for_next_poll(runtime);
    }
}

int scheduler_init(void)
{
    SchedulerRuntime *runtime = &scheduler_runtime;

    if (runtime->initialized) {
        return -1;
    }
    memset(runtime, 0, sizeof(*runtime));
    if (collector_mutex_init(&runtime->mutex) != 0) {
        return -1;
    }
    if (collector_thread_start(&runtime->thread,
                               scheduler_worker,
                               runtime) != 0) {
        collector_mutex_destroy(&runtime->mutex);
        return -1;
    }
    runtime->initialized = 1;
    return 0;
}

void scheduler_cleanup(void)
{
    SchedulerRuntime *runtime = &scheduler_runtime;

    if (!runtime->initialized) {
        return;
    }
    collector_mutex_lock(&runtime->mutex);
    runtime->stop_requested = 1;
    collector_mutex_unlock(&runtime->mutex);
    collector_thread_join(&runtime->thread);
    collector_mutex_destroy(&runtime->mutex);
    memset(runtime, 0, sizeof(*runtime));
}
