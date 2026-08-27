#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "device_config.h"
#include "machine_runtime.h"

static int checks_run;
static int checks_failed;

#define CHECK(condition) do { \
    ++checks_run; \
    if (!(condition)) { \
        ++checks_failed; \
        fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #condition); \
    } \
} while (0)

static L1Command start_command(const L1DeviceConfig *device,
                               const char *lot_no,
                               int qty)
{
    L1Command command;
    memset(&command, 0, sizeof(command));
    command.command_id = 100 + qty;
    command.type = L1_COMMAND_START;
    strcpy(command.machine_id, device->machine_id);
    strcpy(command.process_code, device->process_code);
    strcpy(command.lot_no, lot_no);
    command.input_qty = qty;
    return command;
}

static void start_runtime(L1MachineRuntime *runtime,
                          const char *machine_id,
                          int qty,
                          int error_after,
                          L1RuntimeActions *actions)
{
    const L1DeviceConfig *device = l1_device_config_find(machine_id);
    L1Command command;
    CHECK(device != NULL);
    command = start_command(device, "LOT-RUNTIME-001", qty);
    l1_machine_runtime_init(runtime, device, error_after);
    CHECK(l1_machine_runtime_handle_command(runtime, &command, actions) == 0);
    CHECK(actions->count == 2);
    CHECK(actions->actions[0].type == L1_RUNTIME_ACTION_COMMAND_ACK);
    CHECK(actions->actions[1].type == L1_RUNTIME_ACTION_MACHINE_STATUS);
}

static int action_has_measurement_ng(const L1RuntimeActions *actions)
{
    size_t index;
    for (index = 0; index < actions->count; ++index) {
        const L1RuntimeAction *action = &actions->actions[index];
        if (action->type != L1_RUNTIME_ACTION_INSPECTION) continue;
        if ((strcmp(action->data.inspection.item, "COIL_RESISTANCE") == 0
             && action->data.inspection.value > 120.0)
            || (strcmp(action->data.inspection.item, "WELD_STRENGTH") == 0
                && action->data.inspection.value < 40.0)
            || (strcmp(action->data.inspection.item, "GAS_PRESSURE") == 0
                && action->data.inspection.value > 3.5)
            || (strcmp(action->data.inspection.item, "INSULATION_RESISTANCE") == 0
                && action->data.inspection.value < 100.0)) {
            return 1;
        }
    }
    return 0;
}

static void test_process_event_shapes(void)
{
    const char *machines[] = {
        "EQ-WIND-01", "EQ-WELD-01", "EQ-ASSY-01",
        "EQ-SEAL-01", "EQ-TEST-01", "EQ-PACK-01"
    };
    const size_t measurement_counts[] = {1, 3, 0, 2, 4, 0};
    size_t machine_index;

    for (machine_index = 0; machine_index < 6; ++machine_index) {
        L1MachineRuntime runtime;
        L1RuntimeActions actions;
        size_t index;
        size_t measurements = 0;
        start_runtime(&runtime, machines[machine_index], 2, 0, &actions);
        CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
        CHECK(actions.actions[0].type == L1_RUNTIME_ACTION_JUDGMENT);
        CHECK(actions.actions[0].data.judgment.unit_seq == 1);
        for (index = 0; index < actions.count; ++index) {
            if (actions.actions[index].type == L1_RUNTIME_ACTION_INSPECTION) {
                ++measurements;
            }
        }
        CHECK(measurements == measurement_counts[machine_index]);
        CHECK(actions.actions[actions.count - 1].completes_unit == 1);
        CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);
    }
}

static void test_replays_unreported_unit(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    start_runtime(&runtime, "EQ-TEST-01", 2, 0, &actions);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.actions[0].data.judgment.unit_seq == 1);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.actions[0].data.judgment.unit_seq == 1);
    CHECK(runtime.processed_qty == 1);
    CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.actions[0].data.judgment.unit_seq == 2);
}

static void test_three_percent_combined_ng(void)
{
    const char *machines[] = {
        "EQ-WIND-01", "EQ-WELD-01", "EQ-ASSY-01",
        "EQ-SEAL-01", "EQ-TEST-01", "EQ-PACK-01"
    };
    size_t machine_index;
    for (machine_index = 0; machine_index < 6; ++machine_index) {
        L1MachineRuntime runtime;
        L1RuntimeActions actions;
        int index;
        int ng_units = 0;
        start_runtime(&runtime, machines[machine_index], 100, 0, &actions);
        for (index = 0; index < 100; ++index) {
            int l1_ng;
            CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
            l1_ng = actions.actions[0].data.judgment.result == L1_JUDGMENT_NG;
            if (l1_ng || action_has_measurement_ng(&actions)) ++ng_units;
            CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);
        }
        CHECK(ng_units == 3);
        CHECK(runtime.state == L1_RUNTIME_IDLE);
    }
}

static void test_error_and_resume(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command resume;
    int index;
    start_runtime(&runtime, "EQ-ASSY-01", 5, 2, &actions);
    for (index = 0; index < 2; ++index) {
        CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
        CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);
    }
    CHECK(runtime.state == L1_RUNTIME_ERROR_PAUSED);
    CHECK(actions.actions[actions.count - 2].type == L1_RUNTIME_ACTION_ALARM);
    CHECK(actions.actions[actions.count - 1].type == L1_RUNTIME_ACTION_MACHINE_STATUS);

    memset(&resume, 0, sizeof(resume));
    resume.command_id = 999;
    resume.type = L1_COMMAND_RESUME;
    strcpy(resume.machine_id, runtime.device->machine_id);
    strcpy(resume.process_code, runtime.device->process_code);
    strcpy(resume.lot_no, runtime.lot_no);
    resume.input_qty = 3;
    CHECK(l1_machine_runtime_handle_command(&runtime, &resume, &actions) == 0);
    CHECK(runtime.state == L1_RUNTIME_RUNNING);
}


static void test_op20_op30_have_no_blocking_alarms(void)
{
    const char *machine_ids[] = {"EQ-WIND-01", "EQ-WELD-01"};
    size_t machine_index;

    for (machine_index = 0; machine_index < 2; ++machine_index) {
        const L1DeviceConfig *device =
            l1_device_config_find(machine_ids[machine_index]);
        size_t alarm_index;

        CHECK(device != NULL);
        CHECK(device->alarm_count > 0);
        for (alarm_index = 0; alarm_index < device->alarm_count; ++alarm_index) {
            const L1AlarmScenario *alarm =
                l1_device_alarm_at(device, alarm_index);
            CHECK(alarm != NULL);
            CHECK(alarm->level == L1_DEVICE_ALARM_WARNING);
            CHECK(alarm->stop_required == 0);
        }
    }
}

static void test_warning_alarm_does_not_pause_production(void)
{
    const L1DeviceConfig *device = l1_device_config_find("EQ-WELD-01");
    L1AlarmInjectionConfig injection;
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command command;
    size_t index;
    int alarm_found = 0;

    memset(&injection, 0, sizeof(injection));
    injection.mode = L1_ALARM_INJECTION_FIXED;
    injection.trigger_after_qty = 1;
    injection.probability_percent = 100;
    injection.alarm_code = "WELD_TIP_WEAR_WARN";
    l1_machine_runtime_init_with_alarm(&runtime, device, &injection);
    command = start_command(device, "LOT-WARNING-001", 3);
    CHECK(l1_machine_runtime_handle_command(&runtime, &command, &actions) == 0);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    for (index = 0; index < actions.count; ++index) {
        if (actions.actions[index].type == L1_RUNTIME_ACTION_ALARM) {
            alarm_found = 1;
            CHECK(actions.actions[index].data.alarm.alarm_level == L1_ALARM_WARNING);
            CHECK(strcmp(actions.actions[index].data.alarm.alarm_code,
                         "WELD_TIP_WEAR_WARN") == 0);
        }
    }
    CHECK(alarm_found);
    CHECK(runtime.state == L1_RUNTIME_RUNNING);
}

static void test_random_alarm_is_planned_once_per_start(void)
{
    const L1DeviceConfig *device = l1_device_config_find("EQ-SEAL-01");
    L1AlarmInjectionConfig injection;
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command command;

    memset(&injection, 0, sizeof(injection));
    injection.mode = L1_ALARM_INJECTION_RANDOM;
    injection.probability_percent = 100;
    l1_machine_runtime_init_with_alarm(&runtime, device, &injection);
    command = start_command(device, "LOT-RANDOM-001", 20);
    CHECK(l1_machine_runtime_handle_command(&runtime, &command, &actions) == 0);
    CHECK(runtime.alarm_scheduled);
    CHECK(runtime.selected_alarm != NULL);
    CHECK(runtime.alarm_after_qty >= 1);
    CHECK(runtime.alarm_after_qty < runtime.target_qty);

    injection.probability_percent = 0;
    l1_machine_runtime_init_with_alarm(&runtime, device, &injection);
    command = start_command(device, "LOT-RANDOM-002", 20);
    CHECK(l1_machine_runtime_handle_command(&runtime, &command, &actions) == 0);
    CHECK(!runtime.alarm_scheduled);
}


static void test_independent_lots_and_busy_start_rejection(void)
{
    const L1DeviceConfig *wind_device = l1_device_config_find("EQ-WIND-01");
    const L1DeviceConfig *seal_device = l1_device_config_find("EQ-SEAL-01");
    L1MachineRuntime wind_runtime;
    L1MachineRuntime seal_runtime;
    L1RuntimeActions wind_actions;
    L1RuntimeActions seal_actions;
    L1Command wind_start;
    L1Command seal_start;
    L1Command conflicting_start;

    CHECK(wind_device != NULL);
    CHECK(seal_device != NULL);
    l1_machine_runtime_init(&wind_runtime, wind_device, 0);
    l1_machine_runtime_init(&seal_runtime, seal_device, 0);

    wind_start = start_command(wind_device, "LOT-PIPE-003", 5);
    wind_start.command_id = 301;
    seal_start = start_command(seal_device, "LOT-PIPE-001", 7);
    seal_start.command_id = 601;

    CHECK(l1_machine_runtime_handle_command(&wind_runtime, &wind_start, &wind_actions) == 0);
    CHECK(l1_machine_runtime_handle_command(&seal_runtime, &seal_start, &seal_actions) == 0);
    CHECK(strcmp(wind_runtime.lot_no, "LOT-PIPE-003") == 0);
    CHECK(strcmp(seal_runtime.lot_no, "LOT-PIPE-001") == 0);
    CHECK(wind_actions.actions[0].data.command_ack.command_id == 301);
    CHECK(seal_actions.actions[0].data.command_ack.command_id == 601);
    CHECK(wind_actions.actions[0].data.command_ack.status == L1_ACK_ACCEPTED);
    CHECK(seal_actions.actions[0].data.command_ack.status == L1_ACK_ACCEPTED);

    conflicting_start = start_command(wind_device, "LOT-PIPE-004", 3);
    conflicting_start.command_id = 302;
    CHECK(l1_machine_runtime_handle_command(
              &wind_runtime, &conflicting_start, &wind_actions) == 0);
    CHECK(wind_actions.count == 1);
    CHECK(wind_actions.actions[0].type == L1_RUNTIME_ACTION_COMMAND_ACK);
    CHECK(wind_actions.actions[0].data.command_ack.command_id == 302);
    CHECK(wind_actions.actions[0].data.command_ack.status == L1_ACK_REJECTED);
    CHECK(strcmp(wind_actions.actions[0].data.command_ack.message,
                 "machine_not_idle") == 0);
    CHECK(strcmp(wind_runtime.lot_no, "LOT-PIPE-003") == 0);
    CHECK(wind_runtime.target_qty == 5);
    CHECK(wind_runtime.state == L1_RUNTIME_RUNNING);
    CHECK(strcmp(seal_runtime.lot_no, "LOT-PIPE-001") == 0);
}

int main(void)
{
    test_process_event_shapes();
    test_replays_unreported_unit();
    test_three_percent_combined_ng();
    test_error_and_resume();
    test_op20_op30_have_no_blocking_alarms();
    test_warning_alarm_does_not_pause_production();
    test_random_alarm_is_planned_once_per_start();
    test_independent_lots_and_busy_start_rejection();
    if (checks_failed != 0) {
        fprintf(stderr, "%d of %d machine runtime checks failed.\n",
                checks_failed, checks_run);
        return EXIT_FAILURE;
    }
    printf("PASS: %d machine runtime checks\n", checks_run);
    return EXIT_SUCCESS;
}
