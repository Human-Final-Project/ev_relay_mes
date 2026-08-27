#ifndef EV_RELAY_L1_MACHINE_RUNTIME_H
#define EV_RELAY_L1_MACHINE_RUNTIME_H

#include <stddef.h>

#include "device_config.h"
#include "protocol.h"

#define L1_RUNTIME_MAX_ACTIONS 8

typedef enum {
    L1_RUNTIME_IDLE = 0,
    L1_RUNTIME_RUNNING,
    L1_RUNTIME_ERROR_PAUSED,
    L1_RUNTIME_STOPPED
} L1RuntimeState;

typedef enum {
    L1_ALARM_INJECTION_NONE = 0,
    L1_ALARM_INJECTION_FIXED,
    L1_ALARM_INJECTION_RANDOM
} L1AlarmInjectionMode;

typedef struct {
    L1AlarmInjectionMode mode;
    int trigger_after_qty;
    int probability_percent;
    const char *alarm_code;
} L1AlarmInjectionConfig;

typedef enum {
    L1_RUNTIME_ACTION_COMMAND_ACK = 0,
    L1_RUNTIME_ACTION_PRODUCTION,
    L1_RUNTIME_ACTION_INSPECTION,
    L1_RUNTIME_ACTION_JUDGMENT,
    L1_RUNTIME_ACTION_ALARM,
    L1_RUNTIME_ACTION_MACHINE_STATUS
} L1RuntimeActionType;

typedef struct {
    L1RuntimeActionType type;
    int completes_unit;
    int reported_quantity;
    union {
        L1CommandAckEvent command_ack;
        L1ProductionEvent production;
        L1InspectionEvent inspection;
        L1JudgmentEvent judgment;
        L1AlarmEvent alarm;
        L1MachineStatusEvent machine_status;
    } data;
} L1RuntimeAction;

typedef struct {
    L1RuntimeAction actions[L1_RUNTIME_MAX_ACTIONS];
    size_t count;
} L1RuntimeActions;

typedef struct {
    const L1DeviceConfig *device;
    L1RuntimeState state;
    char lot_no[L1_LOT_NO_CAPACITY];
    int target_qty;
    int processed_qty;
    int reported_qty;
    L1AlarmInjectionConfig alarm_injection;
    const L1AlarmScenario *selected_alarm;
    int alarm_after_qty;
    int alarm_scheduled;
    int alarm_triggered;
    int64_t last_command_id;
    L1CommandAckStatus last_ack_status;
    char last_ack_message[L1_MESSAGE_CAPACITY];
} L1MachineRuntime;

/* Legacy helper: fixed default ERROR alarm after the requested quantity. */
void l1_machine_runtime_init(L1MachineRuntime *runtime,
                             const L1DeviceConfig *device,
                             int error_after_qty);
void l1_machine_runtime_init_with_alarm(
    L1MachineRuntime *runtime,
    const L1DeviceConfig *device,
    const L1AlarmInjectionConfig *alarm_injection);
int l1_machine_runtime_handle_command(L1MachineRuntime *runtime,
                                      const L1Command *command,
                                      L1RuntimeActions *out_actions);
int l1_machine_runtime_tick(L1MachineRuntime *runtime,
                            L1RuntimeActions *out_actions);
int l1_machine_runtime_mark_reported(L1MachineRuntime *runtime,
                                     int quantity);
int l1_machine_runtime_remaining_qty(const L1MachineRuntime *runtime);
int l1_machine_runtime_unreported_qty(const L1MachineRuntime *runtime);
const char *l1_machine_runtime_state_name(L1RuntimeState state);

#endif
