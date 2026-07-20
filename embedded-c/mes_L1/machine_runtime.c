#include "machine_runtime.h"

#include <string.h>

#define OP70_PROCESS_CODE "OP70"

static void actions_init(L1RuntimeActions *actions)
{
    memset(actions, 0, sizeof(*actions));
}

static L1RuntimeAction *append_action(L1RuntimeActions *actions,
                                      L1RuntimeActionType type)
{
    L1RuntimeAction *action;

    if (actions->count >= L1_RUNTIME_MAX_ACTIONS) {
        return NULL;
    }
    action = &actions->actions[actions->count++];
    memset(action, 0, sizeof(*action));
    action->type = type;
    return action;
}

static int is_inspection_runtime(const L1MachineRuntime *runtime)
{
    return runtime != NULL && runtime->device != NULL
        && strcmp(runtime->device->process_code, OP70_PROCESS_CODE) == 0;
}

static int append_ack(L1RuntimeActions *actions,
                      const L1MachineRuntime *runtime,
                      const L1Command *command,
                      L1CommandAckStatus status,
                      const char *message)
{
    L1RuntimeAction *action = append_action(actions, L1_RUNTIME_ACTION_COMMAND_ACK);
    if (action == NULL) return -1;
    strcpy(action->data.command_ack.machine_id, runtime->device->machine_id);
    action->data.command_ack.command_id = command->command_id;
    action->data.command_ack.status = status;
    strcpy(action->data.command_ack.message, message);
    return 0;
}

static int append_status(L1RuntimeActions *actions,
                         const L1MachineRuntime *runtime,
                         L1MachineStatus status,
                         const char *lot_no,
                         const char *message)
{
    L1RuntimeAction *action = append_action(actions, L1_RUNTIME_ACTION_MACHINE_STATUS);
    if (action == NULL) return -1;
    strcpy(action->data.machine_status.machine_id, runtime->device->machine_id);
    action->data.machine_status.status = status;
    strcpy(action->data.machine_status.lot_no, lot_no);
    strcpy(action->data.machine_status.process_code, runtime->device->process_code);
    strcpy(action->data.machine_status.message, message);
    return 0;
}

static int append_unreported_production(L1RuntimeActions *actions,
                                        const L1MachineRuntime *runtime,
                                        L1ProductionStatus status)
{
    int quantity;
    L1RuntimeAction *action;

    if (is_inspection_runtime(runtime)) return 0;
    quantity = l1_machine_runtime_unreported_qty(runtime);
    if (quantity <= 0) return 0;
    action = append_action(actions, L1_RUNTIME_ACTION_PRODUCTION);
    if (action == NULL) return -1;
    strcpy(action->data.production.machine_id, runtime->device->machine_id);
    strcpy(action->data.production.process_code, runtime->device->process_code);
    strcpy(action->data.production.lot_no, runtime->lot_no);
    action->data.production.input_qty = quantity;
    action->data.production.ok_qty = quantity;
    action->data.production.ng_qty = 0;
    action->data.production.status = status;
    return 0;
}

static double inspection_value(int unit_seq, int item_index)
{
    switch (item_index) {
    case 0:
        return 9.5 + (double)((unit_seq * 7) % 51) / 10.0;
    case 1:
        return 75.0 + (double)((unit_seq * 11) % 51);
    default:
        return 30.0 + (double)((unit_seq * 13) % 31);
    }
}

static int append_inspection(L1RuntimeActions *actions,
                             const L1MachineRuntime *runtime,
                             int unit_seq,
                             const char *item,
                             const char *unit,
                             double value,
                             int completes_unit)
{
    L1RuntimeAction *action = append_action(actions, L1_RUNTIME_ACTION_INSPECTION);
    if (action == NULL) return -1;
    action->completes_unit = completes_unit;
    strcpy(action->data.inspection.machine_id, runtime->device->machine_id);
    strcpy(action->data.inspection.process_code, runtime->device->process_code);
    strcpy(action->data.inspection.lot_no, runtime->lot_no);
    action->data.inspection.unit_seq = unit_seq;
    strcpy(action->data.inspection.item, item);
    action->data.inspection.value = value;
    strcpy(action->data.inspection.unit, unit);
    return 0;
}

static int append_inspection_measurements(L1RuntimeActions *actions,
                                          const L1MachineRuntime *runtime,
                                          int seq)
{
    return append_inspection(actions, runtime, seq,
                             "OPERATION_VOLTAGE", "V",
                             inspection_value(seq, 0), 0) != 0
        || append_inspection(actions, runtime, seq,
                             "COIL_RESISTANCE", "OHM",
                             inspection_value(seq, 1), 0) != 0
        || append_inspection(actions, runtime, seq,
                             "CONTACT_RESISTANCE", "mOHM",
                             inspection_value(seq, 2), 1) != 0
        ? -1 : 0;
}

static int append_error_alarm(L1RuntimeActions *actions,
                              const L1MachineRuntime *runtime)
{
    L1RuntimeAction *action = append_action(actions, L1_RUNTIME_ACTION_ALARM);
    if (action == NULL) return -1;
    strcpy(action->data.alarm.machine_id, runtime->device->machine_id);
    strcpy(action->data.alarm.alarm_code, runtime->device->error_alarm_code);
    action->data.alarm.alarm_level = L1_ALARM_ERROR;
    strcpy(action->data.alarm.message, runtime->device->error_message);
    return 0;
}

void l1_machine_runtime_init(L1MachineRuntime *runtime,
                             const L1DeviceConfig *device,
                             int error_after_qty)
{
    if (runtime == NULL) return;
    memset(runtime, 0, sizeof(*runtime));
    runtime->device = device;
    runtime->state = L1_RUNTIME_IDLE;
    runtime->error_after_qty = error_after_qty > 0 ? error_after_qty : 0;
}

static int handle_start(L1MachineRuntime *runtime,
                        const L1Command *command,
                        L1RuntimeActions *actions)
{
    if (runtime->state != L1_RUNTIME_IDLE) {
        return append_ack(actions, runtime, command, L1_ACK_REJECTED, "machine_not_idle");
    }
    strcpy(runtime->lot_no, command->lot_no);
    runtime->target_qty = command->input_qty;
    runtime->processed_qty = 0;
    runtime->reported_qty = 0;
    runtime->error_triggered = 0;
    runtime->state = L1_RUNTIME_RUNNING;
    if (append_ack(actions, runtime, command, L1_ACK_ACCEPTED, "command_received") != 0) return -1;
    return append_status(actions, runtime, L1_MACHINE_RUNNING,
                         runtime->lot_no,
                         is_inspection_runtime(runtime) ? "inspection_started" : "production_started");
}

static int handle_resume(L1MachineRuntime *runtime,
                         const L1Command *command,
                         L1RuntimeActions *actions)
{
    int remaining = l1_machine_runtime_remaining_qty(runtime);
    if (runtime->state != L1_RUNTIME_ERROR_PAUSED && runtime->state != L1_RUNTIME_STOPPED) {
        return append_ack(actions, runtime, command, L1_ACK_REJECTED, "machine_not_paused");
    }
    if (strcmp(command->lot_no, runtime->lot_no) != 0) {
        return append_ack(actions, runtime, command, L1_ACK_REJECTED, "resume_lot_mismatch");
    }
    if (remaining <= 0 || command->input_qty != remaining) {
        return append_ack(actions, runtime, command, L1_ACK_REJECTED, "resume_quantity_mismatch");
    }
    runtime->state = L1_RUNTIME_RUNNING;
    if (append_ack(actions, runtime, command, L1_ACK_ACCEPTED, "command_received") != 0) return -1;
    return append_status(actions, runtime, L1_MACHINE_RUNNING,
                         runtime->lot_no,
                         is_inspection_runtime(runtime) ? "inspection_resumed" : "production_resumed");
}

static int handle_stop(L1MachineRuntime *runtime,
                       const L1Command *command,
                       L1RuntimeActions *actions)
{
    if (runtime->state != L1_RUNTIME_RUNNING) {
        return append_ack(actions, runtime, command, L1_ACK_REJECTED,
                          runtime->state == L1_RUNTIME_ERROR_PAUSED
                              ? "resume_required" : "machine_not_running");
    }
    runtime->state = L1_RUNTIME_STOPPED;
    if (append_ack(actions, runtime, command, L1_ACK_ACCEPTED, "command_received") != 0
        || append_unreported_production(actions, runtime, L1_PRODUCTION_RUNNING) != 0) return -1;
    return append_status(actions, runtime, L1_MACHINE_STOPPED,
                         runtime->lot_no,
                         is_inspection_runtime(runtime) ? "inspection_stopped" : "production_stopped");
}

int l1_machine_runtime_handle_command(L1MachineRuntime *runtime,
                                      const L1Command *command,
                                      L1RuntimeActions *out_actions)
{
    int result;

    if (runtime == NULL || runtime->device == NULL || command == NULL || out_actions == NULL) return -1;
    actions_init(out_actions);
    if (runtime->last_command_id == command->command_id) {
        return append_ack(out_actions,
                          runtime,
                          command,
                          runtime->last_ack_status,
                          runtime->last_ack_message);
    }
    switch (command->type) {
    case L1_COMMAND_START:
        result = handle_start(runtime, command, out_actions);
        break;
    case L1_COMMAND_STOP:
        result = handle_stop(runtime, command, out_actions);
        break;
    case L1_COMMAND_RESUME:
        result = handle_resume(runtime, command, out_actions);
        break;
    default:
        return -1;
    }
    if (result == 0 && out_actions->count > 0
        && out_actions->actions[0].type == L1_RUNTIME_ACTION_COMMAND_ACK) {
        runtime->last_command_id = command->command_id;
        runtime->last_ack_status = out_actions->actions[0].data.command_ack.status;
        strcpy(runtime->last_ack_message,
               out_actions->actions[0].data.command_ack.message);
    }
    return result;
}

int l1_machine_runtime_tick(L1MachineRuntime *runtime,
                            L1RuntimeActions *out_actions)
{
    int inspection_runtime;
    if (runtime == NULL || runtime->device == NULL || out_actions == NULL) return -1;
    actions_init(out_actions);
    if (runtime->state != L1_RUNTIME_RUNNING) return 0;
    inspection_runtime = is_inspection_runtime(runtime);
    if (inspection_runtime && l1_machine_runtime_unreported_qty(runtime) > 0) {
        return append_inspection_measurements(
                out_actions, runtime, runtime->reported_qty + 1);
    }
    if (runtime->processed_qty >= runtime->target_qty) return -1;

    ++runtime->processed_qty;
    if (inspection_runtime
        && append_inspection_measurements(
                out_actions, runtime, runtime->processed_qty) != 0) return -1;

    if (!runtime->error_triggered
        && runtime->error_after_qty > 0
        && runtime->processed_qty >= runtime->error_after_qty
        && runtime->processed_qty < runtime->target_qty) {
        runtime->error_triggered = 1;
        runtime->state = L1_RUNTIME_ERROR_PAUSED;
        if ((!inspection_runtime
             && append_unreported_production(out_actions, runtime, L1_PRODUCTION_RUNNING) != 0)
            || append_error_alarm(out_actions, runtime) != 0
            || append_status(out_actions, runtime, L1_MACHINE_ERROR,
                             runtime->lot_no,
                             inspection_runtime ? "inspection_paused_by_error" : "production_paused_by_error") != 0) {
            return -1;
        }
        return 0;
    }

    if (runtime->processed_qty == runtime->target_qty) {
        runtime->state = L1_RUNTIME_IDLE;
        if ((!inspection_runtime
             && append_unreported_production(out_actions, runtime, L1_PRODUCTION_COMPLETED) != 0)
            || append_status(out_actions, runtime, L1_MACHINE_IDLE, "-",
                             inspection_runtime ? "inspection_finished" : "production_finished") != 0) {
            return -1;
        }
    }
    return 0;
}

int l1_machine_runtime_mark_reported(L1MachineRuntime *runtime, int quantity)
{
    if (runtime == NULL || quantity < 0
        || quantity > l1_machine_runtime_unreported_qty(runtime)) return -1;
    runtime->reported_qty += quantity;
    return 0;
}

int l1_machine_runtime_remaining_qty(const L1MachineRuntime *runtime)
{
    if (runtime == NULL || runtime->target_qty < runtime->processed_qty) return 0;
    return runtime->target_qty - runtime->processed_qty;
}

int l1_machine_runtime_unreported_qty(const L1MachineRuntime *runtime)
{
    if (runtime == NULL || runtime->processed_qty < runtime->reported_qty) return 0;
    return runtime->processed_qty - runtime->reported_qty;
}

const char *l1_machine_runtime_state_name(L1RuntimeState state)
{
    switch (state) {
    case L1_RUNTIME_IDLE: return "IDLE";
    case L1_RUNTIME_RUNNING: return "RUNNING";
    case L1_RUNTIME_ERROR_PAUSED: return "ERROR_PAUSED";
    case L1_RUNTIME_STOPPED: return "STOPPED";
    default: return "UNKNOWN";
    }
}
