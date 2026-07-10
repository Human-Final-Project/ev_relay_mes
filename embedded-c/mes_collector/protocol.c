#include "protocol.h"

const char *protocol_event_type_name(ProtocolEventType type)
{
    switch (type) {
    case PROTOCOL_EVENT_HELLO:
        return "HELLO";
    case PROTOCOL_EVENT_HEARTBEAT:
        return "HEARTBEAT";
    case PROTOCOL_EVENT_PRODUCTION:
        return "PRODUCTION";
    case PROTOCOL_EVENT_INSPECTION:
        return "INSPECTION";
    case PROTOCOL_EVENT_DEFECT:
        return "DEFECT";
    case PROTOCOL_EVENT_ALARM:
        return "ALARM";
    case PROTOCOL_EVENT_MACHINE_STATUS:
        return "MACHINE_STATUS";
    case PROTOCOL_EVENT_UNKNOWN:
    default:
        return "UNKNOWN";
    }
}
