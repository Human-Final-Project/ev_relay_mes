#include <stdio.h>
#include <string.h>

#include "device_config.h"
#include "protocol.h"

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

static void expect_command_result(const char *line,
                                  L1ProtocolResult expected)
{
    L1Command command;
    L1ProtocolResult actual =
        l1_protocol_parse_command(line, &command);

    CHECK(actual == expected);
    if (actual != expected) {
        fprintf(stderr,
                "  expected=%s actual=%s\n",
                l1_protocol_result_name(expected),
                l1_protocol_result_name(actual));
    }
}

static void test_device_config(void)
{
    static const char *machines[] = {
        "EQ-WIND-01",
        "EQ-WELD-01",
        "EQ-ASSY-01",
        "EQ-SEAL-01",
        "EQ-TEST-01",
        "EQ-PACK-01"
    };
    static const char *processes[] = {
        "OP20",
        "OP30",
        "OP40_OP50",
        "OP60",
        "OP70",
        "OP80"
    };
    size_t index;

    CHECK(l1_device_config_count() == 6);
    for (index = 0; index < l1_device_config_count(); ++index) {
        const L1DeviceConfig *config = l1_device_config_at(index);

        CHECK(config != NULL);
        CHECK(strcmp(config->machine_id, machines[index]) == 0);
        CHECK(strcmp(config->process_code, processes[index]) == 0);
        CHECK(l1_device_config_matches(machines[index], processes[index]));
    }
    CHECK(l1_device_config_at(6) == NULL);
    CHECK(l1_device_config_find("EQ-NOT-REAL") == NULL);
    CHECK(!l1_device_config_matches("EQ-WIND-01", "OP30"));
}

static void test_connection_messages(void)
{
    char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t length = 0;

    CHECK(l1_protocol_build_hello(output,
                                  sizeof(output),
                                  "EQ-WIND-01",
                                  &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output, "V1,HELLO,EQ-WIND-01\n") == 0);
    CHECK(length == strlen(output));

    CHECK(l1_protocol_build_heartbeat(output,
                                      sizeof(output),
                                      "EQ-WELD-01",
                                      &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output, "V1,HEARTBEAT,EQ-WELD-01\n") == 0);
    CHECK(l1_protocol_build_hello(output,
                                  sizeof(output),
                                  "EQ-NOT-REAL",
                                  &length) == L1_PROTOCOL_UNKNOWN_MACHINE);
}

static void test_production_message(void)
{
    char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t length;
    L1ProductionEvent event = {
        "EQ-WIND-01",
        "OP20",
        "EVR-LOT-001",
        100,
        97,
        3,
        L1_PRODUCTION_COMPLETED
    };

    CHECK(l1_protocol_build_production(output,
                                       sizeof(output),
                                       &event,
                                       &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output,
                 "V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-001,100,97,3,COMPLETED\n")
          == 0);

    event.input_qty = 40;
    event.ok_qty = 40;
    event.ng_qty = 0;
    event.status = L1_PRODUCTION_RUNNING;
    CHECK(l1_protocol_build_production(output,
                                       sizeof(output),
                                       &event,
                                       &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output,
                 "V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-001,40,40,0,RUNNING\n")
          == 0);

    event.ok_qty = 39;
    CHECK(l1_protocol_build_production(output,
                                       sizeof(output),
                                       &event,
                                       &length) == L1_PROTOCOL_INVALID_VALUE);
    event.ok_qty = 40;
    strcpy(event.process_code, "OP30");
    CHECK(l1_protocol_build_production(output,
                                       sizeof(output),
                                       &event,
                                       &length) == L1_PROTOCOL_PROCESS_MISMATCH);
}

static void test_inspection_message(void)
{
    char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t length;
    L1InspectionEvent event = {
        "EQ-TEST-01",
        "OP70",
        "EVR-LOT-001",
        1,
        "OPERATION_VOLTAGE",
        12.0,
        "V"
    };

    CHECK(l1_protocol_build_inspection(output,
                                       sizeof(output),
                                       &event,
                                       &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output,
                 "V1,INSPECTION,EQ-TEST-01,OP70,EVR-LOT-001,1,OPERATION_VOLTAGE,12.000,V\n")
          == 0);

    event.unit_seq = 0;
    CHECK(l1_protocol_build_inspection(output,
                                       sizeof(output),
                                       &event,
                                       &length) == L1_PROTOCOL_OUT_OF_RANGE);

    event.unit_seq = 1;
    event.unit[0] = '\0';
    CHECK(l1_protocol_build_inspection(output,
                                       sizeof(output),
                                       &event,
                                       &length) == L1_PROTOCOL_OK);
    CHECK(strstr(output, ",12.000,-\n") != NULL);
}

static void test_defect_alarm_and_status_messages(void)
{
    char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    size_t length;
    L1DefectEvent defect = {
        "EQ-WELD-01",
        "OP30",
        "EVR-LOT-001",
        "WELD_STRENGTH_NG",
        3,
        "weld_strength_ng"
    };
    L1AlarmEvent alarm = {
        "EQ-WIND-01",
        "MOTOR_OVERLOAD",
        L1_ALARM_ERROR,
        "motor_overload"
    };
    L1MachineStatusEvent status = {
        "EQ-WIND-01",
        L1_MACHINE_RUNNING,
        "EVR-LOT-001",
        "OP20",
        "production_started"
    };

    CHECK(l1_protocol_build_defect(output,
                                   sizeof(output),
                                   &defect,
                                   &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output,
                 "V1,DEFECT,EQ-WELD-01,OP30,EVR-LOT-001,WELD_STRENGTH_NG,3,weld_strength_ng\n")
          == 0);
    defect.message[0] = '\0';
    CHECK(l1_protocol_build_defect(output,
                                   sizeof(output),
                                   &defect,
                                   &length) == L1_PROTOCOL_OK);
    CHECK(strstr(output, ",3,-\n") != NULL);
    defect.defect_qty = 0;
    CHECK(l1_protocol_build_defect(output,
                                   sizeof(output),
                                   &defect,
                                   &length) == L1_PROTOCOL_OUT_OF_RANGE);

    CHECK(l1_protocol_build_alarm(output,
                                  sizeof(output),
                                  &alarm,
                                  &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output,
                 "V1,ALARM,EQ-WIND-01,MOTOR_OVERLOAD,ERROR,motor_overload\n")
          == 0);
    strcpy(alarm.alarm_code, "COMM_TIMEOUT");
    CHECK(l1_protocol_build_alarm(output,
                                  sizeof(output),
                                  &alarm,
                                  &length) == L1_PROTOCOL_UNEXPECTED_EVENT);

    CHECK(l1_protocol_build_machine_status(output,
                                           sizeof(output),
                                           &status,
                                           &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output,
                 "V1,MACHINE_STATUS,EQ-WIND-01,RUNNING,EVR-LOT-001,OP20,production_started\n")
          == 0);
    status.status = L1_MACHINE_IDLE;
    status.lot_no[0] = '\0';
    status.message[0] = '\0';
    CHECK(l1_protocol_build_machine_status(output,
                                           sizeof(output),
                                           &status,
                                           &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output,
                 "V1,MACHINE_STATUS,EQ-WIND-01,IDLE,-,OP20,-\n")
          == 0);
}

static void test_command_ack_message(void)
{
    char output[L1_PROTOCOL_MAX_MESSAGE_SIZE + 1];
    char small_output[16];
    size_t length;
    L1CommandAckEvent event = {
        "EQ-WIND-01",
        101,
        L1_ACK_ACCEPTED,
        ""
    };

    CHECK(l1_protocol_build_command_ack(output,
                                        sizeof(output),
                                        &event,
                                        &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output,
                 "V1,COMMAND_ACK,EQ-WIND-01,101,ACCEPTED,-\n")
          == 0);
    CHECK(length == strlen(output));

    event.status = L1_ACK_REJECTED;
    strcpy(event.message, "invalid_state");
    CHECK(l1_protocol_build_command_ack(output,
                                        sizeof(output),
                                        &event,
                                        &length) == L1_PROTOCOL_OK);
    CHECK(strcmp(output,
                 "V1,COMMAND_ACK,EQ-WIND-01,101,REJECTED,invalid_state\n")
          == 0);

    CHECK(l1_protocol_build_command_ack(small_output,
                                        sizeof(small_output),
                                        &event,
                                        &length)
          == L1_PROTOCOL_BUFFER_TOO_SMALL);
    CHECK(small_output[0] == '\0');
}

static void test_valid_commands(void)
{
    L1Command command;

    CHECK(l1_protocol_parse_command(
              "V1,COMMAND,101,START,EQ-WIND-01,OP20,EVR-LOT-001,100\n",
              &command) == L1_PROTOCOL_OK);
    CHECK(command.command_id == 101);
    CHECK(command.type == L1_COMMAND_START);
    CHECK(strcmp(command.machine_id, "EQ-WIND-01") == 0);
    CHECK(strcmp(command.process_code, "OP20") == 0);
    CHECK(strcmp(command.lot_no, "EVR-LOT-001") == 0);
    CHECK(command.input_qty == 100);

    CHECK(l1_protocol_parse_command(
              "V1,COMMAND,102,STOP,EQ-WIND-01,OP20,EVR-LOT-001,0\n",
              &command) == L1_PROTOCOL_OK);
    CHECK(command.type == L1_COMMAND_STOP);
    CHECK(command.input_qty == 0);

    CHECK(l1_protocol_parse_command(
              "V1,COMMAND,103,RESUME,EQ-WIND-01,OP20,EVR-LOT-001,100\n",
              &command) == L1_PROTOCOL_OK);
    CHECK(command.type == L1_COMMAND_RESUME);
}

static void test_invalid_commands(void)
{
    char oversized[L1_PROTOCOL_MAX_MESSAGE_SIZE + 2];
    char invalid_utf8[] = {
        'V', '1', ',', 'C', 'O', 'M', 'M', 'A', 'N', 'D', ',',
        '1', ',', 'S', 'T', 'A', 'R', 'T', ',',
        'E', 'Q', '-', 'W', 'I', 'N', 'D', '-', '0', '1', ',',
        'O', 'P', '2', '0', ',', (char)0xC3, (char)0x28, ',',
        '1', '\n', '\0'
    };

    expect_command_result("", L1_PROTOCOL_EMPTY_MESSAGE);
    expect_command_result(
        "V1,COMMAND,101,START,EQ-WIND-01,OP20,EVR-LOT-001,100",
        L1_PROTOCOL_MISSING_LINE_FEED);
    expect_command_result(
        "V1,COMMAND,101,START,EQ-WIND-01,OP20,EVR-LOT-001,100\r\n",
        L1_PROTOCOL_INVALID_LINE_ENDING);
    expect_command_result(
        "V2,COMMAND,101,START,EQ-WIND-01,OP20,EVR-LOT-001,100\n",
        L1_PROTOCOL_UNSUPPORTED_VERSION);
    expect_command_result(
        "V1,HEARTBEAT,EQ-WIND-01\n",
        L1_PROTOCOL_FIELD_COUNT_MISMATCH);
    expect_command_result(
        "V1,COMMAND,101,PAUSE,EQ-WIND-01,OP20,EVR-LOT-001,100\n",
        L1_PROTOCOL_INVALID_VALUE);
    expect_command_result(
        "V1,COMMAND,0,START,EQ-WIND-01,OP20,EVR-LOT-001,100\n",
        L1_PROTOCOL_OUT_OF_RANGE);
    expect_command_result(
        "V1,COMMAND,abc,START,EQ-WIND-01,OP20,EVR-LOT-001,100\n",
        L1_PROTOCOL_INVALID_NUMBER);
    expect_command_result(
        "V1,COMMAND,101,START,EQ-NOT-REAL,OP20,EVR-LOT-001,100\n",
        L1_PROTOCOL_UNKNOWN_MACHINE);
    expect_command_result(
        "V1,COMMAND,101,START,EQ-WIND-01,OP30,EVR-LOT-001,100\n",
        L1_PROTOCOL_PROCESS_MISMATCH);
    expect_command_result(
        "V1,COMMAND,101,START,EQ-WIND-01,OP20,-,100\n",
        L1_PROTOCOL_INVALID_VALUE);
    expect_command_result(
        "V1,COMMAND,101,START,EQ-WIND-01,OP20,EVR-LOT-001,0\n",
        L1_PROTOCOL_OUT_OF_RANGE);
    expect_command_result(
        "V1,COMMAND,101,STOP,EQ-WIND-01,OP20,EVR-LOT-001,1\n",
        L1_PROTOCOL_OUT_OF_RANGE);
    expect_command_result(
        "V1,COMMAND,101,START, EQ-WIND-01,OP20,EVR-LOT-001,100\n",
        L1_PROTOCOL_INVALID_FIELD_FORMAT);
    expect_command_result(
        "V1,COMMAND,101,START,,OP20,EVR-LOT-001,100\n",
        L1_PROTOCOL_INVALID_FIELD_FORMAT);
    expect_command_result(invalid_utf8, L1_PROTOCOL_INVALID_UTF8);

    memset(oversized, 'A', sizeof(oversized));
    oversized[sizeof(oversized) - 1] = '\0';
    expect_command_result(oversized, L1_PROTOCOL_MESSAGE_TOO_LONG);
}

int main(void)
{
    test_device_config();
    test_connection_messages();
    test_production_message();
    test_inspection_message();
    test_defect_alarm_and_status_messages();
    test_command_ack_message();
    test_valid_commands();
    test_invalid_commands();

    if (tests_failed == 0) {
        printf("PASS: %d L1 protocol checks\n", tests_run);
        return 0;
    }
    fprintf(stderr,
            "FAILED: %d of %d L1 protocol checks\n",
            tests_failed,
            tests_run);
    return 1;
}
