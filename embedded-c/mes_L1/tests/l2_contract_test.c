#include <stdio.h>
#include <string.h>

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
        "operationVoltage", 12.0, "V",
        1, 10.0, 1, 14.0, L1_INSPECTION_OK
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

int main(void)
{
    test_l1_messages_are_accepted_by_l2();
    test_l2_command_is_accepted_by_l1();

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
