#ifndef MES_COLLECTOR_COMMAND_JSON_H
#define MES_COLLECTOR_COMMAND_JSON_H

#include <stddef.h>

#include "api_client.h"

/*
 * Backend가 내려준 작업 명령 JSON 배열을 C 구조체로 변환한다.
 * HTTP 통신은 api_client.c가 담당하고, 이 파일은 JSON 해석만 담당한다.
 */
ApiClientResult command_json_parse_response(
    const char *json,
    ProtocolCommand *commands,
    size_t command_capacity,
    size_t *command_count);

#endif
