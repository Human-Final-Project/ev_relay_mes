CC := gcc

SOURCES := main.c client.c machine_runtime.c device_config.c protocol.c net.c
OBJECTS := $(SOURCES:.c=.o)

ifeq ($(OS),Windows_NT)
TARGET := l1_simulator.exe
TEST_TARGET := l1_protocol_tests.exe
CONTRACT_TEST_TARGET := l1_l2_contract_tests.exe
CLIENT_TEST_TARGET := l1_client_tests.exe
RUNTIME_TEST_TARGET := l1_machine_runtime_tests.exe
SOCKET_LIBS := -lws2_32
RUN_PREFIX := ./
define CLEAN_COMMAND
	powershell.exe -NoProfile -Command "Remove-Item -Force -ErrorAction SilentlyContinue *.o,$(TARGET),$(TEST_TARGET),$(CONTRACT_TEST_TARGET),$(CLIENT_TEST_TARGET),$(RUNTIME_TEST_TARGET); exit 0"
endef
else
TARGET := l1_simulator
TEST_TARGET := l1_protocol_tests
CONTRACT_TEST_TARGET := l1_l2_contract_tests
CLIENT_TEST_TARGET := l1_client_tests
RUNTIME_TEST_TARGET := l1_machine_runtime_tests
SOCKET_LIBS :=
RUN_PREFIX := ./
define CLEAN_COMMAND
	$(RM) $(OBJECTS) $(TARGET) $(TEST_TARGET) $(CONTRACT_TEST_TARGET) $(CLIENT_TEST_TARGET) $(RUNTIME_TEST_TARGET)
endef
endif

CPPFLAGS := -I.
CFLAGS := -std=c11 -Wall -Wextra -Wpedantic
LDLIBS := $(SOCKET_LIBS)

.PHONY: all clean list run test

all: $(TARGET)

$(TARGET): $(OBJECTS)
	$(CC) $(OBJECTS) -o $@ $(LDLIBS)

%.o: %.c
	$(CC) $(CPPFLAGS) $(CFLAGS) -c $< -o $@

$(TEST_TARGET): tests/protocol_test.c protocol.c protocol.h device_config.c device_config.h
	$(CC) $(CPPFLAGS) $(CFLAGS) tests/protocol_test.c protocol.c device_config.c -o $@

$(CONTRACT_TEST_TARGET): tests/l2_contract_test.c machine_runtime.c machine_runtime.h protocol.c protocol.h device_config.c device_config.h ../mes_collector/protocol.c ../mes_collector/protocol.h ../mes_collector/config.h
	$(CC) $(CPPFLAGS) -I../mes_collector $(CFLAGS) tests/l2_contract_test.c machine_runtime.c protocol.c device_config.c ../mes_collector/protocol.c -o $@

$(CLIENT_TEST_TARGET): tests/client_test.c client.c client.h machine_runtime.c machine_runtime.h config.h protocol.c protocol.h device_config.c device_config.h net.c net.h
	$(CC) $(CPPFLAGS) $(CFLAGS) tests/client_test.c client.c machine_runtime.c protocol.c device_config.c net.c -o $@ $(LDLIBS)

$(RUNTIME_TEST_TARGET): tests/machine_runtime_test.c machine_runtime.c machine_runtime.h protocol.h device_config.c device_config.h
	$(CC) $(CPPFLAGS) $(CFLAGS) tests/machine_runtime_test.c machine_runtime.c device_config.c -o $@

test: $(TEST_TARGET) $(CONTRACT_TEST_TARGET) $(CLIENT_TEST_TARGET) $(RUNTIME_TEST_TARGET)
	$(RUN_PREFIX)$(TEST_TARGET)
	$(RUN_PREFIX)$(CONTRACT_TEST_TARGET)
	$(RUN_PREFIX)$(CLIENT_TEST_TARGET)
	$(RUN_PREFIX)$(RUNTIME_TEST_TARGET)

list: $(TARGET)
	$(RUN_PREFIX)$(TARGET) --list

run: $(TARGET)
	$(RUN_PREFIX)$(TARGET) EQ-WIND-01

clean:
	$(CLEAN_COMMAND)
