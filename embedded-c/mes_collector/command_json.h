#ifndef MES_COLLECTOR_COMMAND_JSON_H
#define MES_COLLECTOR_COMMAND_JSON_H

#include <stddef.h>

#include "protocol.h"

typedef enum {
    COMMAND_JSON_OK = 0,
    COMMAND_JSON_INVALID_ARGUMENT,
    COMMAND_JSON_INVALID_JSON,
    COMMAND_JSON_FIELD_TOO_LONG,
    COMMAND_JSON_TOO_MANY_COMMANDS,
    COMMAND_JSON_INVALID_COMMAND
} CommandJsonResult;

/* Parses the Backend pending-command JSON array into validated commands. */
CommandJsonResult command_json_parse_pending(
    const char *json,
    size_t json_length,
    ProtocolCommand *commands,
    size_t command_capacity,
    size_t *command_count);

const char *command_json_result_name(CommandJsonResult result);

#endif
