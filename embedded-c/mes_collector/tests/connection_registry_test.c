#include <stdio.h>
#include <stdlib.h>

#include "connection_registry.h"

static int checks_run;
static int checks_failed;

#define CHECK(condition)                                                     \
    do {                                                                     \
        ++checks_run;                                                        \
        if (!(condition)) {                                                  \
            ++checks_failed;                                                 \
            fprintf(stderr,                                                  \
                    "FAIL %s:%d: %s\n",                                    \
                    __FILE__,                                                \
                    __LINE__,                                                \
                    #condition);                                             \
        }                                                                    \
    } while (0)

static void test_capacity_registration_and_release(void)
{
    static const char *machines[COLLECTOR_MAX_L1_CONNECTIONS] = {
        "EQ-WIND-01",
        "EQ-WELD-01",
        "EQ-ASSY-01",
        "EQ-SEAL-01",
        "EQ-TEST-01",
        "EQ-PACK-01"
    };
    CollectorConnectionRegistry registry;
    CollectorConnection *connections[COLLECTOR_MAX_L1_CONNECTIONS];
    size_t index;

    CHECK(connection_registry_init(&registry) == 0);
    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        NetSocket fake_socket = (NetSocket)(100 + index);

        connections[index] = connection_registry_acquire(&registry,
                                                         fake_socket,
                                                         "127.0.0.1",
                                                         (uint16_t)(10000 + index));
        CHECK(connections[index] != NULL);
    }
    CHECK(connection_registry_active_count(&registry)
          == COLLECTOR_MAX_L1_CONNECTIONS);
    CHECK(connection_registry_acquire(&registry,
                                      (NetSocket)999,
                                      "127.0.0.1",
                                      19999) == NULL);

    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        CHECK(connection_registry_register_machine(&registry,
                                                   connections[index],
                                                   machines[index])
              == CONNECTION_REGISTER_OK);
    }
    CHECK(connection_registry_registered_count(&registry)
          == COLLECTOR_MAX_L1_CONNECTIONS);

    for (index = 0; index < COLLECTOR_MAX_L1_CONNECTIONS; ++index) {
        NetSocket detached = connection_registry_detach(&registry,
                                                        connections[index]);

        CHECK(detached == (NetSocket)(100 + index));
    }
    CHECK(connection_registry_active_count(&registry) == 0);
    CHECK(connection_registry_registered_count(&registry) == 0);
    connection_registry_destroy(&registry);
}

static void test_duplicate_machine_is_rejected(void)
{
    CollectorConnectionRegistry registry;
    CollectorConnection *first;
    CollectorConnection *second;

    CHECK(connection_registry_init(&registry) == 0);
    first = connection_registry_acquire(&registry,
                                        (NetSocket)200,
                                        "127.0.0.1",
                                        20001);
    second = connection_registry_acquire(&registry,
                                         (NetSocket)201,
                                         "127.0.0.1",
                                         20002);
    CHECK(first != NULL);
    CHECK(second != NULL);
    CHECK(connection_registry_register_machine(&registry,
                                               first,
                                               "EQ-WIND-01")
          == CONNECTION_REGISTER_OK);
    CHECK(connection_registry_register_machine(&registry,
                                               second,
                                               "EQ-WIND-01")
          == CONNECTION_REGISTER_DUPLICATE_MACHINE);
    CHECK(connection_registry_registered_count(&registry) == 1);
    CHECK(connection_registry_detach(&registry, first) == (NetSocket)200);
    CHECK(connection_registry_register_machine(&registry,
                                               second,
                                               "EQ-WIND-01")
          == CONNECTION_REGISTER_OK);
    CHECK(connection_registry_detach(&registry, second) == (NetSocket)201);
    connection_registry_destroy(&registry);
}

int main(void)
{
    test_capacity_registration_and_release();
    test_duplicate_machine_is_rejected();

    if (checks_failed != 0) {
        fprintf(stderr,
                "%d of %d connection registry checks failed.\n",
                checks_failed,
                checks_run);
        return EXIT_FAILURE;
    }
    printf("PASS: %d connection registry checks\n", checks_run);
    return EXIT_SUCCESS;
}
