#include "command_json.h"

#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

static const char *skip_whitespace(const char *cursor)
{
    while (cursor != NULL
           && *cursor != '\0'
           && isspace((unsigned char)*cursor)) {
        ++cursor;
    }
    return cursor;
}

static const char *skip_string(const char *cursor)
{
    if (cursor == NULL || *cursor != '"') {
        return NULL;
    }

    ++cursor;
    while (*cursor != '\0') {
        if (*cursor == '\\') {
            if (cursor[1] == '\0') {
                return NULL;
            }
            cursor += 2;
            continue;
        }
        if (*cursor == '"') {
            return cursor + 1;
        }
        ++cursor;
    }
    return NULL;
}

static const char *skip_compound(const char *cursor,
                                 char open_character,
                                 char close_character)
{
    int depth = 0;
    int in_string = 0;
    int escaped = 0;

    while (cursor != NULL && *cursor != '\0') {
        char current = *cursor++;

        if (in_string) {
            if (escaped) {
                escaped = 0;
            } else if (current == '\\') {
                escaped = 1;
            } else if (current == '"') {
                in_string = 0;
            }
            continue;
        }

        if (current == '"') {
            in_string = 1;
        } else if (current == open_character) {
            ++depth;
        } else if (current == close_character) {
            --depth;
            if (depth == 0) {
                return cursor;
            }
        }
    }
    return NULL;
}

static const char *skip_value(const char *cursor)
{
    cursor = skip_whitespace(cursor);
    if (cursor == NULL || *cursor == '\0') {
        return NULL;
    }
    if (*cursor == '"') {
        return skip_string(cursor);
    }
    if (*cursor == '{') {
        return skip_compound(cursor, '{', '}');
    }
    if (*cursor == '[') {
        return skip_compound(cursor, '[', ']');
    }

    while (*cursor != '\0' && *cursor != ',' && *cursor != '}') {
        ++cursor;
    }
    return cursor;
}

static int read_string(const char *cursor,
                       char *output,
                       size_t output_capacity,
                       const char **after_value)
{
    size_t length = 0;

    cursor = skip_whitespace(cursor);
    if (cursor == NULL
        || *cursor != '"'
        || output == NULL
        || output_capacity == 0) {
        return -1;
    }

    ++cursor;
    while (*cursor != '\0' && *cursor != '"') {
        unsigned char value = (unsigned char)*cursor++;

        if (value == '\\') {
            value = (unsigned char)*cursor++;
            if (value == '\0') {
                return -1;
            }
            switch (value) {
            case '"':
            case '\\':
            case '/':
                break;
            case 'b':
                value = '\b';
                break;
            case 'f':
                value = '\f';
                break;
            case 'n':
                value = '\n';
                break;
            case 'r':
                value = '\r';
                break;
            case 't':
                value = '\t';
                break;
            default:
                return -1;
            }
        }

        if (length + 1 >= output_capacity) {
            return -1;
        }
        output[length++] = (char)value;
    }

    if (*cursor != '"') {
        return -1;
    }
    output[length] = '\0';
    if (after_value != NULL) {
        *after_value = cursor + 1;
    }
    return 0;
}

static const char *find_field_value(const char *object,
                                    const char *field_name)
{
    const char *cursor;

    if (object == NULL || field_name == NULL) {
        return NULL;
    }
    cursor = skip_whitespace(object);
    if (*cursor != '{') {
        return NULL;
    }

    ++cursor;
    for (;;) {
        char key[128];
        const char *after_key;
        const char *after_value;

        cursor = skip_whitespace(cursor);
        if (*cursor == '}') {
            return NULL;
        }
        if (read_string(cursor, key, sizeof(key), &after_key) != 0) {
            return NULL;
        }

        cursor = skip_whitespace(after_key);
        if (*cursor != ':') {
            return NULL;
        }
        cursor = skip_whitespace(cursor + 1);
        if (strcmp(key, field_name) == 0) {
            return cursor;
        }

        after_value = skip_value(cursor);
        if (after_value == NULL) {
            return NULL;
        }
        cursor = skip_whitespace(after_value);
        if (*cursor == ',') {
            ++cursor;
            continue;
        }
        if (*cursor == '}') {
            return NULL;
        }
        return NULL;
    }
}

static int get_required_string(const char *object,
                               const char *field_name,
                               char *output,
                               size_t output_capacity)
{
    const char *value = find_field_value(object, field_name);

    if (value == NULL) {
        return -1;
    }
    return read_string(value, output, output_capacity, NULL);
}

static int get_required_int64(const char *object,
                              const char *field_name,
                              int64_t *output)
{
    const char *value = find_field_value(object, field_name);
    char *end;
    long long parsed;

    if (value == NULL || output == NULL) {
        return -1;
    }

    errno = 0;
    parsed = strtoll(value, &end, 10);
    if (errno != 0 || end == value) {
        return -1;
    }
    end = (char *)skip_whitespace(end);
    if (*end != ',' && *end != '}') {
        return -1;
    }
    *output = (int64_t)parsed;
    return 0;
}

static int get_required_int(const char *object,
                            const char *field_name,
                            int *output)
{
    int64_t parsed;

    if (get_required_int64(object, field_name, &parsed) != 0
        || parsed < INT_MIN
        || parsed > INT_MAX) {
        return -1;
    }
    *output = (int)parsed;
    return 0;
}

static ProtocolCommandType command_type_from_text(const char *value)
{
    if (strcmp(value, "START") == 0) {
        return PROTOCOL_COMMAND_START;
    }
    if (strcmp(value, "STOP") == 0) {
        return PROTOCOL_COMMAND_STOP;
    }
    if (strcmp(value, "RESUME") == 0) {
        return PROTOCOL_COMMAND_RESUME;
    }
    return PROTOCOL_COMMAND_UNKNOWN;
}

static ApiClientResult parse_command_object(const char *object,
                                            ProtocolCommand *command)
{
    char command_type[32];
    char status[32];

    memset(command, 0, sizeof(*command));
    if (get_required_int64(object,
                           "commandId",
                           &command->command_id) != 0
        || get_required_string(object,
                               "commandType",
                               command_type,
                               sizeof(command_type)) != 0
        || get_required_string(object,
                               "machineId",
                               command->machine_id,
                               sizeof(command->machine_id)) != 0
        || get_required_string(object,
                               "processCode",
                               command->process_code,
                               sizeof(command->process_code)) != 0
        || get_required_string(object,
                               "lotNo",
                               command->lot_no,
                               sizeof(command->lot_no)) != 0
        || get_required_int(object, "inputQty", &command->input_qty) != 0
        || get_required_string(object,
                               "status",
                               status,
                               sizeof(status)) != 0) {
        return API_CLIENT_INVALID_RESPONSE;
    }

    command->type = command_type_from_text(command_type);
    if (command->command_id <= 0
        || command->type == PROTOCOL_COMMAND_UNKNOWN
        || strcmp(status, "DISPATCHED") != 0
        || !protocol_machine_matches_process(command->machine_id,
                                             command->process_code)
        || command->lot_no[0] == '\0') {
        return API_CLIENT_INVALID_RESPONSE;
    }
    if (command->type == PROTOCOL_COMMAND_STOP) {
        return command->input_qty == 0
            ? API_CLIENT_OK
            : API_CLIENT_INVALID_RESPONSE;
    }
    return command->input_qty > 0
        ? API_CLIENT_OK
        : API_CLIENT_INVALID_RESPONSE;
}

ApiClientResult command_json_parse_response(
    const char *json,
    ProtocolCommand *commands,
    size_t command_capacity,
    size_t *command_count)
{
    const char *cursor;
    size_t count = 0;

    if (command_count != NULL) {
        *command_count = 0;
    }
    if (json == NULL
        || commands == NULL
        || command_capacity == 0
        || command_count == NULL) {
        return API_CLIENT_INVALID_ARGUMENT;
    }

    cursor = skip_whitespace(json);
    if (*cursor != '[') {
        return API_CLIENT_INVALID_RESPONSE;
    }

    ++cursor;
    for (;;) {
        const char *object_end;
        size_t object_length;
        char object[API_CLIENT_JSON_CAPACITY];
        ApiClientResult parse_result;

        cursor = skip_whitespace(cursor);
        if (*cursor == ']') {
            cursor = skip_whitespace(cursor + 1);
            if (*cursor != '\0') {
                return API_CLIENT_INVALID_RESPONSE;
            }
            *command_count = count;
            return API_CLIENT_OK;
        }
        if (*cursor != '{') {
            return API_CLIENT_INVALID_RESPONSE;
        }
        if (count >= command_capacity) {
            return API_CLIENT_BUFFER_TOO_SMALL;
        }

        object_end = skip_compound(cursor, '{', '}');
        if (object_end == NULL) {
            return API_CLIENT_INVALID_RESPONSE;
        }
        object_length = (size_t)(object_end - cursor);
        if (object_length + 1 > sizeof(object)) {
            return API_CLIENT_BUFFER_TOO_SMALL;
        }

        memcpy(object, cursor, object_length);
        object[object_length] = '\0';
        parse_result = parse_command_object(object, &commands[count]);
        if (parse_result != API_CLIENT_OK) {
            return parse_result;
        }

        ++count;
        cursor = skip_whitespace(object_end);
        if (*cursor == ',') {
            ++cursor;
            continue;
        }
        if (*cursor != ']') {
            return API_CLIENT_INVALID_RESPONSE;
        }
    }
}
