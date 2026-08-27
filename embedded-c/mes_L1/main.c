#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "client.h"
#include "config.h"
#include "device_config.h"
#include "net.h"

#define DEFAULT_RANDOM_ALARM_RATE 10

static void print_available_machines(void)
{
    size_t index;

    printf("Available machines:\n");
    for (index = 0; index < l1_device_config_count(); ++index) {
        const L1DeviceConfig *config = l1_device_config_at(index);
        size_t alarm_index;

        printf("  %s -> %s\n", config->machine_id, config->process_code);
        for (alarm_index = 0; alarm_index < config->alarm_count; ++alarm_index) {
            const L1AlarmScenario *alarm = l1_device_alarm_at(config, alarm_index);
            printf("      %s (%s, %s)\n",
                   alarm->alarm_code,
                   alarm->level == L1_DEVICE_ALARM_WARNING ? "WARNING" : "ERROR",
                   alarm->stop_required ? "stops production" : "continues production");
        }
    }
}

static void print_usage(const char *program_name)
{
    printf("Usage:\n");
    printf("  %s MACHINE_ID [L2_IP] [L2_PORT] [ERROR_AFTER_QTY]\n",
           program_name);
    printf("  %s MACHINE_ID [L2_IP] [L2_PORT] [options]\n",
           program_name);
    printf("  %s --list\n", program_name);
    printf("Options:\n");
    printf("  --alarm-random       Randomly select one alarm for each START command.\n");
    printf("  --alarm-rate N       Probability (0-100) that a START job schedules an alarm.\n");
    printf("  --alarm CODE         Use one fixed alarm code registered for the machine.\n");
    printf("  --error-after N      Trigger after N processed products.\n");
    printf("                        Without this option, the trigger quantity is selected per job.\n");
    print_available_machines();
}

static int parse_port(const char *text, uint16_t *out_port)
{
    char *end = NULL;
    unsigned long value;

    if (text == NULL || out_port == NULL || text[0] == '\0') {
        return -1;
    }
    value = strtoul(text, &end, 10);
    if (*end != '\0' || value == 0 || value > 65535UL) {
        return -1;
    }
    *out_port = (uint16_t)value;
    return 0;
}

static int parse_nonnegative_quantity(const char *text, int *out_quantity)
{
    char *end = NULL;
    unsigned long value;

    if (text == NULL || out_quantity == NULL || text[0] == '\0') {
        return -1;
    }
    value = strtoul(text, &end, 10);
    if (*end != '\0' || value > 2147483647UL) {
        return -1;
    }
    *out_quantity = (int)value;
    return 0;
}

static int parse_percent(const char *text, int *out_percent)
{
    int value;

    if (parse_nonnegative_quantity(text, &value) != 0 || value > 100) {
        return -1;
    }
    *out_percent = value;
    return 0;
}

static int is_option(const char *text)
{
    return text != NULL && text[0] == '-' && text[1] == '-';
}

static const char *alarm_mode_name(L1AlarmInjectionMode mode)
{
    switch (mode) {
    case L1_ALARM_INJECTION_FIXED: return "fixed";
    case L1_ALARM_INJECTION_RANDOM: return "random";
    case L1_ALARM_INJECTION_NONE:
    default: return "disabled";
    }
}

int main(int argc, char **argv)
{
    const L1DeviceConfig *config;
    const char *server_address = L1_DEFAULT_SERVER_ADDRESS;
    uint16_t server_port = L1_DEFAULT_SERVER_PORT;
    L1AlarmInjectionConfig alarm_injection;
    int argument_index = 2;
    int exit_code;

    memset(&alarm_injection, 0, sizeof(alarm_injection));
    alarm_injection.probability_percent = DEFAULT_RANDOM_ALARM_RATE;

    if (argc == 2 && strcmp(argv[1], "--list") == 0) {
        print_available_machines();
        return 0;
    }
    if (argc < 2) {
        print_usage(argv[0]);
        return 1;
    }

    config = l1_device_config_find(argv[1]);
    if (config == NULL) {
        fprintf(stderr, "Unknown MACHINE_ID: %s\n", argv[1]);
        print_available_machines();
        return 1;
    }

    if (argument_index < argc && !is_option(argv[argument_index])) {
        server_address = argv[argument_index++];
    }
    if (argument_index < argc && !is_option(argv[argument_index])) {
        if (parse_port(argv[argument_index], &server_port) != 0) {
            fprintf(stderr, "Invalid L2_PORT: %s\n", argv[argument_index]);
            return 1;
        }
        ++argument_index;
    }
    /* Backward compatibility: the fourth positional argument is ERROR_AFTER_QTY. */
    if (argument_index < argc && !is_option(argv[argument_index])) {
        if (parse_nonnegative_quantity(argv[argument_index],
                                       &alarm_injection.trigger_after_qty) != 0) {
            fprintf(stderr, "Invalid ERROR_AFTER_QTY: %s\n", argv[argument_index]);
            return 1;
        }
        if (alarm_injection.trigger_after_qty > 0) {
            alarm_injection.mode = L1_ALARM_INJECTION_FIXED;
        }
        ++argument_index;
    }

    while (argument_index < argc) {
        const char *option = argv[argument_index++];

        if (strcmp(option, "--alarm-random") == 0) {
            alarm_injection.mode = L1_ALARM_INJECTION_RANDOM;
        } else if (strcmp(option, "--alarm-rate") == 0) {
            if (argument_index >= argc
                || parse_percent(argv[argument_index],
                                 &alarm_injection.probability_percent) != 0) {
                fprintf(stderr, "--alarm-rate requires an integer from 0 to 100.\n");
                return 1;
            }
            ++argument_index;
        } else if (strcmp(option, "--alarm") == 0) {
            if (argument_index >= argc) {
                fprintf(stderr, "--alarm requires an alarm code.\n");
                return 1;
            }
            alarm_injection.alarm_code = argv[argument_index++];
            alarm_injection.mode = L1_ALARM_INJECTION_FIXED;
        } else if (strcmp(option, "--error-after") == 0) {
            if (argument_index >= argc
                || parse_nonnegative_quantity(
                       argv[argument_index],
                       &alarm_injection.trigger_after_qty) != 0) {
                fprintf(stderr, "--error-after requires a nonnegative integer.\n");
                return 1;
            }
            if (alarm_injection.mode == L1_ALARM_INJECTION_NONE
                && alarm_injection.trigger_after_qty > 0) {
                alarm_injection.mode = L1_ALARM_INJECTION_FIXED;
            }
            ++argument_index;
        } else {
            fprintf(stderr, "Unknown option: %s\n", option);
            print_usage(argv[0]);
            return 1;
        }
    }

    if (alarm_injection.alarm_code != NULL
        && l1_device_alarm_find(config, alarm_injection.alarm_code) == NULL) {
        fprintf(stderr,
                "Alarm %s is not registered for machine %s.\n",
                alarm_injection.alarm_code,
                config->machine_id);
        return 1;
    }

    printf("EV Relay MES L1 simulator\n");
    printf("Machine: %s\n", config->machine_id);
    printf("Process: %s\n", config->process_code);
    printf("Alarm injection: %s", alarm_mode_name(alarm_injection.mode));
    if (alarm_injection.mode == L1_ALARM_INJECTION_RANDOM) {
        printf(" (job probability %d%%)", alarm_injection.probability_percent);
    }
    if (alarm_injection.alarm_code != NULL) {
        printf(" alarm=%s", alarm_injection.alarm_code);
    }
    if (alarm_injection.trigger_after_qty > 0) {
        printf(" after=%d", alarm_injection.trigger_after_qty);
    }
    printf("\n");

    if (l1_net_runtime_init() != 0) {
        fprintf(stderr,
                "Failed to initialize network runtime: %s\n",
                l1_net_last_error_message());
        return 1;
    }
    exit_code = l1_client_run(config,
                              server_address,
                              server_port,
                              &alarm_injection) == 0
                    ? 0
                    : 1;
    l1_net_runtime_cleanup();
    return exit_code;
}
