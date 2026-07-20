#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "device_config.h"
#include "machine_runtime.h"

static int checks_run;
static int checks_failed;

#define CHECK(condition)                                                     \
    do {                                                                     \
        ++checks_run;                                                        \
        if (!(condition)) {                                                  \
            ++checks_failed;                                                 \
            fprintf(stderr,                                                  \
                    "FAIL %s:%d: %s\n",                                    \
                    __FILE__,                                                \
                    __LINE__,                                                \
                    #condition);                                             \
        }                                                                    \
    } while (0)

static L1Command make_command(L1CommandType type,
                              int64_t command_id,
                              const char *lot_no,
                              int input_qty)
{
    L1Command command;

    memset(&command, 0, sizeof(command));
    command.command_id = command_id;
    command.type = type;
    strcpy(command.machine_id, "EQ-WIND-01");
    strcpy(command.process_code, "OP20");
    strcpy(command.lot_no, lot_no);
    command.input_qty = input_qty;
    return command;
}

static void start_runtime(L1MachineRuntime *runtime,
                          int error_after_qty,
                          int target_qty,
                          L1RuntimeActions *actions)
{
    L1Command start = make_command(L1_COMMAND_START,
                                   101,
                                   "EVR-LOT-001",
                                   target_qty);

    l1_machine_runtime_init(runtime,
                            l1_device_config_find("EQ-WIND-01"),
                            error_after_qty);
    CHECK(l1_machine_runtime_handle_command(runtime,
                                            &start,
                                            actions) == 0);
    CHECK(actions->count == 2);
    CHECK(actions->actions[0].type == L1_RUNTIME_ACTION_COMMAND_ACK);
    CHECK(actions->actions[0].data.command_ack.status == L1_ACK_ACCEPTED);
    CHECK(actions->actions[1].type == L1_RUNTIME_ACTION_MACHINE_STATUS);
    CHECK(actions->actions[1].data.machine_status.status
          == L1_MACHINE_RUNNING);
    CHECK(runtime->state == L1_RUNTIME_RUNNING);
    CHECK(strcmp(runtime->lot_no, "EVR-LOT-001") == 0);
    CHECK(runtime->target_qty == target_qty);
}

static void test_error_flushes_partial_before_alarm_and_status(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;

    start_runtime(&runtime, 3, 10, &actions);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 0);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 0);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);

    CHECK(actions.count == 3);
    CHECK(actions.actions[0].type == L1_RUNTIME_ACTION_PRODUCTION);
    CHECK(actions.actions[0].data.production.input_qty == 3);
    CHECK(actions.actions[0].data.production.ok_qty == 3);
    CHECK(actions.actions[0].data.production.ng_qty == 0);
    CHECK(actions.actions[0].data.production.status
          == L1_PRODUCTION_RUNNING);
    CHECK(actions.actions[1].type == L1_RUNTIME_ACTION_ALARM);
    CHECK(strcmp(actions.actions[1].data.alarm.alarm_code,
                 "WIRE_BREAK") == 0);
    CHECK(actions.actions[1].data.alarm.alarm_level == L1_ALARM_ERROR);
    CHECK(actions.actions[2].type == L1_RUNTIME_ACTION_MACHINE_STATUS);
    CHECK(actions.actions[2].data.machine_status.status == L1_MACHINE_ERROR);
    CHECK(runtime.state == L1_RUNTIME_ERROR_PAUSED);
    CHECK(runtime.processed_qty == 3);
    CHECK(runtime.reported_qty == 0);
    CHECK(l1_machine_runtime_remaining_qty(&runtime) == 7);
    CHECK(strcmp(runtime.lot_no, "EVR-LOT-001") == 0);

    CHECK(l1_machine_runtime_mark_reported(&runtime, 3) == 0);
    CHECK(runtime.reported_qty == 3);
    CHECK(l1_machine_runtime_unreported_qty(&runtime) == 0);

    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 0);
    CHECK(runtime.processed_qty == 3);
}

static void test_resume_rejects_wrong_quantity_then_finishes_remaining(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command wrong_resume;
    L1Command resume;
    int index;

    start_runtime(&runtime, 3, 10, &actions);
    for (index = 0; index < 3; ++index) {
        CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    }
    CHECK(l1_machine_runtime_mark_reported(&runtime, 3) == 0);

    wrong_resume = make_command(L1_COMMAND_RESUME,
                                201,
                                "EVR-LOT-001",
                                8);
    CHECK(l1_machine_runtime_handle_command(&runtime,
                                            &wrong_resume,
                                            &actions) == 0);
    CHECK(actions.count == 1);
    CHECK(actions.actions[0].data.command_ack.status == L1_ACK_REJECTED);
    CHECK(strcmp(actions.actions[0].data.command_ack.message,
                 "resume_quantity_mismatch") == 0);
    CHECK(runtime.state == L1_RUNTIME_ERROR_PAUSED);

    resume = make_command(L1_COMMAND_RESUME,
                          202,
                          "EVR-LOT-001",
                          7);
    CHECK(l1_machine_runtime_handle_command(&runtime,
                                            &resume,
                                            &actions) == 0);
    CHECK(actions.count == 2);
    CHECK(actions.actions[0].type == L1_RUNTIME_ACTION_COMMAND_ACK);
    CHECK(actions.actions[0].data.command_ack.status == L1_ACK_ACCEPTED);
    CHECK(actions.actions[1].type == L1_RUNTIME_ACTION_MACHINE_STATUS);
    CHECK(actions.actions[1].data.machine_status.status
          == L1_MACHINE_RUNNING);
    CHECK(strcmp(actions.actions[1].data.machine_status.message,
                 "production_resumed") == 0);
    CHECK(runtime.state == L1_RUNTIME_RUNNING);

    for (index = 0; index < 6; ++index) {
        CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
        CHECK(actions.count == 0);
    }
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 2);
    CHECK(actions.actions[0].type == L1_RUNTIME_ACTION_PRODUCTION);
    CHECK(actions.actions[0].data.production.input_qty == 7);
    CHECK(actions.actions[0].data.production.status
          == L1_PRODUCTION_COMPLETED);
    CHECK(actions.actions[1].type == L1_RUNTIME_ACTION_MACHINE_STATUS);
    CHECK(actions.actions[1].data.machine_status.status == L1_MACHINE_IDLE);
    CHECK(strcmp(actions.actions[1].data.machine_status.lot_no, "-") == 0);
    CHECK(runtime.state == L1_RUNTIME_IDLE);
    CHECK(runtime.processed_qty == 10);
    CHECK(l1_machine_runtime_mark_reported(&runtime, 7) == 0);
    CHECK(runtime.reported_qty == 10);
}

static void test_duplicate_command_id_only_repeats_ack(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command duplicate = make_command(L1_COMMAND_START,
                                       101,
                                       "EVR-LOT-001",
                                       5);

    start_runtime(&runtime, 0, 5, &actions);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(runtime.processed_qty == 1);

    CHECK(l1_machine_runtime_handle_command(&runtime,
                                            &duplicate,
                                            &actions) == 0);
    CHECK(actions.count == 1);
    CHECK(actions.actions[0].type == L1_RUNTIME_ACTION_COMMAND_ACK);
    CHECK(actions.actions[0].data.command_ack.status == L1_ACK_ACCEPTED);
    CHECK(strcmp(actions.actions[0].data.command_ack.message,
                 "command_received") == 0);
    CHECK(runtime.state == L1_RUNTIME_RUNNING);
    CHECK(runtime.processed_qty == 1);
    CHECK(runtime.target_qty == 5);
}

static void test_error_threshold_at_target_completes_without_error(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    int index;

    start_runtime(&runtime, 5, 5, &actions);
    for (index = 0; index < 4; ++index) {
        CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
        CHECK(actions.count == 0);
    }
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 2);
    CHECK(actions.actions[0].type == L1_RUNTIME_ACTION_PRODUCTION);
    CHECK(actions.actions[0].data.production.input_qty == 5);
    CHECK(actions.actions[0].data.production.status
          == L1_PRODUCTION_COMPLETED);
    CHECK(actions.actions[1].data.machine_status.status == L1_MACHINE_IDLE);
}

static void test_stop_flushes_progress_and_can_resume(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command stop;
    L1Command resume;

    start_runtime(&runtime, 0, 5, &actions);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    stop = make_command(L1_COMMAND_STOP, 301, "EVR-LOT-001", 0);
    CHECK(l1_machine_runtime_handle_command(&runtime,
                                            &stop,
                                            &actions) == 0);
    CHECK(actions.count == 3);
    CHECK(actions.actions[0].type == L1_RUNTIME_ACTION_COMMAND_ACK);
    CHECK(actions.actions[1].type == L1_RUNTIME_ACTION_PRODUCTION);
    CHECK(actions.actions[1].data.production.input_qty == 2);
    CHECK(actions.actions[1].data.production.status
          == L1_PRODUCTION_RUNNING);
    CHECK(actions.actions[2].data.machine_status.status
          == L1_MACHINE_STOPPED);
    CHECK(runtime.state == L1_RUNTIME_STOPPED);
    CHECK(l1_machine_runtime_mark_reported(&runtime, 2) == 0);

    resume = make_command(L1_COMMAND_RESUME,
                          302,
                          "EVR-LOT-001",
                          3);
    CHECK(l1_machine_runtime_handle_command(&runtime,
                                            &resume,
                                            &actions) == 0);
    CHECK(actions.actions[0].data.command_ack.status == L1_ACK_ACCEPTED);
    CHECK(runtime.state == L1_RUNTIME_RUNNING);
}


static void test_op70_sends_measurements_without_production(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command start;
    size_t index;

    memset(&start, 0, sizeof(start));
    start.command_id = 701;
    start.type = L1_COMMAND_START;
    strcpy(start.machine_id, "EQ-TEST-01");
    strcpy(start.process_code, "OP70");
    strcpy(start.lot_no, "EVR-LOT-070");
    start.input_qty = 2;

    l1_machine_runtime_init(&runtime,
                            l1_device_config_find("EQ-TEST-01"),
                            0);
    CHECK(l1_machine_runtime_handle_command(&runtime, &start, &actions) == 0);
    CHECK(actions.count == 2);
    CHECK(strcmp(actions.actions[1].data.machine_status.message,
                 "inspection_started") == 0);

    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 3);
    for (index = 0; index < actions.count; ++index) {
        CHECK(actions.actions[index].type == L1_RUNTIME_ACTION_INSPECTION);
        CHECK(actions.actions[index].data.inspection.unit_seq == 1);
    }
    CHECK(strcmp(actions.actions[0].data.inspection.item,
                 "OPERATION_VOLTAGE") == 0);
    CHECK(strcmp(actions.actions[1].data.inspection.item,
                 "COIL_RESISTANCE") == 0);
    CHECK(strcmp(actions.actions[2].data.inspection.item,
                 "CONTACT_RESISTANCE") == 0);
    CHECK(actions.actions[2].completes_unit == 1);
    CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);

    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 4);
    CHECK(actions.actions[0].type == L1_RUNTIME_ACTION_INSPECTION);
    CHECK(actions.actions[0].data.inspection.unit_seq == 2);
    CHECK(actions.actions[1].type == L1_RUNTIME_ACTION_INSPECTION);
    CHECK(actions.actions[2].type == L1_RUNTIME_ACTION_INSPECTION);
    CHECK(actions.actions[3].type == L1_RUNTIME_ACTION_MACHINE_STATUS);
    CHECK(actions.actions[3].data.machine_status.status == L1_MACHINE_IDLE);
    CHECK(strcmp(actions.actions[3].data.machine_status.message,
                 "inspection_finished") == 0);
    CHECK(runtime.state == L1_RUNTIME_IDLE);
    CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);
    CHECK(runtime.reported_qty == 2);
}


static void test_op70_replays_unreported_measurement_unit(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command start;

    memset(&start, 0, sizeof(start));
    start.command_id = 705;
    start.type = L1_COMMAND_START;
    strcpy(start.machine_id, "EQ-TEST-01");
    strcpy(start.process_code, "OP70");
    strcpy(start.lot_no, "EVR-LOT-075");
    start.input_qty = 2;

    l1_machine_runtime_init(&runtime,
                            l1_device_config_find("EQ-TEST-01"),
                            0);
    CHECK(l1_machine_runtime_handle_command(&runtime, &start, &actions) == 0);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 3);
    CHECK(actions.actions[0].data.inspection.unit_seq == 1);
    CHECK(runtime.processed_qty == 1);
    CHECK(runtime.reported_qty == 0);

    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 3);
    CHECK(actions.actions[0].data.inspection.unit_seq == 1);
    CHECK(actions.actions[2].completes_unit == 1);
    CHECK(runtime.processed_qty == 1);

    CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);
    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.actions[0].data.inspection.unit_seq == 2);
}

static void test_op70_resume_continues_unit_sequence(void)
{
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command start;
    L1Command resume;

    memset(&start, 0, sizeof(start));
    start.command_id = 711;
    start.type = L1_COMMAND_START;
    strcpy(start.machine_id, "EQ-TEST-01");
    strcpy(start.process_code, "OP70");
    strcpy(start.lot_no, "EVR-LOT-071");
    start.input_qty = 4;

    l1_machine_runtime_init(&runtime,
                            l1_device_config_find("EQ-TEST-01"),
                            2);
    CHECK(l1_machine_runtime_handle_command(&runtime, &start, &actions) == 0);

    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.actions[0].data.inspection.unit_seq == 1);
    CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);

    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.count == 5);
    CHECK(actions.actions[0].data.inspection.unit_seq == 2);
    CHECK(actions.actions[2].completes_unit == 1);
    CHECK(actions.actions[3].type == L1_RUNTIME_ACTION_ALARM);
    CHECK(actions.actions[4].type == L1_RUNTIME_ACTION_MACHINE_STATUS);
    CHECK(runtime.state == L1_RUNTIME_ERROR_PAUSED);
    CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);

    resume = make_command(L1_COMMAND_RESUME,
                          712,
                          "EVR-LOT-071",
                          2);
    CHECK(l1_machine_runtime_handle_command(&runtime, &resume, &actions) == 0);
    CHECK(actions.actions[0].data.command_ack.status == L1_ACK_ACCEPTED);

    CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
    CHECK(actions.actions[0].data.inspection.unit_seq == 3);
}

static void test_six_devices_have_registered_error_codes(void)
{
    const char *expected[] = {
        "WIRE_BREAK",
        "WELD_POWER_ERROR",
        "ASSEMBLY_JAM",
        "CHAMBER_PRESSURE_ERROR",
        "TEST_PROBE_ERROR",
        "LABEL_PRINTER_ERROR"
    };
    size_t index;

    CHECK(l1_device_config_count()
          == sizeof(expected) / sizeof(expected[0]));
    for (index = 0; index < l1_device_config_count(); ++index) {
        const L1DeviceConfig *device = l1_device_config_at(index);

        CHECK(device != NULL);
        CHECK(strcmp(device->error_alarm_code, expected[index]) == 0);
        CHECK(device->error_message != NULL);
        CHECK(device->error_message[0] != '\0');
    }
}

int main(void)
{
    test_error_flushes_partial_before_alarm_and_status();
    test_resume_rejects_wrong_quantity_then_finishes_remaining();
    test_duplicate_command_id_only_repeats_ack();
    test_error_threshold_at_target_completes_without_error();
    test_stop_flushes_progress_and_can_resume();
    test_op70_sends_measurements_without_production();
    test_op70_replays_unreported_measurement_unit();
    test_op70_resume_continues_unit_sequence();
    test_six_devices_have_registered_error_codes();

    if (checks_failed != 0) {
        fprintf(stderr,
                "%d of %d machine runtime checks failed.\n",
                checks_failed,
                checks_run);
        return EXIT_FAILURE;
    }
    printf("PASS: %d machine runtime checks\n", checks_run);
    return EXIT_SUCCESS;
}
