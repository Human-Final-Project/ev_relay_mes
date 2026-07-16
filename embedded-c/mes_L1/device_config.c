#include "device_config.h"

#include <string.h>

static const L1DeviceConfig DEVICE_CONFIGS[] = {
    {"EQ-WIND-01", "OP20", "WIRE_BREAK", "wire_break"},
    {"EQ-WELD-01", "OP30", "WELD_POWER_ERROR", "weld_power_error"},
    {"EQ-ASSY-01", "OP40_OP50", "ASSEMBLY_JAM", "assembly_jam"},
    {"EQ-SEAL-01", "OP60", "CHAMBER_PRESSURE_ERROR", "chamber_pressure_error"},
    {"EQ-TEST-01", "OP70", "TEST_PROBE_ERROR", "test_probe_error"},
    {"EQ-PACK-01", "OP80", "LABEL_PRINTER_ERROR", "label_printer_error"}
};

size_t l1_device_config_count(void)
{
    return sizeof(DEVICE_CONFIGS) / sizeof(DEVICE_CONFIGS[0]);
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
