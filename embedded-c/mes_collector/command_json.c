#include "command_json.h"

#include <ctype.h>
#include <limits.h>
#include <stdint.h>
#include <string.h>

#include "config.h"

#define COMMAND_JSON_KEY_CAPACITY 64
#define COMMAND_JSON_STATUS_CAPACITY 32
#define COMMAND_JSON_MAX_DEPTH 16

typedef struct {
    const char *json;
    size_t length;
    size_t position;
} JsonCursor;

static void skip_whitespace(JsonCursor *cursor)
{
    while (cursor->position < cursor->length
           && isspace((unsigned char)cursor->json[cursor->position])) {
        ++cursor->position;
    }
}

static int consume(JsonCursor *cursor, char expected)
{
    skip_whitespace(cursor);
    if (cursor->position >= cursor->length
        || cursor->json[cursor->position] != expected) {
        return 0;
    }
    ++cursor->position;
    return 1;
}

static int hex_digit(unsigned char byte, unsigned int *digit)
{
    if (byte >= '0' && byte <= '9') {
        *digit = byte - '0';
        return 1;
    }
    if (byte >= 'a' && byte <= 'f') {
        *digit = byte - 'a' + 10U;
        return 1;
    }
    if (byte >= 'A' && byte <= 'F') {
        *digit = byte - 'A' + 10U;
        return 1;
    }
    return 0;
}

static CommandJsonResult append_decoded_byte(char *output,
                                             size_t output_capacity,
                                             size_t *written,
                                             unsigned char byte)
{
    if (output != NULL) {
        if (*written + 1U >= output_capacity) {
            return COMMAND_JSON_FIELD_TOO_LONG;
        }
        output[*written] = (char)byte;
    }
    ++*written;
    return COMMAND_JSON_OK;
}

static CommandJsonResult append_code_point(char *output,
                                           size_t output_capacity,
                                           size_t *written,
                                           unsigned int code_point)
{
    CommandJsonResult result;

    if (code_point <= 0x7FU) {
        return append_decoded_byte(output,
                                   output_capacity,
                                   written,
                                   (unsigned char)code_point);
    }
    if (code_point <= 0x7FFU) {
        result = append_decoded_byte(output,
                                     output_capacity,
                                     written,
                                     (unsigned char)(0xC0U
                                         | (code_point >> 6)));
        if (result != COMMAND_JSON_OK) {
            return result;
        }
        return append_decoded_byte(output,
                                   output_capacity,
                                   written,
                                   (unsigned char)(0x80U
                                       | (code_point & 0x3FU)));
    }
    if (code_point >= 0xD800U && code_point <= 0xDFFFU) {
        return COMMAND_JSON_INVALID_JSON;
    }
    result = append_decoded_byte(output,
                                 output_capacity,
                                 written,
                                 (unsigned char)(0xE0U
                                     | (code_point >> 12)));
    if (result != COMMAND_JSON_OK) {
        return result;
    }
    result = append_decoded_byte(output,
                                 output_capacity,
                                 written,
                                 (unsigned char)(0x80U
                                     | ((code_point >> 6) & 0x3FU)));
    if (result != COMMAND_JSON_OK) {
        return result;
    }
    return append_decoded_byte(output,
                               output_capacity,
                               written,
                               (unsigned char)(0x80U
                                   | (code_point & 0x3FU)));
}

static CommandJsonResult parse_string(JsonCursor *cursor,
                                      char *output,
                                      size_t output_capacity)
{
    size_t written = 0;

    skip_whitespace(cursor);
    if (cursor->position >= cursor->length
        || cursor->json[cursor->position] != '"') {
        return COMMAND_JSON_INVALID_JSON;
    }
    ++cursor->position;
    while (cursor->position < cursor->length) {
        unsigned char byte = (unsigned char)cursor->json[cursor->position++];
        CommandJsonResult result;

        if (byte == '"') {
            if (output != NULL) {
                if (written >= output_capacity) {
                    return COMMAND_JSON_FIELD_TOO_LONG;
                }
                output[written] = '\0';
            }
            return COMMAND_JSON_OK;
        }
        if (byte < 0x20U) {
            return COMMAND_JSON_INVALID_JSON;
        }
        if (byte != '\\') {
            result = append_decoded_byte(output,
                                         output_capacity,
                                         &written,
                                         byte);
        } else {
            unsigned char escaped;

            if (cursor->position >= cursor->length) {
                return COMMAND_JSON_INVALID_JSON;
            }
            escaped = (unsigned char)cursor->json[cursor->position++];
            switch (escaped) {
            case '"':
            case '\\':
            case '/':
                result = append_decoded_byte(output,
                                             output_capacity,
                                             &written,
                                             escaped);
                break;
            case 'b':
                result = append_decoded_byte(output,
                                             output_capacity,
                                             &written,
                                             '\b');
                break;
            case 'f':
                result = append_decoded_byte(output,
                                             output_capacity,
                                             &written,
                                             '\f');
                break;
            case 'n':
                result = append_decoded_byte(output,
                                             output_capacity,
                                             &written,
                                             '\n');
                break;
            case 'r':
                result = append_decoded_byte(output,
                                             output_capacity,
                                             &written,
                                             '\r');
                break;
            case 't':
                result = append_decoded_byte(output,
                                             output_capacity,
                                             &written,
                                             '\t');
                break;
            case 'u': {
                unsigned int code_point = 0;
                size_t index;

                if (cursor->length - cursor->position < 4U) {
                    return COMMAND_JSON_INVALID_JSON;
                }
                for (index = 0; index < 4U; ++index) {
                    unsigned int digit;

                    if (!hex_digit((unsigned char)cursor->json[
                                       cursor->position++],
                                   &digit)) {
                        return COMMAND_JSON_INVALID_JSON;
                    }
                    code_point = code_point * 16U + digit;
                }
                result = append_code_point(output,
                                           output_capacity,
                                           &written,
                                           code_point);
                break;
            }
            default:
                return COMMAND_JSON_INVALID_JSON;
            }
        }
        if (result != COMMAND_JSON_OK) {
            return result;
        }
    }
    return COMMAND_JSON_INVALID_JSON;
}

static CommandJsonResult skip_value(JsonCursor *cursor, unsigned int depth);

static CommandJsonResult skip_object(JsonCursor *cursor, unsigned int depth)
{
    if (depth > COMMAND_JSON_MAX_DEPTH || !consume(cursor, '{')) {
        return COMMAND_JSON_INVALID_JSON;
    }
    skip_whitespace(cursor);
    if (consume(cursor, '}')) {
        return COMMAND_JSON_OK;
    }
    for (;;) {
        CommandJsonResult result = parse_string(cursor, NULL, 0);

        if (result != COMMAND_JSON_OK || !consume(cursor, ':')) {
            return result != COMMAND_JSON_OK
                ? result
                : COMMAND_JSON_INVALID_JSON;
        }
        result = skip_value(cursor, depth + 1U);
        if (result != COMMAND_JSON_OK) {
            return result;
        }
        skip_whitespace(cursor);
        if (consume(cursor, '}')) {
            return COMMAND_JSON_OK;
        }
        if (!consume(cursor, ',')) {
            return COMMAND_JSON_INVALID_JSON;
        }
    }
}

static CommandJsonResult skip_array(JsonCursor *cursor, unsigned int depth)
{
    if (depth > COMMAND_JSON_MAX_DEPTH || !consume(cursor, '[')) {
        return COMMAND_JSON_INVALID_JSON;
    }
    skip_whitespace(cursor);
    if (consume(cursor, ']')) {
        return COMMAND_JSON_OK;
    }
    for (;;) {
        CommandJsonResult result = skip_value(cursor, depth + 1U);

        if (result != COMMAND_JSON_OK) {
            return result;
        }
        skip_whitespace(cursor);
        if (consume(cursor, ']')) {
            return COMMAND_JSON_OK;
        }
        if (!consume(cursor, ',')) {
            return COMMAND_JSON_INVALID_JSON;
        }
    }
}

static CommandJsonResult skip_value(JsonCursor *cursor, unsigned int depth)
{
    size_t start;

    if (depth > COMMAND_JSON_MAX_DEPTH) {
        return COMMAND_JSON_INVALID_JSON;
    }
    skip_whitespace(cursor);
    if (cursor->position >= cursor->length) {
        return COMMAND_JSON_INVALID_JSON;
    }
    if (cursor->json[cursor->position] == '"') {
        return parse_string(cursor, NULL, 0);
    }
    if (cursor->json[cursor->position] == '{') {
        return skip_object(cursor, depth + 1U);
    }
    if (cursor->json[cursor->position] == '[') {
        return skip_array(cursor, depth + 1U);
    }
    start = cursor->position;
    while (cursor->position < cursor->length) {
        char byte = cursor->json[cursor->position];

        if (byte == ',' || byte == '}' || byte == ']'
            || isspace((unsigned char)byte)) {
            break;
        }
        ++cursor->position;
    }
    return cursor->position > start
        ? COMMAND_JSON_OK
        : COMMAND_JSON_INVALID_JSON;
}

static CommandJsonResult parse_int64(JsonCursor *cursor, int64_t *value)
{
    int negative = 0;
    uint64_t magnitude = 0;
    int digit_count = 0;

    skip_whitespace(cursor);
    if (cursor->position < cursor->length
        && cursor->json[cursor->position] == '-') {
        negative = 1;
        ++cursor->position;
    }
    while (cursor->position < cursor->length
           && isdigit((unsigned char)cursor->json[cursor->position])) {
        unsigned int digit = (unsigned int)(cursor->json[cursor->position]
            - '0');
        uint64_t maximum = negative
            ? (uint64_t)INT64_MAX + 1U
            : (uint64_t)INT64_MAX;

        if (magnitude > (maximum - digit) / 10U) {
            return COMMAND_JSON_INVALID_COMMAND;
        }
        magnitude = magnitude * 10U + digit;
        ++cursor->position;
        ++digit_count;
    }
    if (digit_count == 0) {
        return COMMAND_JSON_INVALID_JSON;
    }
    if (cursor->position < cursor->length
        && cursor->json[cursor->position] != ','
        && cursor->json[cursor->position] != '}'
        && !isspace((unsigned char)cursor->json[cursor->position])) {
        return COMMAND_JSON_INVALID_JSON;
    }
    if (negative) {
        *value = magnitude == (uint64_t)INT64_MAX + 1U
            ? INT64_MIN
            : -(int64_t)magnitude;
    } else {
        *value = (int64_t)magnitude;
    }
    return COMMAND_JSON_OK;
}

static CommandJsonResult parse_command_object(JsonCursor *cursor,
                                              ProtocolCommand *command)
{
    enum {
        FIELD_COMMAND_ID = 1U << 0,
        FIELD_COMMAND_TYPE = 1U << 1,
        FIELD_MACHINE_ID = 1U << 2,
        FIELD_PROCESS_CODE = 1U << 3,
        FIELD_LOT_NO = 1U << 4,
        FIELD_INPUT_QTY = 1U << 5,
        FIELD_STATUS = 1U << 6,
        REQUIRED_FIELDS = (1U << 7) - 1U
    };
    unsigned int fields = 0;
    char command_type[COMMAND_JSON_STATUS_CAPACITY] = {0};
    char status[COMMAND_JSON_STATUS_CAPACITY] = {0};

    memset(command, 0, sizeof(*command));
    if (!consume(cursor, '{')) {
        return COMMAND_JSON_INVALID_JSON;
    }
    skip_whitespace(cursor);
    if (consume(cursor, '}')) {
        return COMMAND_JSON_INVALID_COMMAND;
    }
    for (;;) {
        char key[COMMAND_JSON_KEY_CAPACITY];
        unsigned int field = 0;
        CommandJsonResult result = parse_string(cursor, key, sizeof(key));

        if (result != COMMAND_JSON_OK || !consume(cursor, ':')) {
            return result != COMMAND_JSON_OK
                ? result
                : COMMAND_JSON_INVALID_JSON;
        }
        if (strcmp(key, "commandId") == 0) {
            field = FIELD_COMMAND_ID;
            result = parse_int64(cursor, &command->command_id);
        } else if (strcmp(key, "commandType") == 0) {
            field = FIELD_COMMAND_TYPE;
            result = parse_string(cursor,
                                  command_type,
                                  sizeof(command_type));
        } else if (strcmp(key, "machineId") == 0) {
            field = FIELD_MACHINE_ID;
            result = parse_string(cursor,
                                  command->machine_id,
                                  sizeof(command->machine_id));
        } else if (strcmp(key, "processCode") == 0) {
            field = FIELD_PROCESS_CODE;
            result = parse_string(cursor,
                                  command->process_code,
                                  sizeof(command->process_code));
        } else if (strcmp(key, "lotNo") == 0) {
            field = FIELD_LOT_NO;
            result = parse_string(cursor,
                                  command->lot_no,
                                  sizeof(command->lot_no));
        } else if (strcmp(key, "inputQty") == 0) {
            int64_t input_qty = 0;

            field = FIELD_INPUT_QTY;
            result = parse_int64(cursor, &input_qty);
            if (result == COMMAND_JSON_OK) {
                if (input_qty < INT_MIN || input_qty > INT_MAX) {
                    result = COMMAND_JSON_INVALID_COMMAND;
                } else {
                    command->input_qty = (int)input_qty;
                }
            }
        } else if (strcmp(key, "status") == 0) {
            field = FIELD_STATUS;
            result = parse_string(cursor, status, sizeof(status));
        } else {
            result = skip_value(cursor, 0);
        }
        if (result != COMMAND_JSON_OK) {
            return result;
        }
        if (field != 0) {
            if ((fields & field) != 0) {
                return COMMAND_JSON_INVALID_JSON;
            }
            fields |= field;
        }
        skip_whitespace(cursor);
        if (consume(cursor, '}')) {
            break;
        }
        if (!consume(cursor, ',')) {
            return COMMAND_JSON_INVALID_JSON;
        }
    }
    if (fields != REQUIRED_FIELDS || strcmp(status, "DISPATCHED") != 0) {
        return COMMAND_JSON_INVALID_COMMAND;
    }
    if (strcmp(command_type, "START") == 0) {
        command->type = PROTOCOL_COMMAND_START;
    } else if (strcmp(command_type, "STOP") == 0) {
        command->type = PROTOCOL_COMMAND_STOP;
    } else if (strcmp(command_type, "RESUME") == 0) {
        command->type = PROTOCOL_COMMAND_RESUME;
    } else {
        return COMMAND_JSON_INVALID_COMMAND;
    }
    {
        char wire[COLLECTOR_MAX_MESSAGE_SIZE + 1];
        size_t wire_length = 0;

        if (protocol_build_command(wire,
                                   sizeof(wire),
                                   command,
                                   &wire_length) != PROTOCOL_RESULT_OK) {
            return COMMAND_JSON_INVALID_COMMAND;
        }
    }
    return COMMAND_JSON_OK;
}

CommandJsonResult command_json_parse_pending(
    const char *json,
    size_t json_length,
    ProtocolCommand *commands,
    size_t command_capacity,
    size_t *command_count)
{
    JsonCursor cursor;
    size_t count = 0;

    if (json == NULL || commands == NULL || command_capacity == 0
        || command_count == NULL) {
        return COMMAND_JSON_INVALID_ARGUMENT;
    }
    *command_count = 0;
    cursor.json = json;
    cursor.length = json_length;
    cursor.position = 0;
    if (!consume(&cursor, '[')) {
        return COMMAND_JSON_INVALID_JSON;
    }
    skip_whitespace(&cursor);
    if (consume(&cursor, ']')) {
        skip_whitespace(&cursor);
        return cursor.position == cursor.length
            ? COMMAND_JSON_OK
            : COMMAND_JSON_INVALID_JSON;
    }
    for (;;) {
        CommandJsonResult result;

        if (count >= command_capacity) {
            return COMMAND_JSON_TOO_MANY_COMMANDS;
        }
        result = parse_command_object(&cursor, &commands[count]);
        if (result != COMMAND_JSON_OK) {
            return result;
        }
        ++count;
        skip_whitespace(&cursor);
        if (consume(&cursor, ']')) {
            break;
        }
        if (!consume(&cursor, ',')) {
            return COMMAND_JSON_INVALID_JSON;
        }
    }
    skip_whitespace(&cursor);
    if (cursor.position != cursor.length) {
        return COMMAND_JSON_INVALID_JSON;
    }
    *command_count = count;
    return COMMAND_JSON_OK;
}

const char *command_json_result_name(CommandJsonResult result)
{
    switch (result) {
    case COMMAND_JSON_OK:
        return "OK";
    case COMMAND_JSON_INVALID_ARGUMENT:
        return "INVALID_ARGUMENT";
    case COMMAND_JSON_INVALID_JSON:
        return "INVALID_JSON";
    case COMMAND_JSON_FIELD_TOO_LONG:
        return "FIELD_TOO_LONG";
    case COMMAND_JSON_TOO_MANY_COMMANDS:
        return "TOO_MANY_COMMANDS";
    case COMMAND_JSON_INVALID_COMMAND:
        return "INVALID_COMMAND";
    default:
        return "UNKNOWN";
    }
}
