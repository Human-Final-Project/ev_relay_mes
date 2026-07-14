#include <stdio.h>
#include <string.h>

#include "device_config.h"

static void print_available_machines(void)
{
    size_t index;

    printf("Available machines:\n");
    for (index = 0; index < l1_device_config_count(); ++index) {
        const L1DeviceConfig *config = l1_device_config_at(index);

        printf("  %s -> %s\n", config->machine_id, config->process_code);
    }
}

static void print_usage(const char *program_name)
{
    printf("Usage: %s MACHINE_ID\n", program_name);
    printf("       %s --list\n", program_name);
    print_available_machines();
}

int main(int argc, char **argv)
{
    const L1DeviceConfig *config;

    if (argc == 2 && strcmp(argv[1], "--list") == 0) {
        print_available_machines();
        return 0;
    }
    if (argc != 2) {
        print_usage(argv[0]);
        return 1;
    }

    config = l1_device_config_find(argv[1]);
    if (config == NULL) {
        fprintf(stderr, "Unknown MACHINE_ID: %s\n", argv[1]);
        print_available_machines();
        return 1;
    }

    printf("EV Relay MES L1 simulator\n");
    printf("Machine: %s\n", config->machine_id);
    printf("Process: %s\n", config->process_code);
    printf("L1 stages 1-3 ready. TCP connection is implemented in stage 4.\n");
    return 0;
}
