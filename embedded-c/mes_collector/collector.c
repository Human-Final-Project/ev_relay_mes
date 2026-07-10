#include "collector.h"

#include <stdio.h>

#include "config.h"

int collector_run(void)
{
    printf("EV Relay MES L2 collector scaffold\n");
    printf("TCP bind: %s:%d\n", COLLECTOR_BIND_ADDRESS, COLLECTOR_TCP_PORT);
    printf("Backend: %s\n", MES_BACKEND_BASE_URL);
    printf("Max L1 connections: %d\n", COLLECTOR_MAX_L1_CONNECTIONS);
    printf("Heartbeat/timeout: %d/%d seconds\n",
           COLLECTOR_HEARTBEAT_INTERVAL_SECONDS,
           COLLECTOR_COMM_TIMEOUT_SECONDS);

    return 0;
}
