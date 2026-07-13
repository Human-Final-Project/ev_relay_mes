CC := gcc

SOURCES := main.c collector.c scheduler.c api_client.c protocol.c net.c
OBJECTS := $(SOURCES:.c=.o)

ifeq ($(OS),Windows_NT)
TARGET := mes_collector.exe
TEST_TARGET := protocol_tests.exe
TEST_RUN := .\$(TEST_TARGET)
THREAD_FLAGS :=
define CLEAN_COMMAND
	powershell.exe -NoProfile -Command "Remove-Item -Force -ErrorAction SilentlyContinue *.o,$(TARGET),$(TEST_TARGET); exit 0"
endef
else
TARGET := mes_collector
TEST_TARGET := protocol_tests
TEST_RUN := ./$(TEST_TARGET)
THREAD_FLAGS ?= -pthread
define CLEAN_COMMAND
	$(RM) $(OBJECTS) $(TARGET) $(TEST_TARGET)
endef
endif

CPPFLAGS := -I.
CFLAGS := -std=c11 -Wall -Wextra -Wpedantic
LDLIBS := $(THREAD_FLAGS)

.PHONY: all clean run test

all: $(TARGET)

$(TARGET): $(OBJECTS)
	$(CC) $(OBJECTS) -o $@ $(LDLIBS)

%.o: %.c
	$(CC) $(CPPFLAGS) $(CFLAGS) $(THREAD_FLAGS) -c $< -o $@

run: $(TARGET)
	./$(TARGET)

$(TEST_TARGET): tests/protocol_test.c protocol.c protocol.h config.h
	$(CC) $(CPPFLAGS) $(CFLAGS) tests/protocol_test.c protocol.c -o $@

test: $(TEST_TARGET)
	$(TEST_RUN)

clean:
	$(CLEAN_COMMAND)
