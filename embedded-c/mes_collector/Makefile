CC := gcc

SOURCES := main.c collector.c scheduler.c api_client.c protocol.c net.c
OBJECTS := $(SOURCES:.c=.o)

ifeq ($(OS),Windows_NT)
TARGET := mes_collector.exe
PROTOCOL_TEST_TARGET := protocol_tests.exe
COLLECTOR_TEST_TARGET := collector_tests.exe
SOCKET_LIBS := -lws2_32
RUN_PREFIX := ./
THREAD_FLAGS :=
define CLEAN_COMMAND
	powershell.exe -NoProfile -Command "Remove-Item -Force -ErrorAction SilentlyContinue *.o,$(TARGET),$(PROTOCOL_TEST_TARGET),$(COLLECTOR_TEST_TARGET); exit 0"
endef
else
TARGET := mes_collector
PROTOCOL_TEST_TARGET := protocol_tests
COLLECTOR_TEST_TARGET := collector_tests
SOCKET_LIBS :=
RUN_PREFIX := ./
THREAD_FLAGS ?= -pthread
define CLEAN_COMMAND
	$(RM) $(OBJECTS) $(TARGET) $(PROTOCOL_TEST_TARGET) $(COLLECTOR_TEST_TARGET)
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

$(COLLECTOR_TEST_TARGET): tests/collector_test.c collector.c collector.h protocol.c protocol.h net.c net.h config.h
	$(CC) $(CPPFLAGS) $(CFLAGS) tests/collector_test.c collector.c protocol.c net.c -o $@ $(SOCKET_LIBS)

test: $(PROTOCOL_TEST_TARGET) $(COLLECTOR_TEST_TARGET)
	$(RUN_PREFIX)$(PROTOCOL_TEST_TARGET)
	$(RUN_PREFIX)$(COLLECTOR_TEST_TARGET)

clean:
	$(CLEAN_COMMAND)
