#include <stdio.h>
#include <stdlib.h>

#include "api_client.h"
#include "collector.h"
#include "net.h"
#include "scheduler.h"

int main(void)
{
    int exit_code = EXIT_FAILURE;

    if (net_runtime_init() != 0) {
        fprintf(stderr, "Failed to initialize network runtime.\n");
        return EXIT_FAILURE;
    }

    if (scheduler_init() != 0) {
        fprintf(stderr, "Failed to initialize scheduler.\n");
        goto cleanup_network;
    }

    if (api_client_init() != 0) {
        fprintf(stderr, "Failed to initialize API client.\n");
        goto cleanup_scheduler;
    }

    exit_code = collector_run() == 0 ? EXIT_SUCCESS : EXIT_FAILURE;

    api_client_cleanup();
cleanup_scheduler:
    scheduler_cleanup();
cleanup_network:
    net_runtime_cleanup();

    return exit_code;
}
