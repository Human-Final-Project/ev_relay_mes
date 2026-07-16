CC := gcc

SOURCES := main.c collector.c connection_registry.c thread_compat.c scheduler.c api_client.c protocol.c net.c
OBJECTS := $(SOURCES:.c=.o)

ifeq ($(OS),Windows_NT)
TARGET := mes_collector.exe
PROTOCOL_TEST_TARGET := protocol_tests.exe
COLLECTOR_TEST_TARGET := collector_tests.exe
REGISTRY_TEST_TARGET := connection_registry_tests.exe
API_CLIENT_TEST_TARGET := api_client_tests.exe
SOCKET_LIBS := -lws2_32
RUN_PREFIX := ./
THREAD_FLAGS :=
define CLEAN_COMMAND
	powershell.exe -NoProfile -Command "Remove-Item -Force -ErrorAction SilentlyContinue *.o,$(TARGET),$(PROTOCOL_TEST_TARGET),$(COLLECTOR_TEST_TARGET),$(REGISTRY_TEST_TARGET),$(API_CLIENT_TEST_TARGET); exit 0"
endef
else
TARGET := mes_collector
PROTOCOL_TEST_TARGET := protocol_tests
COLLECTOR_TEST_TARGET := collector_tests
REGISTRY_TEST_TARGET := connection_registry_tests
API_CLIENT_TEST_TARGET := api_client_tests
SOCKET_LIBS :=
RUN_PREFIX := ./
THREAD_FLAGS ?= -pthread
define CLEAN_COMMAND
	$(RM) $(OBJECTS) $(TARGET) $(PROTOCOL_TEST_TARGET) $(COLLECTOR_TEST_TARGET) $(REGISTRY_TEST_TARGET) $(API_CLIENT_TEST_TARGET)
endef
endif

CPPFLAGS := -I.
CFLAGS := -std=c11 -Wall -Wextra -Wpedantic
LDLIBS := $(THREAD_FLAGS) $(SOCKET_LIBS)

.PHONY: all clean run test

all: $(TARGET)

$(TARGET): $(OBJECTS)
	$(CC) $(OBJECTS) -o $@ $(LDLIBS)

%.o: %.c
	$(CC) $(CPPFLAGS) $(CFLAGS) $(THREAD_FLAGS) -c $< -o $@

run: $(TARGET)
	./$(TARGET)

$(PROTOCOL_TEST_TARGET): tests/protocol_test.c protocol.c protocol.h config.h
	$(CC) $(CPPFLAGS) $(CFLAGS) tests/protocol_test.c protocol.c -o $@

$(COLLECTOR_TEST_TARGET): tests/collector_test.c collector.c collector.h connection_registry.c connection_registry.h thread_compat.c thread_compat.h api_client.c api_client.h protocol.c protocol.h net.c net.h config.h
	$(CC) $(CPPFLAGS) $(CFLAGS) $(THREAD_FLAGS) tests/collector_test.c collector.c connection_registry.c thread_compat.c api_client.c protocol.c net.c -o $@ $(LDLIBS)

$(REGISTRY_TEST_TARGET): tests/connection_registry_test.c connection_registry.c connection_registry.h thread_compat.c thread_compat.h protocol.h net.c net.h config.h
	$(CC) $(CPPFLAGS) $(CFLAGS) $(THREAD_FLAGS) tests/connection_registry_test.c connection_registry.c thread_compat.c net.c -o $@ $(LDLIBS)

$(API_CLIENT_TEST_TARGET): tests/api_client_test.c api_client.c api_client.h protocol.c protocol.h net.c net.h thread_compat.c thread_compat.h config.h
	$(CC) $(CPPFLAGS) $(CFLAGS) $(THREAD_FLAGS) tests/api_client_test.c api_client.c protocol.c net.c thread_compat.c -o $@ $(LDLIBS)

test: $(PROTOCOL_TEST_TARGET) $(COLLECTOR_TEST_TARGET) $(REGISTRY_TEST_TARGET) $(API_CLIENT_TEST_TARGET)
	$(RUN_PREFIX)$(PROTOCOL_TEST_TARGET)
	$(RUN_PREFIX)$(COLLECTOR_TEST_TARGET)
	$(RUN_PREFIX)$(REGISTRY_TEST_TARGET)
	$(RUN_PREFIX)$(API_CLIENT_TEST_TARGET)

clean:
	$(CLEAN_COMMAND)
