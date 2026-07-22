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
    start_runtime(&runtime, "EQ-WIND-01", 5, 2, &actions);
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

int main(void)
{
    test_process_event_shapes();
    test_replays_unreported_unit();
    test_three_percent_combined_ng();
    test_error_and_resume();
    if (checks_failed != 0) {
        fprintf(stderr, "%d of %d machine runtime checks failed.\n",
                checks_failed, checks_run);
        return EXIT_FAILURE;
    }
    printf("PASS: %d machine runtime checks\n", checks_run);
    return EXIT_SUCCESS;
}
