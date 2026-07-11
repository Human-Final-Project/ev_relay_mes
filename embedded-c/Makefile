# 빌드 타겟 이름
TARGET = l1_simulator

# 컴파일러 및 플래그 설정
CC = gcc
CFLAGS = -Wall -Wextra -O2 -std=c11
LIBS = -lpthread

# 소스 파일 목록
SRCS = main.c net.c
OBJS = $(SRCS:.c=.o)

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LIBS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)