#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "command_json.h"

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

static CommandJsonResult parse(const char *json,
                               ProtocolCommand *commands,
                               size_t capacity,
                               size_t *count)
{
    return command_json_parse_pending(json,
                                      strlen(json),
                                      commands,
                                      capacity,
                                      count);
}

static void test_empty_array(void)
{
    ProtocolCommand commands[1];
    size_t count = 99;

    CHECK(parse(" [ ] \n", commands, 1, &count) == COMMAND_JSON_OK);
    CHECK(count == 0);
}

static void test_backend_response_with_unknown_fields(void)
{
    const char *json =
        "[{"
        "\"commandId\":101,"
        "\"commandType\":\"START\","
        "\"machineId\":\"EQ-WIND-01\","
        "\"processCode\":\"OP20\","
        "\"lotNo\":\"EVR-LOT-001\","
        "\"inputQty\":100,"
        "\"status\":\"DISPATCHED\","
        "\"ackMessage\":null,"
        "\"createdAt\":\"2026-07-20T09:00:00\","
        "\"extra\":{\"nested\":[true,2,null]}"
        "}]";
    ProtocolCommand commands[2];
    size_t count = 0;

    CHECK(parse(json, commands, 2, &count) == COMMAND_JSON_OK);
    CHECK(count == 1);
    CHECK(commands[0].command_id == 101);
    CHECK(commands[0].type == PROTOCOL_COMMAND_START);
    CHECK(strcmp(commands[0].machine_id, "EQ-WIND-01") == 0);
    CHECK(strcmp(commands[0].process_code, "OP20") == 0);
    CHECK(strcmp(commands[0].lot_no, "EVR-LOT-001") == 0);
    CHECK(commands[0].input_qty == 100);
}

static void test_multiple_command_types(void)
{
    const char *json =
        "["
        "{\"status\":\"DISPATCHED\",\"inputQty\":0,"
        "\"lotNo\":\"EVR-LOT-001\",\"processCode\":\"OP30\","
        "\"machineId\":\"EQ-WELD-01\",\"commandType\":\"STOP\","
        "\"commandId\":102},"
        "{\"commandId\":103,\"commandType\":\"RESUME\","
        "\"machineId\":\"EQ-ASSY-01\",\"processCode\":\"OP40_OP50\","
        "\"lotNo\":\"EVR-LOT-001\",\"inputQty\":60,"
        "\"status\":\"DISPATCHED\"}"
        "]";
    ProtocolCommand commands[2];
    size_t count = 0;

    CHECK(parse(json, commands, 2, &count) == COMMAND_JSON_OK);
    CHECK(count == 2);
    CHECK(commands[0].type == PROTOCOL_COMMAND_STOP);
    CHECK(commands[0].input_qty == 0);
    CHECK(commands[1].type == PROTOCOL_COMMAND_RESUME);
    CHECK(commands[1].input_qty == 60);
}

static void test_invalid_contract_values(void)
{
    const char *missing_status =
        "[{\"commandId\":1,\"commandType\":\"START\","
        "\"machineId\":\"EQ-WIND-01\",\"processCode\":\"OP20\","
        "\"lotNo\":\"EVR-LOT-001\",\"inputQty\":10}]";
    const char *pending_status =
        "[{\"commandId\":1,\"commandType\":\"START\","
        "\"machineId\":\"EQ-WIND-01\",\"processCode\":\"OP20\","
        "\"lotNo\":\"EVR-LOT-001\",\"inputQty\":10,"
        "\"status\":\"PENDING\"}]";
    const char *mismatch =
        "[{\"commandId\":1,\"commandType\":\"START\","
        "\"machineId\":\"EQ-WIND-01\",\"processCode\":\"OP30\","
        "\"lotNo\":\"EVR-LOT-001\",\"inputQty\":10,"
        "\"status\":\"DISPATCHED\"}]";
    const char *bad_stop_quantity =
        "[{\"commandId\":1,\"commandType\":\"STOP\","
        "\"machineId\":\"EQ-WIND-01\",\"processCode\":\"OP20\","
        "\"lotNo\":\"EVR-LOT-001\",\"inputQty\":1,"
        "\"status\":\"DISPATCHED\"}]";
    ProtocolCommand commands[1];
    size_t count = 0;

    CHECK(parse(missing_status, commands, 1, &count)
          == COMMAND_JSON_INVALID_COMMAND);
    CHECK(parse(pending_status, commands, 1, &count)
          == COMMAND_JSON_INVALID_COMMAND);
    CHECK(parse(mismatch, commands, 1, &count)
          == COMMAND_JSON_INVALID_COMMAND);
    CHECK(parse(bad_stop_quantity, commands, 1, &count)
          == COMMAND_JSON_INVALID_COMMAND);
}

static void test_malformed_and_capacity_errors(void)
{
    const char *valid =
        "[{\"commandId\":1,\"commandType\":\"START\","
        "\"machineId\":\"EQ-WIND-01\",\"processCode\":\"OP20\","
        "\"lotNo\":\"EVR-LOT-001\",\"inputQty\":10,"
        "\"status\":\"DISPATCHED\"}]";
    const char *two =
        "[{\"commandId\":1,\"commandType\":\"START\","
        "\"machineId\":\"EQ-WIND-01\",\"processCode\":\"OP20\","
        "\"lotNo\":\"EVR-LOT-001\",\"inputQty\":10,"
        "\"status\":\"DISPATCHED\"},"
        "{\"commandId\":2,\"commandType\":\"START\","
        "\"machineId\":\"EQ-WELD-01\",\"processCode\":\"OP30\","
        "\"lotNo\":\"EVR-LOT-001\",\"inputQty\":10,"
        "\"status\":\"DISPATCHED\"}]";
    ProtocolCommand commands[1];
    size_t count = 0;

    CHECK(parse("not-json", commands, 1, &count)
          == COMMAND_JSON_INVALID_JSON);
    CHECK(parse("[{]", commands, 1, &count)
          == COMMAND_JSON_INVALID_JSON);
    CHECK(parse(two, commands, 1, &count)
          == COMMAND_JSON_TOO_MANY_COMMANDS);
    CHECK(command_json_parse_pending(valid,
                                     strlen(valid),
                                     NULL,
                                     1,
                                     &count)
          == COMMAND_JSON_INVALID_ARGUMENT);
}

int main(void)
{
    test_empty_array();
    test_backend_response_with_unknown_fields();
    test_multiple_command_types();
    test_invalid_contract_values();
    test_malformed_and_capacity_errors();

    if (checks_failed != 0) {
        fprintf(stderr,
                "%d of %d command JSON checks failed.\n",
                checks_failed,
                checks_run);
        return EXIT_FAILURE;
    }
    printf("PASS: %d command JSON checks\n", checks_run);
    return EXIT_SUCCESS;
}
