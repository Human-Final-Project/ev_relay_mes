#ifndef EV_RELAY_L1_DEVICE_CONFIG_H
#define EV_RELAY_L1_DEVICE_CONFIG_H

#include <stddef.h>

typedef enum {
    L1_DEVICE_ALARM_WARNING = 0,
    L1_DEVICE_ALARM_ERROR
} L1DeviceAlarmLevel;

typedef struct {
    const char *alarm_code;
    const char *message;
    L1DeviceAlarmLevel level;
    int stop_required;
} L1AlarmScenario;

typedef struct {
    const char *machine_id;
    const char *process_code;
    const L1AlarmScenario *alarms;
    size_t alarm_count;
} L1DeviceConfig;

size_t l1_device_config_count(void);
const L1DeviceConfig *l1_device_config_at(size_t index);
const L1DeviceConfig *l1_device_config_find(const char *machine_id);
int l1_device_config_matches(const char *machine_id,
                             const char *process_code);
const L1AlarmScenario *l1_device_alarm_at(const L1DeviceConfig *config,
                                          size_t index);
const L1AlarmScenario *l1_device_alarm_find(const L1DeviceConfig *config,
                                            const char *alarm_code);
const L1AlarmScenario *l1_device_default_error_alarm(
    const L1DeviceConfig *config);

#endif
