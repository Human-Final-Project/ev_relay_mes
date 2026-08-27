#include "machine_runtime.h"

#include <string.h>

#include "config.h"

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
    return runtime != NULL && runtime->device != NULL;
}

static unsigned int text_seed(const char *text)
{
    unsigned int seed = 0U;

    while (text != NULL && *text != '\0') {
        seed = (seed * 31U + (unsigned char)*text) % 100U;
        ++text;
    }
    return seed;
}

static unsigned int text_hash(const char *text)
{
    unsigned int hash = 2166136261U;

    while (text != NULL && *text != '\0') {
        hash ^= (unsigned char)*text;
        hash *= 16777619U;
        ++text;
    }
    return hash;
}

static unsigned int job_alarm_seed(const L1MachineRuntime *runtime)
{
    unsigned int seed = text_hash(runtime->lot_no);

    seed ^= text_hash(runtime->device->machine_id) * 31U;
    seed ^= text_hash(runtime->device->process_code) * 131U;
    return seed;
}

static void schedule_alarm_for_job(L1MachineRuntime *runtime)
{
    const L1AlarmInjectionConfig *config = &runtime->alarm_injection;
    unsigned int seed;
    int trigger_after;

    runtime->selected_alarm = NULL;
    runtime->alarm_after_qty = 0;
    runtime->alarm_scheduled = 0;
    runtime->alarm_triggered = 0;

    if (runtime->target_qty <= 1 || config->mode == L1_ALARM_INJECTION_NONE) {
        return;
    }

    seed = job_alarm_seed(runtime);
    if (config->mode == L1_ALARM_INJECTION_RANDOM) {
        int rate = config->probability_percent;

        if (rate < 0) rate = 0;
        if (rate > 100) rate = 100;
        if ((int)(seed % 100U) >= rate || runtime->device->alarm_count == 0) {
            return;
        }
        runtime->selected_alarm = l1_device_alarm_at(
            runtime->device,
            (seed / 101U) % runtime->device->alarm_count);
    } else {
        runtime->selected_alarm = config->alarm_code != NULL
            ? l1_device_alarm_find(runtime->device, config->alarm_code)
            : l1_device_default_error_alarm(runtime->device);
    }

    if (runtime->selected_alarm == NULL) {
        return;
    }
    trigger_after = config->trigger_after_qty;
    if (trigger_after <= 0) {
        trigger_after = 1 + (int)((seed / 7919U)
            % (unsigned int)(runtime->target_qty - 1));
    }
    if (trigger_after >= runtime->target_qty) {
        return;
    }
    runtime->alarm_after_qty = trigger_after;
    runtime->alarm_scheduled = 1;
}

static int unit_is_ng(const L1MachineRuntime *runtime, int unit_seq)
{
    unsigned int seed = text_seed(runtime->lot_no)
        + text_seed(runtime->device->machine_id);
    unsigned int score = ((unsigned int)unit_seq * 37U + seed) % 100U;

    return score < L1_NG_RATE_PERCENT;
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
    int unit_seq;
    int ng_qty = 0;
    L1RuntimeAction *action;

    if (is_inspection_runtime(runtime)) return 0;
    quantity = l1_machine_runtime_unreported_qty(runtime);
    if (quantity <= 0) return 0;
    action = append_action(actions, L1_RUNTIME_ACTION_PRODUCTION);
    if (action == NULL) return -1;
    strcpy(action->data.production.machine_id, runtime->device->machine_id);
    strcpy(action->data.production.process_code, runtime->device->process_code);
    strcpy(action->data.production.lot_no, runtime->lot_no);
    for (unit_seq = 1;
         unit_seq <= runtime->processed_qty;
         ++unit_seq) {
        if (unit_is_ng(runtime, unit_seq)) {
            ++ng_qty;
        }
    }
    action->reported_quantity = quantity;
    action->data.production.input_qty = runtime->processed_qty;
    action->data.production.ok_qty = runtime->processed_qty - ng_qty;
    action->data.production.ng_qty = ng_qty;
    action->data.production.status = status;
    return 0;
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

static int measurement_is_ng(const L1MachineRuntime *runtime, int seq)
{
    const char *process = runtime->device->process_code;
    if (!unit_is_ng(runtime, seq)) return 0;
    if (strcmp(process, "OP20") == 0 || strcmp(process, "OP60") == 0) {
        return seq % 2 == 0;
    }
    return strcmp(process, "OP30") == 0 || strcmp(process, "OP70") == 0;
}

static const char *categorical_defect_code(const L1MachineRuntime *runtime,
                                           int seq)
{
    const char *process = runtime->device->process_code;
    if (!unit_is_ng(runtime, seq) || measurement_is_ng(runtime, seq)) return NULL;
    if (strcmp(process, "OP20") == 0) return seq % 3 == 0 ? "COIL_SHORT_NG" : "COIL_OPEN_NG";
    if (strcmp(process, "OP40_OP50") == 0) {
        switch (seq % 3) {
        case 0: return "ASSY_MISALIGN_NG";
        case 1: return "SPRING_MISSING_NG";
        default: return "CHAMBER_CRACK_NG";
        }
    }
    if (strcmp(process, "OP60") == 0) return "SEAL_WELD_NG";
    if (strcmp(process, "OP80") == 0) return seq % 2 == 0 ? "MARKING_NG" : "PACKING_COUNT_NG";
    return NULL;
}

static int append_judgment(L1RuntimeActions *actions,
                           const L1MachineRuntime *runtime,
                           int seq)
{
    const char *defect_code = categorical_defect_code(runtime, seq);
    L1RuntimeAction *action = append_action(actions, L1_RUNTIME_ACTION_JUDGMENT);
    if (action == NULL) return -1;
    strcpy(action->data.judgment.machine_id, runtime->device->machine_id);
    strcpy(action->data.judgment.process_code, runtime->device->process_code);
    strcpy(action->data.judgment.lot_no, runtime->lot_no);
    action->data.judgment.unit_seq = seq;
    action->data.judgment.result = defect_code == NULL ? L1_JUDGMENT_OK : L1_JUDGMENT_NG;
    strcpy(action->data.judgment.defect_code, defect_code == NULL ? "-" : defect_code);
    strcpy(action->data.judgment.message,
           defect_code == NULL ? "automatic_judgment_ok" : "automatic_judgment_ng");
    return 0;
}

static int append_process_measurements(L1RuntimeActions *actions,
                                       const L1MachineRuntime *runtime,
                                       int seq)
{
    const char *process = runtime->device->process_code;
    int ng = measurement_is_ng(runtime, seq);
    if (strcmp(process, "OP20") == 0) {
        return append_inspection(actions, runtime, seq, "COIL_RESISTANCE", "OHM",
                                 ng ? 130.0 : 90.0 + (seq % 21), 0);
    }
    if (strcmp(process, "OP30") == 0) {
        return append_inspection(actions, runtime, seq, "WELD_STRENGTH", "N",
                                 ng ? 30.0 : 50.0 + (seq % 21), 0) != 0
            || append_inspection(actions, runtime, seq, "CONTACT_RESISTANCE", "mOHM",
                                 20.0 + (seq % 21), 0) != 0
            || append_inspection(actions, runtime, seq, "CONTACT_POSITION", "MM",
                                 0.05 + (double)(seq % 10) / 100.0, 0) != 0 ? -1 : 0;
    }
    if (strcmp(process, "OP60") == 0) {
        return append_inspection(actions, runtime, seq, "GAS_PRESSURE", "BAR",
                                 ng ? 4.0 : 2.8 + (double)(seq % 5) / 10.0, 0) != 0
            || append_inspection(actions, runtime, seq, "LEAK_RATE", "SCCM",
                                 0.1 + (double)(seq % 3) / 10.0, 0) != 0 ? -1 : 0;
    }
    if (strcmp(process, OP70_PROCESS_CODE) == 0) {
        return append_inspection(actions, runtime, seq, "INSULATION_RESISTANCE", "MOHM",
                                 ng ? 50.0 : 200.0 + (seq % 101), 0) != 0
            || append_inspection(actions, runtime, seq, "WITHSTAND_VOLTAGE", "V",
                                 1600.0 + (seq % 101), 0) != 0
            || append_inspection(actions, runtime, seq, "OPERATION_VOLTAGE", "V",
                                 11.0 + (double)(seq % 21) / 10.0, 0) != 0
            || append_inspection(actions, runtime, seq, "CONTACT_BOUNCE", "MS",
                                 1.0 + (double)(seq % 21) / 10.0, 0) != 0 ? -1 : 0;
    }
    return 0;
}

static int append_unit_events(L1RuntimeActions *actions,
                              const L1MachineRuntime *runtime,
                              int seq)
{
    if (append_judgment(actions, runtime, seq) != 0
        || append_process_measurements(actions, runtime, seq) != 0
        || actions->count == 0) return -1;
    actions->actions[actions->count - 1].completes_unit = 1;
    return 0;
}

static int append_selected_alarm(L1RuntimeActions *actions,
                                 const L1MachineRuntime *runtime)
{
    L1RuntimeAction *action;

    if (runtime->selected_alarm == NULL) return -1;
    action = append_action(actions, L1_RUNTIME_ACTION_ALARM);
    if (action == NULL) return -1;
    strcpy(action->data.alarm.machine_id, runtime->device->machine_id);
    strcpy(action->data.alarm.alarm_code,
           runtime->selected_alarm->alarm_code);
    action->data.alarm.alarm_level =
        runtime->selected_alarm->level == L1_DEVICE_ALARM_WARNING
            ? L1_ALARM_WARNING
            : L1_ALARM_ERROR;
    strcpy(action->data.alarm.message, runtime->selected_alarm->message);
    return 0;
}

void l1_machine_runtime_init_with_alarm(
    L1MachineRuntime *runtime,
    const L1DeviceConfig *device,
    const L1AlarmInjectionConfig *alarm_injection)
{
    if (runtime == NULL) return;
    memset(runtime, 0, sizeof(*runtime));
    runtime->device = device;
    runtime->state = L1_RUNTIME_IDLE;
    if (alarm_injection != NULL) {
        runtime->alarm_injection = *alarm_injection;
    } else {
        runtime->alarm_injection.mode = L1_ALARM_INJECTION_NONE;
    }
}

void l1_machine_runtime_init(L1MachineRuntime *runtime,
                             const L1DeviceConfig *device,
                             int error_after_qty)
{
    L1AlarmInjectionConfig config;

    memset(&config, 0, sizeof(config));
    if (error_after_qty > 0) {
        config.mode = L1_ALARM_INJECTION_FIXED;
        config.trigger_after_qty = error_after_qty;
        config.probability_percent = 100;
    }
    l1_machine_runtime_init_with_alarm(runtime, device, &config);
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
    schedule_alarm_for_job(runtime);
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
        return append_unit_events(
                out_actions, runtime, runtime->reported_qty + 1);
    }
    if (runtime->processed_qty >= runtime->target_qty) return -1;

    ++runtime->processed_qty;
    if (inspection_runtime
        && append_unit_events(
                out_actions, runtime, runtime->processed_qty) != 0) return -1;

    if (runtime->alarm_scheduled
        && !runtime->alarm_triggered
        && runtime->processed_qty >= runtime->alarm_after_qty
        && runtime->processed_qty < runtime->target_qty) {
        int stop_required = runtime->selected_alarm != NULL
            && runtime->selected_alarm->stop_required;

        runtime->alarm_triggered = 1;
        if (stop_required) {
            runtime->state = L1_RUNTIME_ERROR_PAUSED;
            if ((!inspection_runtime
                 && append_unreported_production(out_actions, runtime,
                                                 L1_PRODUCTION_RUNNING) != 0)
                || append_selected_alarm(out_actions, runtime) != 0
                || append_status(out_actions, runtime, L1_MACHINE_ERROR,
                                 runtime->lot_no,
                                 inspection_runtime
                                     ? "inspection_paused_by_error"
                                     : "production_paused_by_error") != 0) {
                return -1;
            }
            return 0;
        }
        if (append_selected_alarm(out_actions, runtime) != 0) {
            return -1;
        }
    }

    if (runtime->processed_qty == runtime->target_qty) {
        runtime->state = L1_RUNTIME_IDLE;
        if ((!inspection_runtime
             && append_unreported_production(out_actions, runtime, L1_PRODUCTION_COMPLETED) != 0)
            || append_status(out_actions, runtime, L1_MACHINE_IDLE, "-",
                             inspection_runtime ? "inspection_finished" : "production_finished") != 0) {
            return -1;
        }
    } else if (!inspection_runtime
               && append_unreported_production(
                       out_actions, runtime, L1_PRODUCTION_RUNNING) != 0) {
        return -1;
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
