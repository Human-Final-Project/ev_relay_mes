#include <stdio.h>
#include <string.h>

#include "config.h"
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

static void expect_parse_result(const char *line, ProtocolResult expected)
{
    ProtocolMessage message;
    ProtocolResult actual = protocol_parse_message(line, &message);

    CHECK(actual == expected);
    if (actual != expected) {
        fprintf(stderr,
                "  expected=%s actual=%s line=%s\n",
                protocol_result_name(expected),
                protocol_result_name(actual),
                line);
    }
}

static void test_connection_events(void)
{
    ProtocolMessage message;

    CHECK(protocol_parse_message("V1,HELLO,EQ-WIND-01\n", &message)
          == PROTOCOL_RESULT_OK);
    CHECK(message.type == PROTOCOL_EVENT_HELLO);
    CHECK(strcmp(message.data.connection.machine_id, "EQ-WIND-01") == 0);

    CHECK(protocol_parse_message("V1,HEARTBEAT,EQ-PACK-01\n", &message)
          == PROTOCOL_RESULT_OK);
    CHECK(message.type == PROTOCOL_EVENT_HEARTBEAT);
    CHECK(strcmp(message.data.connection.machine_id, "EQ-PACK-01") == 0);
}

static void test_production(void)
{
    ProtocolMessage message;
    const ProtocolProductionEvent *event;

    CHECK(protocol_parse_message(
              "V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-001,100,97,3,COMPLETED\n",
              &message)
          == PROTOCOL_RESULT_OK);
    CHECK(message.type == PROTOCOL_EVENT_PRODUCTION);
    event = &message.data.production;
    CHECK(strcmp(event->machine_id, "EQ-WIND-01") == 0);
    CHECK(strcmp(event->process_code, "OP20") == 0);
    CHECK(strcmp(event->lot_no, "EVR-LOT-001") == 0);
    CHECK(event->input_qty == 100);
    CHECK(event->ok_qty == 97);
    CHECK(event->ng_qty == 3);
    CHECK(strcmp(event->status, "COMPLETED") == 0);
}

static void test_inspection(void)
{
    ProtocolMessage message;
    const ProtocolInspectionEvent *event;

    CHECK(protocol_parse_message(
              "V1,INSPECTION,EQ-TEST-01,OP70,EVR-LOT-001,operationVoltage,12.000,V,10.000,14.000,OK\n",
              &message)
          == PROTOCOL_RESULT_OK);
    CHECK(message.type == PROTOCOL_EVENT_INSPECTION);
    event = &message.data.inspection;
    CHECK(strcmp(event->item, "operationVoltage") == 0);
    CHECK(event->value == 12.0);
    CHECK(event->has_lower_limit == 1);
    CHECK(event->lower_limit == 10.0);
    CHECK(event->has_upper_limit == 1);
    CHECK(event->upper_limit == 14.0);
    CHECK(strcmp(event->result, "OK") == 0);

    CHECK(protocol_parse_message(
              "V1,INSPECTION,EQ-TEST-01,OP70,EVR-LOT-001,visualCheck,1,EA,-,-,NG\n",
              &message)
          == PROTOCOL_RESULT_OK);
    CHECK(message.data.inspection.has_lower_limit == 0);
    CHECK(message.data.inspection.has_upper_limit == 0);
}

static void test_error_and_status_events(void)
{
    ProtocolMessage message;

    CHECK(protocol_parse_message(
              "V1,DEFECT,EQ-WELD-01,OP30,EVR-LOT-001,WELD_STRENGTH_NG,3,weld_strength_ng\n",
              &message)
          == PROTOCOL_RESULT_OK);
    CHECK(message.type == PROTOCOL_EVENT_DEFECT);
    CHECK(message.data.defect.defect_qty == 3);
    CHECK(strcmp(message.data.defect.defect_code, "WELD_STRENGTH_NG") == 0);

    CHECK(protocol_parse_message(
              "V1,ALARM,EQ-WIND-01,MOTOR_OVERLOAD,ERROR,motor_overload\n",
              &message)
          == PROTOCOL_RESULT_OK);
    CHECK(message.type == PROTOCOL_EVENT_ALARM);
    CHECK(strcmp(message.data.alarm.alarm_level, "ERROR") == 0);

    CHECK(protocol_parse_message(
              "V1,MACHINE_STATUS,EQ-WIND-01,IDLE,-,OP20,production_finished\n",
              &message)
          == PROTOCOL_RESULT_OK);
    CHECK(message.type == PROTOCOL_EVENT_MACHINE_STATUS);
    CHECK(strcmp(message.data.machine_status.lot_no, "-") == 0);
}

static void test_command_ack(void)
{
    ProtocolMessage message;

    CHECK(protocol_parse_message(
              "V1,COMMAND_ACK,EQ-WIND-01,101,ACCEPTED,-\n",
              &message)
          == PROTOCOL_RESULT_OK);
    CHECK(message.type == PROTOCOL_EVENT_COMMAND_ACK);
    CHECK(message.data.command_ack.command_id == 101);
    CHECK(strcmp(message.data.command_ack.ack_status, "ACCEPTED") == 0);

    CHECK(protocol_parse_message(
              "V1,COMMAND_ACK,EQ-WIND-01,102,REJECTED,invalid_state\n",
              &message)
          == PROTOCOL_RESULT_OK);
    CHECK(strcmp(message.data.command_ack.message, "invalid_state") == 0);
}

static void test_invalid_messages(void)
{
    char oversized[COLLECTOR_MAX_MESSAGE_SIZE + 2];
    char invalid_utf8[] = {
        'V', '1', ',', 'A', 'L', 'A', 'R', 'M', ',',
        'E', 'Q', '-', 'W', 'I', 'N', 'D', '-', '0', '1', ',',
        'M', 'O', 'T', 'O', 'R', '_', 'O', 'V', 'E', 'R', 'L', 'O', 'A', 'D', ',',
        'E', 'R', 'R', 'O', 'R', ',', (char)0xC3, (char)0x28, '\n', '\0'
    };

    expect_parse_result("", PROTOCOL_RESULT_EMPTY_MESSAGE);
    expect_parse_result("V1,HELLO,EQ-WIND-01",
                        PROTOCOL_RESULT_MISSING_LINE_FEED);
    expect_parse_result("V1,HELLO,EQ-WIND-01\r\n",
                        PROTOCOL_RESULT_INVALID_LINE_ENDING);
    expect_parse_result("V2,HELLO,EQ-WIND-01\n",
                        PROTOCOL_RESULT_UNSUPPORTED_VERSION);
    expect_parse_result("V1,NOT_REAL,EQ-WIND-01\n",
                        PROTOCOL_RESULT_UNKNOWN_EVENT);
    expect_parse_result("V1,HELLO,EQ-WIND-01,EXTRA\n",
                        PROTOCOL_RESULT_FIELD_COUNT_MISMATCH);
    expect_parse_result("V1,HELLO,\n",
                        PROTOCOL_RESULT_INVALID_FIELD_FORMAT);
    expect_parse_result("V1,HELLO, EQ-WIND-01\n",
                        PROTOCOL_RESULT_INVALID_FIELD_FORMAT);
    expect_parse_result("V1,HELLO,EQ-NOT-REAL\n",
                        PROTOCOL_RESULT_UNKNOWN_MACHINE);
    expect_parse_result(
        "V1,PRODUCTION,EQ-WIND-01,OP30,EVR-LOT-001,100,97,3,COMPLETED\n",
        PROTOCOL_RESULT_PROCESS_MISMATCH);
    expect_parse_result(
        "V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-001,100,96,3,COMPLETED\n",
        PROTOCOL_RESULT_INVALID_VALUE);
    expect_parse_result(
        "V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-001,-1,0,0,COMPLETED\n",
        PROTOCOL_RESULT_OUT_OF_RANGE);
    expect_parse_result(
        "V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-001,1x,1,0,COMPLETED\n",
        PROTOCOL_RESULT_INVALID_NUMBER);
    expect_parse_result(
        "V1,INSPECTION,EQ-TEST-01,OP70,EVR-LOT-001,item,abc,V,10,14,OK\n",
        PROTOCOL_RESULT_INVALID_NUMBER);
    expect_parse_result(
        "V1,INSPECTION,EQ-TEST-01,OP70,EVR-LOT-001,item,12,V,14,10,OK\n",
        PROTOCOL_RESULT_INVALID_VALUE);
    expect_parse_result(
        "V1,DEFECT,EQ-WELD-01,OP30,EVR-LOT-001,WELD_STRENGTH_NG,0,-\n",
        PROTOCOL_RESULT_OUT_OF_RANGE);
    expect_parse_result(
        "V1,ALARM,EQ-WIND-01,COMM_DISCONNECTED,ERROR,-\n",
        PROTOCOL_RESULT_UNEXPECTED_EVENT);
    expect_parse_result(
        "V1,ALARM,EQ-WIND-01,COMM_TIMEOUT,ERROR,-\n",
        PROTOCOL_RESULT_UNEXPECTED_EVENT);
    expect_parse_result(
        "V1,MACHINE_STATUS,EQ-WIND-01,PAUSED,-,OP20,-\n",
        PROTOCOL_RESULT_INVALID_VALUE);
    expect_parse_result(
        "V1,COMMAND_ACK,EQ-WIND-01,0,ACCEPTED,-\n",
        PROTOCOL_RESULT_OUT_OF_RANGE);
    expect_parse_result(
        "V1,COMMAND,101,START,EQ-WIND-01,OP20,EVR-LOT-001,100\n",
        PROTOCOL_RESULT_UNEXPECTED_EVENT);
    expect_parse_result(invalid_utf8, PROTOCOL_RESULT_INVALID_UTF8);

    memset(oversized, 'A', sizeof(oversized));
    oversized[sizeof(oversized) - 1] = '\0';
    expect_parse_result(oversized, PROTOCOL_RESULT_MESSAGE_TOO_LONG);
}

static void test_command_builder(void)
{
    char output[COLLECTOR_MAX_MESSAGE_SIZE + 1];
    char small_output[16];
    size_t length = 0;
    ProtocolCommand command = {
        101,
        PROTOCOL_COMMAND_START,
        "EQ-WIND-01",
        "OP20",
        "EVR-LOT-001",
        100
    };

    CHECK(protocol_build_command(output, sizeof(output), &command, &length)
          == PROTOCOL_RESULT_OK);
    CHECK(strcmp(output,
                 "V1,COMMAND,101,START,EQ-WIND-01,OP20,EVR-LOT-001,100\n")
          == 0);
    CHECK(length == strlen(output));

    command.command_id = 102;
    command.type = PROTOCOL_COMMAND_STOP;
    command.input_qty = 0;
    CHECK(protocol_build_command(output, sizeof(output), &command, &length)
          == PROTOCOL_RESULT_OK);
    CHECK(strcmp(output,
                 "V1,COMMAND,102,STOP,EQ-WIND-01,OP20,EVR-LOT-001,0\n")
          == 0);

    command.command_id = 103;
    command.type = PROTOCOL_COMMAND_RESUME;
    command.input_qty = 100;
    CHECK(protocol_build_command(output, sizeof(output), &command, &length)
          == PROTOCOL_RESULT_OK);

    command.type = PROTOCOL_COMMAND_START;
    command.input_qty = 0;
    CHECK(protocol_build_command(output, sizeof(output), &command, &length)
          == PROTOCOL_RESULT_OUT_OF_RANGE);

    command.type = PROTOCOL_COMMAND_STOP;
    command.input_qty = 1;
    CHECK(protocol_build_command(output, sizeof(output), &command, &length)
          == PROTOCOL_RESULT_OUT_OF_RANGE);

    command.input_qty = 0;
    strcpy(command.process_code, "OP30");
    CHECK(protocol_build_command(output, sizeof(output), &command, &length)
          == PROTOCOL_RESULT_PROCESS_MISMATCH);

    strcpy(command.process_code, "OP20");
    CHECK(protocol_build_command(small_output,
                                 sizeof(small_output),
                                 &command,
                                 &length)
          == PROTOCOL_RESULT_BUFFER_TOO_SMALL);
    CHECK(small_output[0] == '\0');
}

int main(void)
{
    test_connection_events();
    test_production();
    test_inspection();
    test_error_and_status_events();
    test_command_ack();
    test_invalid_messages();
    test_command_builder();

    if (tests_failed == 0) {
        printf("PASS: %d protocol checks\n", tests_run);
        return 0;
    }

    fprintf(stderr,
            "FAILED: %d of %d protocol checks\n",
            tests_failed,
            tests_run);
    return 1;
}
