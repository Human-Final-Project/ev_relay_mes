# EV Relay MES 모듈화 기준

## 목적

이 문서는 기존 기능, REST API, DB 스키마를 유지하면서 계층형 패키지를 기능 중심
모듈형 모놀리스로 옮기기 위한 기준을 정의한다. 모듈화 작업은 배포 단위를 나누는
마이크로서비스 전환이 아니라, 하나의 Spring Boot 애플리케이션 안에서 변경 영향
범위를 줄이는 작업이다.

## 목표 구조

```text
com.human.ev_relay_mes
├─ common
└─ feature
   ├─ auth
   ├─ notice
   ├─ masterdata
   ├─ material
   ├─ workforce
   ├─ machine
   ├─ quality
   ├─ production
   ├─ collector
   └─ dashboard
```

각 기능 모듈은 다음 하위 패키지를 사용한다.

```text
feature.<module>
├─ api       # 다른 모듈에 공개하는 DTO, 조회 인터페이스, 이벤트
└─ internal  # Controller, Service, Repository, Entity와 내부 구현
```

모듈 루트의 `package-info.java`는 모듈의 책임과 공개 경계를 기록한다.

## 의존 규칙

1. `common`은 어떤 `feature` 모듈에도 의존하지 않는다.
2. 기능 모듈 내부에서는 자신의 `api`, `internal`, `common`을 사용할 수 있다.
3. 다른 기능 모듈을 호출할 때는 대상 모듈의 `api` 패키지만 사용한다.
4. 다른 모듈의 `internal` 패키지, Repository 또는 Entity를 직접 참조하지 않는다.
5. 모듈 간 상태 변화는 공개 Service 인터페이스 또는 이벤트로 전달한다.
6. 순환 의존성이 생기면 한쪽 호출을 이벤트로 바꾸거나 공통 계약을 `api`로 올린다.
7. 단순 재사용을 이유로 업무 로직을 `common`으로 이동하지 않는다.

`Controller`, `Service`, `Repository`, `Entity` 등 기존 계층형 패키지는 단계적 이관이
끝날 때까지 임시 레거시 영역으로 유지한다. 새 기능 코드는 이 영역에 추가하지 않는다.

## 호환성 규칙

각 이관 단계에서는 다음 계약을 변경하지 않는다.

- REST API 경로, HTTP 메서드와 상태 코드
- 요청·응답 JSON 필드
- DB 테이블, 컬럼, enum 저장값과 연관관계
- Spring Security 권한 및 CSRF/API Key 경계
- L1/L2 TCP 메시지와 Collector HTTP 계약
- React 라우트와 사용자 화면 동작

파일 이동과 업무 로직 변경은 같은 작업 단위에서 수행하지 않는다. 구조 이동 후 전체
테스트가 통과한 다음에만 내부 구현을 별도로 개선한다.

## 안전한 10단계

1. 모듈 골격, 의존 규칙과 자동 경계 검사 마련
2. 공지사항 모듈 이관
3. 인증·사용자 모듈 이관
4. 기준정보 모듈 이관
5. 자재 모듈 이관
6. 작업자·설비 모듈 이관
7. 품질·대시보드 조회 모듈 이관
8. 작업지시·LOT·생산실적 모듈 이관
9. 스케줄링·작업명령·Collector와 프런트 기능 폴더 정리
10. 전체 통합 시나리오, 의존성, 문서 최종 검수

각 단계가 끝날 때 백엔드, 프런트, L1, L2 테스트를 실행한다. 실패한 상태에서는 다음
단계로 넘어가지 않는다.
