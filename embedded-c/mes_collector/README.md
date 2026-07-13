# L2 MES Collector

L1 설비 시뮬레이터의 TCP 메시지를 수집하고 Spring Boot MES에 REST API로 전달하는 C 프로그램이다.

기본 프로젝트 구성과 TCP 프로토콜 파서가 구현되어 있다. 이후 기능은 아래 순서로 구현한다.

1. 단일 L1 TCP 연결
2. 연결당 `pthread`를 사용하는 다중 L1 수집
3. Backend REST API 전송
4. 재연결, Timeout 및 오류 처리

생산 흐름은 OP20과 OP30이 병렬로 실행된 후 OP40_OP50에서 합류한다. L2는 두 공정 결과를 각각 독립적으로 Backend에 전달하며 조립 가능 수량은 계산하지 않는다. 공정 완료 `PRODUCTION`은 항상 전달하고, `DEFECT`와 `ALARM`은 발생할 때만 전달한다. `HELLO`와 `HEARTBEAT`는 L2 내부 연결 관리에만 사용한다.

작업명령은 L2가 Backend REST API를 1초마다 Polling하여 가져오고, 기존 L1 TCP 연결로 `START`, `STOP`, `RESUME` 명령을 전달한다. MVP는 정상 네트워크를 가정하므로 Backend HTTP 요청은 자동 재시도하지 않는다.

통신 규칙은 [`../../docs/tcp-protocol.md`](../../docs/tcp-protocol.md)를 따른다.

## Build

Linux 또는 pthread를 지원하는 MinGW 환경에서 빌드한다.

```bash
make
./mes_collector
```

프로토콜 단위 테스트는 다음 명령으로 실행한다.

```bash
make test
```
