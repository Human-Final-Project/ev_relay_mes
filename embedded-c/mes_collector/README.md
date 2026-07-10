# L2 MES Collector

L1 설비 시뮬레이터의 TCP 메시지를 수집하고 Spring Boot MES에 REST API로 전달하는 C 프로그램이다.

현재 단계는 모듈과 빌드 구성을 확인하기 위한 기본 프로젝트 골격이다. 실제 기능은 아래 순서로 구현한다.

1. 프로토콜 파서
2. 단일 L1 TCP 연결
3. 연결당 `pthread`를 사용하는 다중 L1 수집
4. Backend REST API 전송
5. 재연결, Timeout 및 오류 처리

통신 규칙은 [`../../docs/tcp-protocol.md`](../../docs/tcp-protocol.md)를 따른다.

## Build

Linux 또는 pthread를 지원하는 MinGW 환경에서 빌드한다.

```bash
make
./mes_collector
```
