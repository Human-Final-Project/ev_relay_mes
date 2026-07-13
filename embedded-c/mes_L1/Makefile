# 빌드 타겟 이름
TARGET = l1_simulator

# 컴파일러 및 플래그 설정
CC = gcc
CFLAGS = -Wall -Wextra -O2 -std=c11

# 1. [보완] 윈도우 환경(MinGW/MSYS2)일 때 필수 네트워크 라이브러리 자동 추가
ifeq ($(OS),Windows_NT)
    LIBS = -lws2_32 -lpthread
else
    LIBS = -lpthread
endif

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

# 2. [보완] 윈도우(CMD)와 리눅스/MSYS2 셸 모두에서 에러 없이 실행되도록 크로스플랫폼 명령어로 보완
clean:
	gcc -e "int main(){}" -o .dummy 2>nul || true
	rm -f $(OBJS) $(TARGET) $(TARGET).exe