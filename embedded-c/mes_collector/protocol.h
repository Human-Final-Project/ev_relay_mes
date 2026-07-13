#ifndef MES_COLLECTOR_PROTOCOL_H
#define MES_COLLECTOR_PROTOCOL_H

typedef enum {
    PROTOCOL_EVENT_UNKNOWN = 0,
    PROTOCOL_EVENT_HELLO,
    PROTOCOL_EVENT_HEARTBEAT,
    PROTOCOL_EVENT_PRODUCTION,
    PROTOCOL_EVENT_INSPECTION,
    PROTOCOL_EVENT_DEFECT,
    PROTOCOL_EVENT_ALARM,
    PROTOCOL_EVENT_MACHINE_STATUS,
    PROTOCOL_EVENT_COMMAND,
    PROTOCOL_EVENT_COMMAND_ACK
} ProtocolEventType;

/* Returns a stable name for logging. Parsing is implemented in stage 3. */
const char *protocol_event_type_name(ProtocolEventType type);

#endif
