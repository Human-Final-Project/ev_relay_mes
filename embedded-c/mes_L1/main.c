#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "client.h"
#include "config.h"
#include "device_config.h"
#include "net.h"

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
    printf("Usage: %s MACHINE_ID [L2_IP] [L2_PORT]\n", program_name);
    printf("       %s --list\n", program_name);
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

int main(int argc, char **argv)
{
    const L1DeviceConfig *config;
    const char *server_address = L1_DEFAULT_SERVER_ADDRESS;
    uint16_t server_port = L1_DEFAULT_SERVER_PORT;
    int exit_code;

    if (argc == 2 && strcmp(argv[1], "--list") == 0) {
        print_available_machines();
        return 0;
    }
    if (argc < 2 || argc > 4) {
        print_usage(argv[0]);
        return 1;
    }

    config = l1_device_config_find(argv[1]);
    if (config == NULL) {
        fprintf(stderr, "Unknown MACHINE_ID: %s\n", argv[1]);
        print_available_machines();
        return 1;
    }

    if (argc >= 3) {
        server_address = argv[2];
    }
    if (argc == 4 && parse_port(argv[3], &server_port) != 0) {
        fprintf(stderr, "Invalid L2_PORT: %s\n", argv[3]);
        return 1;
    }

    printf("EV Relay MES L1 simulator\n");
    printf("Machine: %s\n", config->machine_id);
    printf("Process: %s\n", config->process_code);

    if (l1_net_runtime_init() != 0) {
        fprintf(stderr,
                "Failed to initialize network runtime: %s\n",
                l1_net_last_error_message());
        return 1;
    }
    exit_code = l1_client_run(config,
                              server_address,
                              server_port) == 0
                    ? 0
                    : 1;
    l1_net_runtime_cleanup();
    return exit_code;
}
