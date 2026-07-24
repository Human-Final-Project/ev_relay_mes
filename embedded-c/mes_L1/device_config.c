#include "device_config.h"

#include <string.h>

#define ARRAY_COUNT(values) (sizeof(values) / sizeof((values)[0]))
#define ERROR_ALARM(code, message) \
    {code, message, L1_DEVICE_ALARM_ERROR, 1}
#define WARNING_ALARM(code, message) \
    {code, message, L1_DEVICE_ALARM_WARNING, 0}

/*
 * OP20 and OP30 are the parallel prerequisite processes for OP40_OP50.
 * A blocking alarm on either process can prevent the downstream merge from
 * ever starting in the non-individual-stop MVP.  Therefore these two devices
 * expose WARNING-only scenarios: alarms are still reported, but production
 * continues and no RESUME command is required.
 */
static const L1AlarmScenario WIND_ALARMS[] = {
    WARNING_ALARM("WIRE_TENSION_WARN", "wire_tension_warn"),
    WARNING_ALARM("WINDING_VIBRATION_WARN", "winding_vibration_warn")
};

static const L1AlarmScenario WELD_ALARMS[] = {
    WARNING_ALARM("WELD_TIP_WEAR_WARN", "weld_tip_wear_warn"),
    WARNING_ALARM("WELD_TEMPERATURE_WARN", "weld_temperature_warn")
};

static const L1AlarmScenario ASSY_ALARMS[] = {
    WARNING_ALARM("ALIGNMENT_DEVIATION_WARN", "alignment_deviation_warn"),
    WARNING_ALARM("PICK_CYCLE_DELAY_WARN", "pick_cycle_delay_warn"),
    ERROR_ALARM("PICK_PLACE_ERROR", "pick_place_error"),
    ERROR_ALARM("ASSEMBLY_JAM", "assembly_jam")
};

static const L1AlarmScenario SEAL_ALARMS[] = {
    WARNING_ALARM("VACUUM_LEVEL_WARN", "vacuum_level_warn"),
    WARNING_ALARM("CHAMBER_TEMPERATURE_WARN", "chamber_temperature_warn"),
    ERROR_ALARM("VACUUM_PUMP_ERROR", "vacuum_pump_error"),
    ERROR_ALARM("CHAMBER_PRESSURE_ERROR", "chamber_pressure_error")
};

static const L1AlarmScenario TEST_ALARMS[] = {
    WARNING_ALARM("PROBE_CONTACT_WARN", "probe_contact_warn"),
    WARNING_ALARM("MEASURE_DRIFT_WARN", "measure_drift_warn"),
    ERROR_ALARM("HV_TESTER_ERROR", "hv_tester_error"),
    ERROR_ALARM("TEST_SEQUENCE_ERROR", "test_sequence_error")
};

static const L1AlarmScenario PACK_ALARMS[] = {
    WARNING_ALARM("LABEL_PRINT_QUALITY_WARN", "label_print_quality_warn"),
    WARNING_ALARM("BARCODE_READ_RATE_WARN", "barcode_read_rate_warn"),
    ERROR_ALARM("LABEL_PRINTER_ERROR", "label_printer_error"),
    ERROR_ALARM("BARCODE_READER_ERROR", "barcode_reader_error")
};

static const L1DeviceConfig DEVICE_CONFIGS[] = {
    {"EQ-WIND-01", "OP20", WIND_ALARMS, ARRAY_COUNT(WIND_ALARMS)},
    {"EQ-WELD-01", "OP30", WELD_ALARMS, ARRAY_COUNT(WELD_ALARMS)},
    {"EQ-ASSY-01", "OP40_OP50", ASSY_ALARMS, ARRAY_COUNT(ASSY_ALARMS)},
    {"EQ-SEAL-01", "OP60", SEAL_ALARMS, ARRAY_COUNT(SEAL_ALARMS)},
    {"EQ-TEST-01", "OP70", TEST_ALARMS, ARRAY_COUNT(TEST_ALARMS)},
    {"EQ-PACK-01", "OP80", PACK_ALARMS, ARRAY_COUNT(PACK_ALARMS)}
};

size_t l1_device_config_count(void)
{
    return ARRAY_COUNT(DEVICE_CONFIGS);
}

const L1DeviceConfig *l1_device_config_at(size_t index)
{
    return index < l1_device_config_count()
        ? &DEVICE_CONFIGS[index]
        : NULL;
}

const L1DeviceConfig *l1_device_config_find(const char *machine_id)
{
    size_t index;

    if (machine_id == NULL) {
        return NULL;
    }
    for (index = 0; index < l1_device_config_count(); ++index) {
        if (strcmp(machine_id, DEVICE_CONFIGS[index].machine_id) == 0) {
            return &DEVICE_CONFIGS[index];
        }
    }
    return NULL;
}

int l1_device_config_matches(const char *machine_id,
                             const char *process_code)
{
    const L1DeviceConfig *config = l1_device_config_find(machine_id);

    return config != NULL
        && process_code != NULL
        && strcmp(config->process_code, process_code) == 0;
}

const L1AlarmScenario *l1_device_alarm_at(const L1DeviceConfig *config,
                                          size_t index)
{
    return config != NULL && index < config->alarm_count
        ? &config->alarms[index]
        : NULL;
}

const L1AlarmScenario *l1_device_alarm_find(const L1DeviceConfig *config,
                                            const char *alarm_code)
{
    size_t index;

    if (config == NULL || alarm_code == NULL) {
        return NULL;
    }
    for (index = 0; index < config->alarm_count; ++index) {
        if (strcmp(config->alarms[index].alarm_code, alarm_code) == 0) {
            return &config->alarms[index];
        }
    }
    return NULL;
}

const L1AlarmScenario *l1_device_default_error_alarm(
    const L1DeviceConfig *config)
{
    size_t index;

    if (config == NULL) {
        return NULL;
    }
    for (index = 0; index < config->alarm_count; ++index) {
        if (config->alarms[index].level == L1_DEVICE_ALARM_ERROR
            && config->alarms[index].stop_required) {
            return &config->alarms[index];
        }
    }
    return config->alarm_count > 0 ? &config->alarms[0] : NULL;
}
