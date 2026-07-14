#include <stdio.h>
#include <string.h>

#include "collector.h"

typedef struct {
    int message_count;
    int error_count;
    ProtocolEventType last_type;
    CollectorSessionError last_error;
    ProtocolResult last_protocol_result;
} TestObserver;

static int tests_run;
static int tests_failed;

#define CHECK(condition)                                                       \
    do {                                                                       \
        ++tests_run;                                                           \
        if (!(condition)) {                                                    \
            ++tests_failed;                                                    \
            fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #condition); \
        }                                                                      \
    } while (0)

static void observe_message(const ProtocolMessage *message, void *context)
{
    TestObserver *observer = (TestObserver *)context;

    ++observer->message_count;
    observer->last_type = message->type;
}

static void observe_error(CollectorSessionError error,
                          ProtocolResult protocol_result,
                          const char *line,
                          void *context)
{
    TestObserver *observer = (TestObserver *)context;

    (void)line;
    ++observer->error_count;
    observer->last_error = error;
    observer->last_protocol_result = protocol_result;
}

static CollectorSessionHandlers make_handlers(TestObserver *observer)
{
    CollectorSessionHandlers handlers;

    handlers.on_message = observe_message;
    handlers.on_error = observe_error;
    handlers.context = observer;
    return handlers;
}

static void test_fragmented_hello(void)
{
    CollectorSession session;
    TestObserver observer = {0};
    CollectorSessionHandlers handlers = make_handlers(&observer);

    collector_session_init(&session);
    CHECK(collector_session_feed(&session,
                                 "V1,HEL",
                                 strlen("V1,HEL"),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(session.hello_received == 0);
    CHECK(observer.message_count == 0);

    CHECK(collector_session_feed(&session,
                                 "LO,EQ-WIND-01\n",
                                 strlen("LO,EQ-WIND-01\n"),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(session.hello_received == 1);
    CHECK(strcmp(session.machine_id, "EQ-WIND-01") == 0);
    CHECK(observer.message_count == 1);
    CHECK(observer.last_type == PROTOCOL_EVENT_HELLO);
}

static void test_multiple_messages_in_one_receive(void)
{
    const char *data =
        "V1,HELLO,EQ-WIND-01\n"
        "V1,HEARTBEAT,EQ-WIND-01\n"
        "V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-001,10,9,1,COMPLETED\n";
    CollectorSession session;
    TestObserver observer = {0};
    CollectorSessionHandlers handlers = make_handlers(&observer);

    collector_session_init(&session);
    CHECK(collector_session_feed(&session,
                                 data,
                                 strlen(data),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(observer.message_count == 3);
    CHECK(observer.error_count == 0);
    CHECK(observer.last_type == PROTOCOL_EVENT_PRODUCTION);
}

static void test_first_message_must_be_hello(void)
{
    CollectorSession session;
    TestObserver observer = {0};
    CollectorSessionHandlers handlers = make_handlers(&observer);
    const char *heartbeat = "V1,HEARTBEAT,EQ-WIND-01\n";

    collector_session_init(&session);
    CHECK(collector_session_feed(&session,
                                 heartbeat,
                                 strlen(heartbeat),
                                 &handlers)
          == COLLECTOR_FEED_CLOSE_CONNECTION);
    CHECK(session.hello_received == 0);
    CHECK(observer.message_count == 0);
    CHECK(observer.error_count == 1);
    CHECK(observer.last_error == COLLECTOR_SESSION_ERROR_HELLO_REQUIRED);
}

static void test_invalid_first_message_closes_connection(void)
{
    CollectorSession session;
    TestObserver observer = {0};
    CollectorSessionHandlers handlers = make_handlers(&observer);
    const char *invalid = "V2,HELLO,EQ-WIND-01\n";

    collector_session_init(&session);
    CHECK(collector_session_feed(&session,
                                 invalid,
                                 strlen(invalid),
                                 &handlers)
          == COLLECTOR_FEED_CLOSE_CONNECTION);
    CHECK(observer.error_count == 1);
    CHECK(observer.last_error == COLLECTOR_SESSION_ERROR_PROTOCOL);
    CHECK(observer.last_protocol_result
          == PROTOCOL_RESULT_UNSUPPORTED_VERSION);
}

static void test_machine_id_mismatch_is_discarded(void)
{
    const char *hello = "V1,HELLO,EQ-WIND-01\n";
    const char *wrong_machine = "V1,HEARTBEAT,EQ-WELD-01\n";
    CollectorSession session;
    TestObserver observer = {0};
    CollectorSessionHandlers handlers = make_handlers(&observer);

    collector_session_init(&session);
    CHECK(collector_session_feed(&session,
                                 hello,
                                 strlen(hello),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(collector_session_feed(&session,
                                 wrong_machine,
                                 strlen(wrong_machine),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(observer.message_count == 1);
    CHECK(observer.error_count == 1);
    CHECK(observer.last_error
          == COLLECTOR_SESSION_ERROR_MACHINE_ID_MISMATCH);
}

static void test_invalid_message_after_hello_is_discarded(void)
{
    const char *data =
        "V1,HELLO,EQ-WIND-01\n"
        "V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-001,10,8,1,COMPLETED\n"
        "V1,HEARTBEAT,EQ-WIND-01\n";
    CollectorSession session;
    TestObserver observer = {0};
    CollectorSessionHandlers handlers = make_handlers(&observer);

    collector_session_init(&session);
    CHECK(collector_session_feed(&session,
                                 data,
                                 strlen(data),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(observer.message_count == 2);
    CHECK(observer.last_type == PROTOCOL_EVENT_HEARTBEAT);
    CHECK(observer.error_count == 1);
    CHECK(observer.last_error == COLLECTOR_SESSION_ERROR_PROTOCOL);
    CHECK(observer.last_protocol_result == PROTOCOL_RESULT_INVALID_VALUE);
}

static void test_duplicate_hello_is_discarded(void)
{
    const char *data =
        "V1,HELLO,EQ-WIND-01\n"
        "V1,HELLO,EQ-WIND-01\n";
    CollectorSession session;
    TestObserver observer = {0};
    CollectorSessionHandlers handlers = make_handlers(&observer);

    collector_session_init(&session);
    CHECK(collector_session_feed(&session,
                                 data,
                                 strlen(data),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(observer.message_count == 1);
    CHECK(observer.error_count == 1);
    CHECK(observer.last_error == COLLECTOR_SESSION_ERROR_DUPLICATE_HELLO);
}

static void test_oversized_message_is_discarded_until_lf(void)
{
    char oversized[COLLECTOR_MAX_MESSAGE_SIZE + 1];
    const char *hello = "V1,HELLO,EQ-WIND-01\n";
    const char *recovery = "\nV1,HEARTBEAT,EQ-WIND-01\n";
    CollectorSession session;
    TestObserver observer = {0};
    CollectorSessionHandlers handlers = make_handlers(&observer);

    memset(oversized, 'A', sizeof(oversized));
    collector_session_init(&session);
    CHECK(collector_session_feed(&session,
                                 hello,
                                 strlen(hello),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(collector_session_feed(&session,
                                 oversized,
                                 sizeof(oversized),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(session.discarding_oversized_message == 1);
    CHECK(observer.error_count == 1);
    CHECK(observer.last_error
          == COLLECTOR_SESSION_ERROR_MESSAGE_TOO_LONG);

    CHECK(collector_session_feed(&session,
                                 recovery,
                                 strlen(recovery),
                                 &handlers)
          == COLLECTOR_FEED_OK);
    CHECK(session.discarding_oversized_message == 0);
    CHECK(observer.message_count == 2);
    CHECK(observer.last_type == PROTOCOL_EVENT_HEARTBEAT);
}

static void test_command_send_preconditions(void)
{
    CollectorSession session;
    ProtocolResult protocol_result = PROTOCOL_RESULT_OK;
    ProtocolCommand command = {
        101,
        PROTOCOL_COMMAND_START,
        "EQ-WIND-01",
        "OP20",
        "EVR-LOT-001",
        100
    };

    collector_session_init(&session);
    CHECK(collector_send_command(&session,
                                 NET_INVALID_SOCKET,
                                 &command,
                                 &protocol_result)
          == COLLECTOR_SEND_NOT_REGISTERED);

    session.hello_received = 1;
    strcpy(session.machine_id, "EQ-WIND-01");
    strcpy(command.machine_id, "EQ-WELD-01");
    strcpy(command.process_code, "OP30");
    CHECK(collector_send_command(&session,
                                 NET_INVALID_SOCKET,
                                 &command,
                                 &protocol_result)
          == COLLECTOR_SEND_MACHINE_ID_MISMATCH);

    strcpy(command.machine_id, "EQ-WIND-01");
    strcpy(command.process_code, "OP20");
    command.input_qty = 0;
    CHECK(collector_send_command(&session,
                                 NET_INVALID_SOCKET,
                                 &command,
                                 &protocol_result)
          == COLLECTOR_SEND_INVALID_COMMAND);
    CHECK(protocol_result == PROTOCOL_RESULT_OUT_OF_RANGE);
}

int main(void)
{
    test_fragmented_hello();
    test_multiple_messages_in_one_receive();
    test_first_message_must_be_hello();
    test_invalid_first_message_closes_connection();
    test_machine_id_mismatch_is_discarded();
    test_invalid_message_after_hello_is_discarded();
    test_duplicate_hello_is_discarded();
    test_oversized_message_is_discarded_until_lf();
    test_command_send_preconditions();

    if (tests_failed == 0) {
        printf("PASS: %d collector checks\n", tests_run);
        return 0;
    }
    fprintf(stderr,
            "FAILED: %d of %d collector checks\n",
            tests_failed,
            tests_run);
    return 1;
}
