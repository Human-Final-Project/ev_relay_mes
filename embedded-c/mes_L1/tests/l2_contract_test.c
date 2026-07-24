#include <stdio.h>
#include <string.h>

#include "../machine_runtime.h"
#include "../protocol.h"
#include "../../mes_collector/protocol.h"

static int tests_run;
static int tests_failed;

#define CHECK(condition)                                                       \
    do {                                                                       \
        ++tests_run;                                                           \
        if (!(condition)) {                                                    \
            ++tests_failed;                                                    \
            fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #condition); \
        }                                                                      \
    } while (0)

static void expect_l2_event(const char *line, ProtocolEventType expected_type)
{
    ProtocolMessage message;
    ProtocolResult result = protocol_parse_message(line, &message);

    CHECK(result == PROTOCOL_RESULT_OK);
    if (result == PROTOCOL_RESULT_OK) {
        CHECK(message.type == expected_type);
    } else {
        fprintf(stderr,
                "  L2 rejected message: %s\n",
                protocol_result_name(result));
    }
}

static void test_l1_messages_are_accepted_by_l2(void)
{
    char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t length;
    L1ProductionEvent production = {
        "EQ-WIND-01", "OP20", "EVR-LOT-001",
        100, 97, 3, L1_PRODUCTION_COMPLETED
    };
    L1InspectionEvent inspection = {
        "EQ-TEST-01", "OP70", "EVR-LOT-001",
        1, "OPERATION_VOLTAGE", 12.0, "V"
    };
    L1DefectEvent defect = {
        "EQ-WELD-01", "OP30", "EVR-LOT-001",
        "WELD_STRENGTH_NG", 3, "weld_strength_ng"
    };
    L1AlarmEvent alarm = {
        "EQ-WIND-01", "MOTOR_OVERLOAD",
        L1_ALARM_ERROR, "motor_overload"
    };
    L1MachineStatusEvent status = {
        "EQ-WIND-01", L1_MACHINE_RUNNING,
        "EVR-LOT-001", "OP20", "production_started"
    };
    L1CommandAckEvent ack = {
        "EQ-WIND-01", 101, L1_ACK_ACCEPTED, ""
    };

    CHECK(l1_protocol_build_hello(output,
                                  sizeof(output),
                                  "EQ-WIND-01",
                                  &length) == L1_PROTOCOL_OK);
    expect_l2_event(output, PROTOCOL_EVENT_HELLO);

    CHECK(l1_protocol_build_heartbeat(output,
                                      sizeof(output),
                                      "EQ-WIND-01",
                                      &length) == L1_PROTOCOL_OK);
    expect_l2_event(output, PROTOCOL_EVENT_HEARTBEAT);

    CHECK(l1_protocol_build_production(output,
                                       sizeof(output),
                                       &production,
                                       &length) == L1_PROTOCOL_OK);
    expect_l2_event(output, PROTOCOL_EVENT_PRODUCTION);

    production.input_qty = 40;
    production.ok_qty = 40;
    production.ng_qty = 0;
    production.status = L1_PRODUCTION_RUNNING;
    CHECK(l1_protocol_build_production(output,
                                       sizeof(output),
                                       &production,
                                       &length) == L1_PROTOCOL_OK);
    expect_l2_event(output, PROTOCOL_EVENT_PRODUCTION);

    CHECK(l1_protocol_build_inspection(output,
                                       sizeof(output),
                                       &inspection,
                                       &length) == L1_PROTOCOL_OK);
    expect_l2_event(output, PROTOCOL_EVENT_INSPECTION);

    CHECK(l1_protocol_build_defect(output,
                                   sizeof(output),
                                   &defect,
                                   &length) == L1_PROTOCOL_OK);
    expect_l2_event(output, PROTOCOL_EVENT_DEFECT);

    CHECK(l1_protocol_build_alarm(output,
                                  sizeof(output),
                                  &alarm,
                                  &length) == L1_PROTOCOL_OK);
    expect_l2_event(output, PROTOCOL_EVENT_ALARM);

    CHECK(l1_protocol_build_machine_status(output,
                                           sizeof(output),
                                           &status,
                                           &length) == L1_PROTOCOL_OK);
    expect_l2_event(output, PROTOCOL_EVENT_MACHINE_STATUS);

    CHECK(l1_protocol_build_command_ack(output,
                                        sizeof(output),
                                        &ack,
                                        &length) == L1_PROTOCOL_OK);
    expect_l2_event(output, PROTOCOL_EVENT_COMMAND_ACK);
}

static void test_l2_command_is_accepted_by_l1(void)
{
    char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t length;
    ProtocolCommand l2_command = {
        201,
        PROTOCOL_COMMAND_START,
        "EQ-WIND-01",
        "OP20",
        "EVR-LOT-002",
        50
    };
    L1Command l1_command;

    CHECK(protocol_build_command(output,
                                 sizeof(output),
                                 &l2_command,
                                 &length) == PROTOCOL_RESULT_OK);
    CHECK(l1_protocol_parse_command(output, &l1_command)
          == L1_PROTOCOL_OK);
    CHECK(l1_command.command_id == 201);
    CHECK(l1_command.type == L1_COMMAND_START);
    CHECK(strcmp(l1_command.machine_id, "EQ-WIND-01") == 0);
    CHECK(strcmp(l1_command.process_code, "OP20") == 0);
    CHECK(strcmp(l1_command.lot_no, "EVR-LOT-002") == 0);
    CHECK(l1_command.input_qty == 50);
}

static L1ProtocolResult build_runtime_action(
    const L1RuntimeAction *action,
    char *output,
    size_t output_capacity,
    size_t *output_length)
{
    switch (action->type) {
    case L1_RUNTIME_ACTION_COMMAND_ACK:
        return l1_protocol_build_command_ack(output,
                                             output_capacity,
                                             &action->data.command_ack,
                                             output_length);
    case L1_RUNTIME_ACTION_PRODUCTION:
        return l1_protocol_build_production(output,
                                            output_capacity,
                                            &action->data.production,
                                            output_length);
    case L1_RUNTIME_ACTION_INSPECTION:
        return l1_protocol_build_inspection(output,
                                            output_capacity,
                                            &action->data.inspection,
                                            output_length);
    case L1_RUNTIME_ACTION_JUDGMENT:
        return l1_protocol_build_judgment(output,
                                          output_capacity,
                                          &action->data.judgment,
                                          output_length);
    case L1_RUNTIME_ACTION_ALARM:
        return l1_protocol_build_alarm(output,
                                       output_capacity,
                                       &action->data.alarm,
                                       output_length);
    case L1_RUNTIME_ACTION_MACHINE_STATUS:
        return l1_protocol_build_machine_status(
            output,
            output_capacity,
            &action->data.machine_status,
            output_length);
    default:
        return L1_PROTOCOL_INVALID_VALUE;
    }
}

static ProtocolMessage parse_runtime_action(const L1RuntimeAction *action)
{
    char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t length = 0;
    ProtocolMessage message;

    memset(&message, 0, sizeof(message));
    CHECK(build_runtime_action(action,
                               output,
                               sizeof(output),
                               &length) == L1_PROTOCOL_OK);
    CHECK(protocol_parse_message(output, &message) == PROTOCOL_RESULT_OK);
    return message;
}

static void test_error_resume_flow_is_accepted_by_l2(void)
{
    const L1DeviceConfig *device = l1_device_config_find("EQ-SEAL-01");
    L1MachineRuntime runtime;
    L1RuntimeActions actions;
    L1Command command = {
        301,
        L1_COMMAND_START,
        "EQ-SEAL-01",
        "OP60",
        "EVR-LOT-003",
        10
    };
    ProtocolMessage message;
    int index;

    CHECK(device != NULL);
    l1_machine_runtime_init(&runtime, device, 4);
    CHECK(l1_machine_runtime_handle_command(&runtime,
                                             &command,
                                             &actions) == 0);
    CHECK(actions.count == 2);

    for (index = 0; index < 4; ++index) {
        CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
        CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);
    }
    CHECK(runtime.state == L1_RUNTIME_ERROR_PAUSED);
    CHECK(actions.count == 5);

    message = parse_runtime_action(&actions.actions[0]);
    CHECK(message.type == PROTOCOL_EVENT_JUDGMENT);
    CHECK(message.data.judgment.unit_seq == 4);

    message = parse_runtime_action(&actions.actions[1]);
    CHECK(message.type == PROTOCOL_EVENT_INSPECTION);
    CHECK(message.data.inspection.unit_seq == 4);

    message = parse_runtime_action(&actions.actions[2]);
    CHECK(message.type == PROTOCOL_EVENT_INSPECTION);
    CHECK(message.data.inspection.unit_seq == 4);

    message = parse_runtime_action(&actions.actions[3]);
    CHECK(message.type == PROTOCOL_EVENT_ALARM);
    CHECK(strcmp(message.data.alarm.alarm_code, "VACUUM_PUMP_ERROR") == 0);
    CHECK(strcmp(message.data.alarm.alarm_level, "ERROR") == 0);

    message = parse_runtime_action(&actions.actions[4]);
    CHECK(message.type == PROTOCOL_EVENT_MACHINE_STATUS);
    CHECK(strcmp(message.data.machine_status.status, "ERROR") == 0);

    command.command_id = 302;
    command.type = L1_COMMAND_RESUME;
    command.input_qty = 6;
    CHECK(l1_machine_runtime_handle_command(&runtime,
                                             &command,
                                             &actions) == 0);
    CHECK(actions.count == 2);
    message = parse_runtime_action(&actions.actions[0]);
    CHECK(message.type == PROTOCOL_EVENT_COMMAND_ACK);
    CHECK(strcmp(message.data.command_ack.ack_status, "ACCEPTED") == 0);
    message = parse_runtime_action(&actions.actions[1]);
    CHECK(message.type == PROTOCOL_EVENT_MACHINE_STATUS);
    CHECK(strcmp(message.data.machine_status.status, "RUNNING") == 0);

    for (index = 0; index < 6; ++index) {
        CHECK(l1_machine_runtime_tick(&runtime, &actions) == 0);
        CHECK(l1_machine_runtime_mark_reported(&runtime, 1) == 0);
    }
    CHECK(runtime.state == L1_RUNTIME_IDLE);
    CHECK(actions.count == 4);
    message = parse_runtime_action(&actions.actions[0]);
    CHECK(message.type == PROTOCOL_EVENT_JUDGMENT);
    CHECK(message.data.judgment.unit_seq == 10);
    message = parse_runtime_action(&actions.actions[1]);
    CHECK(message.type == PROTOCOL_EVENT_INSPECTION);
    message = parse_runtime_action(&actions.actions[2]);
    CHECK(message.type == PROTOCOL_EVENT_INSPECTION);
    message = parse_runtime_action(&actions.actions[3]);
    CHECK(message.type == PROTOCOL_EVENT_MACHINE_STATUS);
}

int main(void)
{
    test_l1_messages_are_accepted_by_l2();
    test_l2_command_is_accepted_by_l1();
    test_error_resume_flow_is_accepted_by_l2();

    if (tests_failed == 0) {
        printf("PASS: %d L1-L2 contract checks\n", tests_run);
        return 0;
    }
    fprintf(stderr,
            "FAILED: %d of %d L1-L2 contract checks\n",
            tests_failed,
            tests_run);
    return 1;
}
