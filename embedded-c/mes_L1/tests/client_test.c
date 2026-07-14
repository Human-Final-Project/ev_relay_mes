#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "client.h"
#include "device_config.h"

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

typedef struct {
    int command_count;
    int error_count;
    L1ClientFeedResult last_error;
    L1ProtocolResult last_protocol_result;
    L1Command last_command;
} Observer;

static void observe_command(const L1Command *command, void *context)
{
    Observer *observer = (Observer *)context;

    ++observer->command_count;
    observer->last_command = *command;
}

static void observe_error(L1ClientFeedResult error,
                          L1ProtocolResult protocol_result,
                          const char *line,
                          void *context)
{
    Observer *observer = (Observer *)context;

    (void)line;
    ++observer->error_count;
    observer->last_error = error;
    observer->last_protocol_result = protocol_result;
}

static void setup(L1ClientSession *session,
                  L1ClientHandlers *handlers,
                  Observer *observer)
{
    memset(observer, 0, sizeof(*observer));
    l1_client_session_init(session,
                           l1_device_config_find("EQ-WIND-01"));
    handlers->on_command = observe_command;
    handlers->on_error = observe_error;
    handlers->context = observer;
}

static void test_fragmented_command(void)
{
    L1ClientSession session;
    L1ClientHandlers handlers;
    Observer observer;

    setup(&session, &handlers, &observer);
    CHECK(l1_client_session_feed(&session,
                                 "V1,COMMAND,101,START,EQ-WI",
                                 strlen("V1,COMMAND,101,START,EQ-WI"),
                                 &handlers) == L1_CLIENT_FEED_OK);
    CHECK(observer.command_count == 0);
    CHECK(l1_client_session_feed(
              &session,
              "ND-01,OP20,EVR-LOT-001,100\n",
              strlen("ND-01,OP20,EVR-LOT-001,100\n"),
              &handlers) == L1_CLIENT_FEED_OK);
    CHECK(observer.command_count == 1);
    CHECK(observer.last_command.command_id == 101);
    CHECK(observer.last_command.type == L1_COMMAND_START);
    CHECK(observer.last_command.input_qty == 100);
}

static void test_multiple_commands_in_one_receive(void)
{
    const char *messages =
        "V1,COMMAND,102,STOP,EQ-WIND-01,OP20,EVR-LOT-001,0\n"
        "V1,COMMAND,103,RESUME,EQ-WIND-01,OP20,EVR-LOT-001,50\n";
    L1ClientSession session;
    L1ClientHandlers handlers;
    Observer observer;

    setup(&session, &handlers, &observer);
    CHECK(l1_client_session_feed(&session,
                                 messages,
                                 strlen(messages),
                                 &handlers) == L1_CLIENT_FEED_OK);
    CHECK(observer.command_count == 2);
    CHECK(observer.last_command.command_id == 103);
    CHECK(observer.last_command.type == L1_COMMAND_RESUME);
}

static void test_other_machine_is_rejected(void)
{
    const char *message =
        "V1,COMMAND,104,START,EQ-WELD-01,OP30,EVR-LOT-001,100\n";
    L1ClientSession session;
    L1ClientHandlers handlers;
    Observer observer;

    setup(&session, &handlers, &observer);
    CHECK(l1_client_session_feed(&session,
                                 message,
                                 strlen(message),
                                 &handlers)
          == L1_CLIENT_FEED_MACHINE_MISMATCH);
    CHECK(observer.command_count == 0);
    CHECK(observer.error_count == 1);
    CHECK(observer.last_error == L1_CLIENT_FEED_MACHINE_MISMATCH);
}

static void test_invalid_protocol_is_rejected(void)
{
    const char *message =
        "V2,COMMAND,105,START,EQ-WIND-01,OP20,EVR-LOT-001,100\n";
    L1ClientSession session;
    L1ClientHandlers handlers;
    Observer observer;

    setup(&session, &handlers, &observer);
    CHECK(l1_client_session_feed(&session,
                                 message,
                                 strlen(message),
                                 &handlers)
          == L1_CLIENT_FEED_PROTOCOL_ERROR);
    CHECK(observer.command_count == 0);
    CHECK(observer.error_count == 1);
    CHECK(observer.last_protocol_result
          == L1_PROTOCOL_UNSUPPORTED_VERSION);
}

static void test_oversized_message_is_discarded_until_lf(void)
{
    char oversized[L1_PROTOCOL_MAX_MESSAGE_SIZE + 20];
    const char *valid =
        "V1,COMMAND,106,START,EQ-WIND-01,OP20,EVR-LOT-001,10\n";
    L1ClientSession session;
    L1ClientHandlers handlers;
    Observer observer;

    memset(oversized, 'A', sizeof(oversized));
    oversized[sizeof(oversized) - 1] = '\n';
    setup(&session, &handlers, &observer);

    CHECK(l1_client_session_feed(&session,
                                 oversized,
                                 sizeof(oversized),
                                 &handlers)
          == L1_CLIENT_FEED_MESSAGE_TOO_LONG);
    CHECK(observer.error_count == 1);
    CHECK(observer.last_error == L1_CLIENT_FEED_MESSAGE_TOO_LONG);
    CHECK(l1_client_session_feed(&session,
                                 valid,
                                 strlen(valid),
                                 &handlers) == L1_CLIENT_FEED_OK);
    CHECK(observer.command_count == 1);
}

int main(void)
{
    test_fragmented_command();
    test_multiple_commands_in_one_receive();
    test_other_machine_is_rejected();
    test_invalid_protocol_is_rejected();
    test_oversized_message_is_discarded_until_lf();

    if (checks_failed != 0) {
        fprintf(stderr,
                "%d of %d L1 client checks failed.\n",
                checks_failed,
                checks_run);
        return EXIT_FAILURE;
    }
    printf("All %d L1 client checks passed.\n", checks_run);
    return EXIT_SUCCESS;
}
