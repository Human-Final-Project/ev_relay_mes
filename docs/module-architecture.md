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
   ├─ notification
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

## 현재 모듈 경계

2026-07-30 기준으로 11개 기능 모듈을 운영한다.

| 모듈 | 책임 | 대표 공개 계약 |
|---|---|---|
| `auth` | 로그인, 세션, 회원 계정 | 사용자 조회 및 인증 계약 |
| `notice` | 공지사항 작성·조회 | 공지사항 DTO와 작업 계약 |
| `notification` | 설비 이상·재고 부족 알림, 사용자별 읽음 이력 | 알림 조회·읽음 처리와 이벤트 계약 |
| `masterdata` | 품목, BOM, 공정, 검사·불량·알람 기준정보 | 기준정보 조회·관리 계약 |
| `material` | 자재 LOT 입고, 재고, 생산 투입 | 자재 재고·사용 계약 |
| `workforce` | 작업자와 설비 배정 | 작업자 조회·배정 계약 |
| `machine` | 설비 상태, 이력, 알람 | 설비 조회·상태·알람 계약 |
| `quality` | 검사 결과, 제품 판정, 불량 이력 | 검사·판정·품질 지표 계약 |
| `production` | 작업지시, LOT, 생산실적, 스케줄링 | 생산·LOT·스케줄링 계약 |
| `collector` | L2 HTTP 인증, 상태, 작업명령 전달·ACK | `CollectorApiKeyFilter`, `CollectorStatusOperations`, `WorkCommandOperations` |
| `dashboard` | 기능별 공개 API를 조합한 현황 조회 | `DashboardQuery` |

`api`에는 다른 모듈이 사용할 수 있는 계약과 그 계약에 필요한 데이터 타입만 둔다.
Controller, Service, Repository와 보안 필터 구현은 `internal`에 둔다. 현재 다른 기능
모듈이 대상 모듈의 `internal`을 참조하는 경우는 자동 경계 테스트에서 허용하지 않는다.

## 의존 규칙

1. `common`은 어떤 `feature` 모듈에도 의존하지 않는다.
2. 기능 모듈 내부에서는 자신의 `api`, `internal`, `common`을 사용할 수 있다.
3. 다른 기능 모듈을 호출할 때는 대상 모듈의 `api` 패키지만 사용한다.
4. 다른 모듈의 `internal` 패키지, Repository 또는 Entity를 직접 참조하지 않는다.
5. 모듈 간 상태 변화는 공개 Service 인터페이스 또는 이벤트로 전달한다.
6. 순환 의존성이 생기면 한쪽 호출을 이벤트로 바꾸거나 공통 계약을 `api`로 올린다.
7. 단순 재사용을 이유로 업무 로직을 `common`으로 이동하지 않는다.

기능별 `Controller`, `Service`, `Repository`, `Entity`의 이관은 완료했다. 애플리케이션
전체를 조합하는 `Config`, 공통 예외 처리와 최상위 MES 진입 Controller 등은 기능 모듈
밖에 남을 수 있지만, 기능 구현을 최상위 계층형 패키지에 새로 추가하지 않는다.

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

1. [x] 모듈 골격, 의존 규칙과 자동 경계 검사 마련
2. [x] 공지사항 모듈 이관
3. [x] 인증·사용자 모듈 이관
4. [x] 기준정보 모듈 이관
5. [x] 자재 모듈 이관
6. [x] 작업자·설비 모듈 이관
7. [x] 품질·대시보드 조회 모듈 이관
8. [x] 작업지시·LOT·생산실적 모듈 이관
9. [x] 스케줄링·작업명령·Collector와 프런트 기능 폴더 정리
10. [x] 전체 통합 시나리오, 의존성, 문서 최종 검수

각 단계가 끝날 때 백엔드, 프런트, L1, L2 테스트를 실행한다. 실패한 상태에서는 다음
단계로 넘어가지 않는다.

## Collector 인증과 작업명령 경계

- `/api/collector/**`는 일반 사용자 세션이 아니라 `X-Collector-Key` 헤더로 인증한다.
- Backend 키는 `MES_COLLECTOR_API_KEY` 환경 변수로 설정한다.
- L2는 `embedded-c/mes_collector/config.h`의 `MES_COLLECTOR_API_KEY`와 같은 값을
  사용해야 한다.
- 올바른 키는 `ROLE_COLLECTOR` 권한으로 변환되며 Collector API에만 접근한다.
- 일반 로그인 사용자는 `/api/collector/**`를 호출할 수 없다.
- 작업명령 생성·조회·배정·해제·ACK는 `WorkCommandOperations`를 통해서만 호출한다.
- 작업명령 저장소와 API 키 인증 구현은 Collector 모듈 외부에 공개하지 않는다.

통신 메시지와 HTTP 경로의 상세 계약은 [TCP 프로토콜](tcp-protocol.md), L2 설정과
실행 방법은 [L2 Collector README](../embedded-c/mes_collector/README.md)를 따른다.

## 최종 검수 기준

Windows 개발 환경에서는 다음 명령으로 전체 회귀 검사를 수행한다.

```powershell
cd backend
.\gradlew.bat cleanTest test

cd ..\frontend
$env:CI='true'
npm test -- --watchAll=false
npm run build

cd ..\embedded-c\mes_collector
mingw32-make test

cd ..\mes_L1
mingw32-make test
```

Linux/macOS에서는 Gradle Wrapper를 `./gradlew`, C 테스트를 `make test`로 실행한다.

2026-07-29 완료 기준:

- Backend: 23개 테스트 스위트, 123개 테스트 통과
- Frontend: 18개 테스트 스위트, 40개 테스트 통과 및 빌드 성공
- L2 Collector와 L1: 총 1,888개 체크 통과
- 다른 기능 모듈의 `internal` 직접 참조 0건
- 이전 계층형 기능 패키지 참조 0건

자동 경계 검사는 `ModuleBoundaryTest`가 담당한다. 기능을 추가할 때는 공개 계약을
`feature.<module>.api`에 정의하고, 구현은 `feature.<module>.internal`에 둔 다음 전체
회귀 검사를 다시 실행한다.
