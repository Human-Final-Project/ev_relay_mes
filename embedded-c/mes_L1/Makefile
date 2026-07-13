# 빌드 타겟 이름
TARGET = l1_simulator

# 컴파일러 및 플래그 설정
CC = gcc
CFLAGS = -Wall -Wextra -O2 -std=c11
LIBS = -lpthread

# 소스 파일 목록 (★ protocol.c 추가)
SRCS = main.c net.c protocol.c
OBJS = $(SRCS:.c=.o)

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LIBS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

# 헤더 파일 변경 시 자동 재컴파일을 위한 의존성 추가 (선택사항이지만 권장)
main.o: main.c net.h protocol.h
net.o: net.c net.h
protocol.o: protocol.c protocol.h

clean:
	rm -f $(OBJS) $(TARGET)