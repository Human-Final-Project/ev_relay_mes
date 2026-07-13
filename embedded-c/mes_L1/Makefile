# 빌드 타겟 이름
TARGET = l1_simulator

# 컴파일러 및 플래그 설정
CC = gcc
CFLAGS = -Wall -Wextra -O2 -std=c11
LIBS = -lpthread

# 소스 파일 목록
SRCS = main.c net.c protocol.c
OBJS = $(SRCS:.c=.o)

# 가짜 타겟 선언 (동일 이름의 파일이 존재해도 무조건 실행 보장)
.PHONY: all clean

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LIBS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

# 헤더 파일 변경 시 자동 재컴파일을 위한 의존성 정의
main.o: main.c net.h protocol.h
net.o: net.c net.h
protocol.o: protocol.c protocol.h

clean:
	rm -f $(OBJS) $(TARGET)