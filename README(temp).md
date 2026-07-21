# EV Relay Mini MES — Stitch용 React 화면 생성 명세

> 이 문서는 현재 프로젝트의 `backend`와 `embedded-c` 구현을 기준으로 React UI를 생성하기 위한 명세다.
> 기존 `frontend`의 화면 구조나 mock 데이터는 기준으로 삼지 않는다.
> 목표는 실제 Spring Boot API 및 C 기반 L1/L2 생산 흐름을 정확하게 표현하는 데 있다.

## 1. Stitch에 전달할 핵심 요청

EV Relay 생산라인을 관리하는 데스크톱 중심의 Mini MES 웹 애플리케이션을 설계한다.

- React 기반의 반응형 SPA로 만든다.
- 기본 언어는 한국어이며 설비 ID, 공정 코드, 상태 코드는 영문 원문을 함께 표시한다.
- 제조 현장에서 멀리서도 상태를 빠르게 파악할 수 있는 고밀도 운영 대시보드 형태로 만든다.
- 화려한 마케팅 페이지가 아니라 실제 생산관리 시스템처럼 명확하고 절제된 UI를 사용한다.
- 좌측 고정 사이드바, 상단 헤더, 본문 콘텐츠 구조를 사용한다.
- 모든 목록 화면에 로딩, 빈 데이터, 오류, 재시도 상태를 설계한다.
- 모든 저장·상태 변경·삭제 작업에는 성공/실패 피드백을 표시한다.
- 백엔드에 없는 근태, 공지, 교육, 인수인계, 채팅 기능은 만들지 않는다.
- L1/L2 Collector API는 브라우저에서 호출하지 않는다. 화면은 MES 조회/관리 API만 사용한다.

## 2. 제품과 시스템 배경

생산 대상은 EV Relay이며 전체 시스템은 다음과 같다.

```text
[L1 C 설비 시뮬레이터 6대]
          │ TCP/CSV, 장기 연결
          ▼
[L2 C 수집기]
          │ HTTP REST/JSON
          ▼
[Spring Boot MES Backend]
          │ JPA
          ▼
[MySQL]
          ▲
          │ REST API
[React Frontend]
```

구성 요소별 역할:

- L1: 실제 설비 역할을 하는 C 시뮬레이터. 생산, 검사, 불량, 알람, 설비 상태 이벤트를 전송한다.
- L2: 최대 6대의 L1 TCP 연결을 관리하고 메시지를 검증한 뒤 Backend로 전달한다.
- Backend: 작업지시, LOT, BOM, 생산 실적, 검사, 불량, 설비, 작업자를 저장하고 다음 공정 명령을 생성한다.
- Frontend: Backend에 저장된 현재 상태와 이력을 조회하고 관리 작업을 수행한다.

## 3. 공정과 설비의 고정 매핑

이 매핑은 화면 전체에서 동일하게 사용한다.

| 순서 | 공정 코드 | 공정명 | 설비 ID | 대표 오류 코드 |
|---:|---|---|---|---|
| 1A | `OP20` | 코일 권선 | `EQ-WIND-01` | `WIRE_BREAK` |
| 1B | `OP30` | 접점 가공/용접 | `EQ-WELD-01` | `WELD_POWER_ERROR` |
| 2 | `OP40_OP50` | 자동 조립 | `EQ-ASSY-01` | `ASSEMBLY_JAM` |
| 3 | `OP60` | 실링/가스충전 | `EQ-SEAL-01` | `CHAMBER_PRESSURE_ERROR` |
| 4 | `OP70` | 최종 검사 | `EQ-TEST-01` | `TEST_PROBE_ERROR` |
| 5 | `OP80` | 마킹/포장 | `EQ-PACK-01` | `LABEL_PRINTER_ERROR` |

공정 흐름은 반드시 다음 구조로 시각화한다.

```text
OP20 코일 권선 ───────┐
                      ├─→ OP40_OP50 자동 조립 → OP60 실링 → OP70 검사 → OP80 포장
OP30 접점 용접 ───────┘
```

- OP20과 OP30은 병렬 선행 공정이다.
- 두 공정의 결과는 OP40_OP50에서 합류한다.
- 기본 조립 가능 수량은 `min(OP20 okQty, OP30 okQty)`이다.
- OP40_OP50 이후 다음 공정의 투입 수량은 이전 공정의 OK 수량이다.
- 최종 완제품 수량은 OP80의 OK 수량이다.
- OP70은 제품 단위 검사 결과를 생성하며, 일반 생산 공정과 다르게 측정값을 중심으로 보여준다.

## 4. 공통 디자인 시스템

### 4.1 시각 방향

- 분위기: 정밀 제조, 산업 제어, 신뢰성, 실시간 모니터링
- 배경: 밝은 회색 또는 옅은 청회색
- 카드: 흰색, 얇은 회색 테두리, 작은 그림자, 8~12px 모서리
- 기본 강조색: 짙은 네이비와 산업용 블루
- 숫자: 표와 KPI에서 정렬이 잘 되는 tabular 숫자 사용
- 아이콘: 라인 스타일의 단순한 산업/설비 아이콘
- 지나친 그라디언트, 유리 효과, 네온 효과는 사용하지 않는다.

권장 색상:

| 용도 | 색상 |
|---|---|
| Primary | `#075985` 또는 `#0369A1` |
| Page background | `#F1F5F9` |
| Card background | `#FFFFFF` |
| Main text | `#0F172A` |
| Secondary text | `#64748B` |
| Border | `#CBD5E1` |
| Success / 정상 | `#16A34A` |
| Warning / 주의 | `#D97706` |
| Error / 이상 | `#DC2626` |
| Info / 진행 | `#2563EB` |
| Inactive | `#64748B` |

### 4.2 레이아웃

- 데스크톱 기준 최소 폭은 1280px, 주요 설계 폭은 1440px로 한다.
- 사이드바 폭은 약 240px이며 축소 상태도 고려한다.
- 상단 헤더에는 현재 페이지명, 최종 갱신 시각, 새로고침, 사용자 메뉴를 배치한다.
- 본문 최대 폭을 지나치게 제한하지 말고 대시보드와 데이터 테이블이 넓게 보이게 한다.
- 태블릿에서는 사이드바를 아이콘 형태로 접고, 모바일에서는 드로어로 전환한다.
- 데이터 테이블은 모바일에서 중요한 열만 유지하고 상세 정보는 행 확장 또는 상세 패널로 보여준다.

### 4.3 상태 배지

영문 원본 상태는 데이터와 API 요청에 그대로 사용하고, 화면에는 한국어를 함께 표시한다.

설비 상태:

| 코드 | 표시 | 색상 |
|---|---|---|
| `IDLE` | 대기 | 회색/청회색 |
| `RUNNING` | 가동 중 | 초록 |
| `ERROR` | 이상 | 빨강 |
| `STOPPED` | 정지 | 주황 |

작업지시 상태:

| 코드 | 표시 |
|---|---|
| `CREATED` | 생성 |
| `RELEASED` | 출고/실행 준비 |
| `RUNNING` | 진행 중 |
| `COMPLETED` | 완료 |
| `CANCELED` | 취소 |

LOT 상태:

| 코드 | 표시 |
|---|---|
| `WAITING` | 대기 |
| `RUNNING` | 진행 중 |
| `COMPLETED` | 완료 |
| `HOLD` | 보류 |
| `SCRAPPED` | 폐기 |

작업명령 상태:

| 코드 | 표시 |
|---|---|
| `PENDING` | 전송 대기 |
| `DISPATCHED` | 전송됨 |
| `ACCEPTED` | 설비 수락 |
| `REJECTED` | 설비 거부 |
| `COMPLETED` | 실행 완료 |
| `CANCELED` | 취소 |

기타 상태:

- 검사 결과: `OK`, `NG`
- 자재 LOT: `AVAILABLE`, `HOLD`, `USED`, `DISCARDED`
- 품목 유형: `RM` 원자재, `SA` 반제품, `FG` 완제품
- LOT 유형: `INITIAL` 최초 생산, `SUPPLEMENT` 보충 생산
- 사용자 역할: `ADMIN`, `MANAGER`, `OPERATOR`, `VIEWER`
- 사용자 상태: `ACTIVE`, `LOCKED`, `RETIRED`

## 5. 전체 내비게이션

사이드바 메뉴는 다음 순서로 구성한다.

1. 대시보드
2. 작업지시
3. LOT 추적
4. 생산 실적
5. 설비 현황
6. 품질 관리
7. 자재 LOT
8. 기준정보
   - 품목
   - BOM
   - 공정
   - 검사 기준
9. 작업자 배정
10. 사용자 관리 — ADMIN만 표시

상단 또는 사이드바 하단에는 다음 시스템 연결 정보를 간결하게 보여준다.

- MES API: 연결됨/연결 실패
- L2 Collector: 직접 연결 여부를 추정하지 말고 설비 상태 및 최근 상태 시각으로 표현
- 사용자 이름과 역할
- 로그아웃

## 6. 화면별 상세 요구사항

### 6.1 로그인

목적: Spring Security 세션 로그인을 수행한다.

구성:

- 서비스명 `EV Relay Mini MES`
- 로그인 ID 입력
- 비밀번호 입력
- 로그인 버튼
- 로그인 처리 중 상태
- 401 오류 메시지
- 세션 만료 안내

동작:

1. CSRF 토큰을 조회한다.
2. 로그인 요청을 보낸다.
3. 응답의 `memberId`, `loginId`, `memberName`, `role`, `status`를 앱 사용자 상태에 저장한다.
4. 로그인 후 대시보드로 이동한다.
5. 페이지 새로고침 시 `/api/auth/me`로 세션을 복원한다.

### 6.2 대시보드

첫 화면에서 생산라인 전체 상태를 5초 또는 10초 간격으로 갱신한다.

상단 KPI 카드:

- 오늘 완료 LOT 수: `production.completedLots`
- 오늘 생산 OK: `production.okQty`
- 오늘 생산 NG: `production.ngQty`
- 진행 중 작업지시: `workOrders.running`
- 가동 중 설비: `machines.running / machines.total`
- 활성 알람: `alarms.active`
- 재고 부족 품목: `materials.lowStockItemCount`

중앙 영역:

- 6개 공정을 실제 순서대로 보여주는 생산 흐름 보드
- OP20과 OP30은 나란히 표시하고 OP40_OP50에서 합류시키기
- 각 공정 카드에 설비명, 상태, 현재 LOT, 최근 갱신 시각 표시
- `RUNNING` 공정은 진행 강조, `ERROR` 공정은 강한 경고 강조

하단 영역:

- 작업지시 상태 분포: created/released/running/completed/canceled
- 설비 상태 분포: idle/running/error/stopped
- 오늘 검사 OK/NG
- 최근 생산 실적 테이블
- 미해제 알람 목록
- 자재 요약: 가용 품목 수, 총 가용 수량, HOLD 수량, 부족 기준값

### 6.3 작업지시

목록 열:

- 작업지시 ID
- 작업지시 번호 `orderNo`
- 품목 코드/명 `itemCode`, `itemName`
- 목표 수량 `targetQty`
- 완료 OK 수량 `completedOkQty`
- 잔여 수량 `remainingQty`
- 보충 생산 필요 `supplementRequired`
- 상태
- 계획 시작/종료
- 생성자
- 생성 시각

기능:

- 상태 필터
- 품목/작업지시 번호 검색
- 작업지시 생성
- 수정
- 상태 변경
- 삭제 전 확인
- 작업지시에서 최초 LOT 생성
- 불량 부족분이 있을 때 보충 LOT 생성

생성 폼:

- 품목 코드
- 목표 수량
- 계획 시작 시각
- 계획 종료 시각

LOT 생성 폼:

- 투입 수량 `inputQty`

### 6.4 LOT 추적

목록 열:

- LOT ID와 LOT 번호
- 작업지시 번호
- 품목 코드/명
- LOT 유형 `INITIAL`/`SUPPLEMENT`
- 생산 차수 `productionRound`
- 투입/OK/NG 수량
- 현재 공정
- 상태
- 시작 요청/시작/완료 시각
- 생성자

검색과 필터:

- LOT 번호
- 상태
- 작업지시 ID
- 품목

상세는 우측 드로어 또는 별도 상세 페이지로 구성한다.

LOT 상세 탭:

1. 개요
2. 공정 진행
3. 작업명령
4. 생산 실적
5. 검사 결과
6. 불량 이력
7. 담당 작업자

공정 진행 탭은 OP20/OP30 병렬 분기를 명확히 표현하고 각 공정에 다음을 표시한다.

- 공정 코드/명
- 설비 ID/명
- 상태
- 투입/OK/NG 누적 수량
- 시작/종료 시각
- 오류나 중단 시 부분 실적 여러 건

작업명령 타임라인:

- 명령 ID
- `START`, `STOP`, `RESUME`
- 대상 설비/공정
- 투입 수량
- 상태
- ACK 메시지
- 생성/전송/수락/완료 시각

### 6.5 생산 실적

검색 조건:

- LOT 번호
- 공정 코드
- 설비 ID
- 상태
- 시작/종료 날짜와 시각

테이블 열:

- 생산 로그 ID
- LOT 번호
- 설비 ID/명
- 공정 코드/명
- 투입 수량
- OK 수량
- NG 수량
- 양품률
- 상태
- 시작/종료/수집 시각

규칙:

- `inputQty = okQty + ngQty` 관계가 한눈에 보이게 한다.
- 정상 공정은 `COMPLETED` 한 건이지만 오류/정지 후 재개한 공정은 `RUNNING` 부분 실적과 `COMPLETED` 실적이 함께 존재할 수 있다.
- 같은 LOT·공정의 여러 행을 합산해 보여주는 그룹 보기와 원본 이벤트 보기 전환을 제공한다.
- OP80 OK 수량을 최종 완제품 수량으로 강조한다.

### 6.6 설비 현황

기본 화면은 6개 설비 카드와 목록 보기 전환을 제공한다.

설비 카드:

- 설비 ID/명
- 설비 타입
- 담당 공정
- 현재 상태
- 현재 LOT
- 최근 상태 변경 시각
- 담당 작업자
- 상세 보기 버튼

설비 상세:

- 상태 이력 타임라인
- 최근 생산 실적
- 현재 및 과거 알람
- 담당자/작업자 배정

알람 목록 열:

- 알람 이력 ID
- 설비 ID/명
- 알람 코드/한글명
- 알람 레벨
- 메시지
- 발생 시각
- 해제 여부
- 해제 시각/해제자

미해제 알람에는 권한이 있는 사용자용 `알람 해제` 버튼을 제공한다.

통신 장애 코드도 제품 설비 오류와 함께 표시한다.

- `COMM_TIMEOUT`: 15초 동안 정상 메시지 없음
- `COMM_DISCONNECTED`: TCP 연결 종료 또는 통신 오류

### 6.7 품질 관리

상단 탭:

1. 검사 결과
2. 불량 이력
3. 검사 기준

검사 결과 검색:

- LOT 번호
- 설비 ID
- 공정 코드
- 결과 OK/NG
- 검사 시간 범위

검사 결과 열:

- 검사 ID
- LOT 번호
- 제품 순번 `unitSeq`
- 설비/공정
- 검사 항목
- 측정값/단위
- 기준 하한/상한
- 기준 버전
- 결과
- 검사 시각

OP70 대표 검사 항목:

- `OPERATION_VOLTAGE` / V
- `COIL_RESISTANCE` / OHM
- `CONTACT_RESISTANCE` / mOHM

표현 방식:

- 기준 범위 안의 값은 초록, 범위 밖은 빨강으로 표시한다.
- 동일 LOT와 `unitSeq`의 세 검사 항목을 묶어서 볼 수 있게 한다.
- NG 행은 불량 원인 확인으로 연결한다.

불량 이력 열:

- 불량 이력 ID
- LOT 번호
- 설비/공정
- 불량 코드/명
- 불량 수량
- 메시지
- 발생 시각

### 6.8 자재 LOT

목록 열:

- 자재 LOT ID/번호
- 품목 코드/명
- 입고 수량
- 현재 수량
- 상태
- 입고 처리자
- 입고 시각

기능:

- 자재 LOT 등록
- 상태 변경: AVAILABLE/HOLD/USED/DISCARDED
- 상세 조회
- 삭제 전 확인
- 품목별 가용 재고 합계 표시
- 가용 수량이 10 이하인 품목을 부족 상태로 강조

### 6.9 기준정보

하나의 화면에 탭 또는 하위 메뉴로 구성한다.

품목:

- 품목 코드, 품목명, 유형 RM/SA/FG, 사용 여부
- 생성, 조회, 수정, 활성/비활성, 삭제

BOM:

- BOM ID
- 상위 품목 코드
- 하위 품목 코드
- 필요 수량
- 적용 공정 코드
- 사용 여부
- 제품 또는 반제품을 선택하면 하위 구성품을 계층 또는 테이블로 표시

공정:

- 공정 코드, 공정명, 순서, 설명
- OP20/OP30 병렬 관계를 일반 순번만으로 왜곡하지 않는다.

검사 기준:

- 기준 ID
- 공정 코드/명
- 검사 항목
- 품목명
- 단위
- 하한/상한
- 버전
- 수정 시각

### 6.10 작업자 배정

작업자 목록:

- 작업자 ID
- 사번
- 이름
- 부서
- 직급
- ACTIVE/INACTIVE

설비별 배정 정보:

- 배정 ID
- 설비/공정
- 작업자 정보
- 역할
- 배정 시각

기능:

- 설비 책임자 지정
- 추가 작업자 배정
- 배정 해제
- 설비 카드에서 현재 책임자 확인

### 6.11 사용자 관리

ADMIN 전용 화면이다.

목록 열:

- 사용자 ID
- 로그인 ID
- 이름
- 역할
- 상태
- 부서
- 직급
- 생성자
- 생성/수정 시각

기능:

- 사용자 등록
- 역할/상태/부서/직급 수정
- 임시 비밀번호 초기화
- 초기화 결과는 한 번만 노출하고 복사 버튼을 제공

## 7. 사용자 권한에 따른 화면 동작

| 역할 | 주요 권한 |
|---|---|
| `ADMIN` | 전체 관리, 사용자 관리, 기준정보 관리, 알람 해제 |
| `MANAGER` | 생산·작업지시·기준정보 관리, 주요 조회 |
| `OPERATOR` | 생산/자재/LOT 관련 허용 작업과 조회 |
| `VIEWER` | 조회 중심 |

- 허용되지 않은 버튼은 숨기거나 disabled 처리하고 이유를 툴팁으로 제공한다.
- 프론트 권한 처리는 편의 기능이며 실제 권한 판정은 Backend 응답을 따른다.
- 401은 로그인 화면으로 이동하고, 403은 권한 부족 안내를 보여준다.

## 8. Frontend가 사용할 Backend API

개발 기본 주소는 `http://localhost:8111`이다. 세션 쿠키를 사용하므로 요청에 credentials를 포함한다.

### 8.1 인증

| Method | Path | 용도 |
|---|---|---|
| GET | `/api/auth/csrf` | CSRF 토큰 조회 |
| POST | `/api/auth/login` | 로그인 |
| GET | `/api/auth/me` | 현재 사용자/세션 복원 |
| POST | `/api/auth/logout` | 로그아웃 |
| PATCH | `/api/auth/password` | 본인 비밀번호 변경 후 로그아웃 |

로그인 요청:

```json
{
  "loginId": "admin",
  "password": "password"
}
```

### 8.2 대시보드

| Method | Path | 용도 |
|---|---|---|
| GET | `/api/mes/dashboard/summary` | KPI 요약 |
| GET | `/api/mes/production/recent-logs` | 최근 생산 로그 |

### 8.3 작업지시와 LOT

| Method | Path | 용도 |
|---|---|---|
| GET/POST | `/api/work-orders` | 목록/생성 |
| GET/PUT/DELETE | `/api/work-orders/{id}` | 상세/수정/삭제 |
| PATCH | `/api/work-orders/{id}/status` | 상태 변경 |
| POST | `/api/work-orders/{id}/lots` | 최초 LOT 생성 |
| POST | `/api/work-orders/{id}/supplement` | 보충 LOT 생성 |
| GET | `/api/lots?status=&workOrderId=` | LOT 검색 |
| GET | `/api/lots/{id}` | LOT 상세 |
| PATCH | `/api/lots/{id}/status` | LOT 상태 변경 |
| DELETE | `/api/lots/{id}` | LOT 삭제 |
| GET | `/api/lots/by-no/{lotNo}` | LOT 번호 조회 |
| GET | `/api/lots/by-no/{lotNo}/commands` | LOT 작업명령 조회 |
| GET | `/api/lots/by-no/{lotNo}/responsibles` | LOT 공정 담당자 조회 |

### 8.4 생산과 품질

| Method | Path | 용도 |
|---|---|---|
| GET | `/api/production-logs` | 생산 로그 조건 검색 |
| GET | `/api/production-logs/{id}` | 생산 로그 상세 |
| GET | `/api/quality/inspections` | 검사 결과 검색 |
| GET | `/api/quality/defects` | 불량 이력 검색 |
| GET | `/api/inspection-standards` | 검사 기준 목록 |
| PATCH | `/api/inspection-standards/{standardId}/limits` | 검사 상하한 수정 |

검색 쿼리는 빈 값이면 전송하지 않는다.

- 생산: `lotNo`, `processCode`, `machineId`, `status`, `startAt`, `endAt`
- 검사: `lotNo`, `machineId`, `processCode`, `result`, `startAt`, `endAt`
- 불량: `lotNo`, `machineId`, `processCode`, `defectCode`, `startAt`, `endAt`

### 8.5 설비와 알람

| Method | Path | 용도 |
|---|---|---|
| GET | `/api/machines` | 설비 목록 |
| GET | `/api/machines/{id}` | 설비 상세 |
| GET | `/api/machines/{id}/status-history` | 상태 이력 |
| GET | `/api/machines/alarms` | 알람 검색 |
| PATCH | `/api/machines/alarms/{id}/clear` | 알람 해제 |
| GET | `/api/machines/{machineId}/assignments` | 작업자 배정 조회 |
| PUT | `/api/machines/{machineId}/responsible` | 책임자 지정 |
| POST | `/api/machines/{machineId}/workers` | 작업자 추가 배정 |
| DELETE | `/api/machines/{machineId}/assignments/{workerId}` | 배정 해제 |

알람 검색 쿼리: `machineId`, `alarmCode`, `alarmLevel`, `cleared`, `startAt`, `endAt`.

### 8.6 자재와 기준정보

| Method | Path | 용도 |
|---|---|---|
| GET/POST | `/api/material-lots` | 자재 LOT 목록/등록 |
| GET/DELETE | `/api/material-lots/{id}` | 상세/삭제 |
| PATCH | `/api/material-lots/{id}/status?status=AVAILABLE` | 상태 변경 |
| GET/POST | `/api/items` | 품목 목록/등록 |
| GET/PUT/DELETE | `/api/items/{code}` | 품목 상세/수정/삭제 |
| PATCH | `/api/items/{code}/active?active=true` | 품목 활성 상태 변경 |
| GET/POST | `/api/boms` | BOM 목록/등록 |
| GET | `/api/boms/parent/{itemCode}` | 상위 품목별 BOM |
| PUT/DELETE | `/api/boms/{id}` | BOM 수정/삭제 |
| PATCH | `/api/boms/{id}/active?active=true` | BOM 활성 상태 변경 |
| GET | `/api/processes` | 공정 목록 |
| GET | `/api/processes/{code}` | 공정 상세 |
| GET | `/api/defect-codes` | 불량 코드 목록 |
| GET | `/api/alarm-codes` | 알람 코드 목록 |

### 8.7 작업자와 사용자

| Method | Path | 용도 |
|---|---|---|
| GET/POST | `/api/workers` | 작업자 목록/등록 |
| GET/PUT | `/api/workers/{id}` | 작업자 상세/수정 |
| PATCH | `/api/workers/{id}/active?active=true` | 작업자 활성 상태 변경 |
| GET/POST | `/api/members` | 사용자 목록/등록 |
| GET/PATCH | `/api/members/{id}` | 사용자 상세/수정 |
| PATCH | `/api/members/{id}/password-reset` | 임시 비밀번호 발급 |

## 9. 브라우저에서 호출하면 안 되는 Collector API

다음 API는 L2 C 수집기 전용이다. React 화면의 API 서비스에 넣지 않는다.

- `POST /api/collector/production-logs`
- `POST /api/collector/inspections`
- `POST /api/collector/defects`
- `POST /api/collector/machine-statuses`
- `POST /api/collector/machine-alarms`
- `GET /api/collector/commands/pending`
- `POST /api/collector/commands/{commandId}/release`
- `POST /api/collector/command-acks`

화면은 Collector 이벤트를 직접 생성하지 않고, 저장된 결과를 MES 조회 API를 통해서만 확인한다.

## 10. C 통신 상태를 UI에 반영하는 규칙

L1/L2 통신 규칙:

- L1은 연결 직후 5초 안에 `HELLO`를 보낸다.
- L1은 5초마다 `HEARTBEAT`를 보낸다.
- L2는 마지막 정상 메시지 후 15초가 지나면 `COMM_TIMEOUT`을 생성한다.
- 연결이 끊어지면 `COMM_DISCONNECTED`를 생성한다.
- L2는 Backend 작업명령을 1초마다 polling한다.
- Backend 전송 실패 시 재시도하고, 계속 실패하면 디스크 큐에 저장해 복구 전송한다.
- 동일 `eventId`의 이벤트는 Backend에서 중복 저장하지 않는다.

UI 표현 원칙:

- TCP 소켓 자체를 브라우저가 직접 관찰하는 것처럼 표현하지 않는다.
- 설비의 `ERROR` 상태와 통신 알람 이력을 근거로 통신 이상을 표시한다.
- 실시간처럼 보이게 하되 WebSocket이 구현된 것으로 가정하지 않는다. 기본은 주기적인 REST 갱신이다.
- 마지막 갱신 시각과 수동 새로고침 버튼을 항상 제공한다.

## 11. 권장 React 구조

Stitch가 코드를 생성한다면 다음과 같이 역할을 분리한다.

```text
src/
├─ api/
│  ├─ httpClient
│  ├─ authApi
│  ├─ dashboardApi
│  ├─ workOrderApi
│  ├─ lotApi
│  ├─ productionApi
│  ├─ qualityApi
│  ├─ machineApi
│  └─ masterDataApi
├─ components/
│  ├─ layout
│  ├─ common
│  ├─ status
│  ├─ tables
│  └─ process-flow
├─ pages/
│  ├─ Login
│  ├─ Dashboard
│  ├─ WorkOrders
│  ├─ Lots
│  ├─ Production
│  ├─ Machines
│  ├─ Quality
│  ├─ Materials
│  ├─ MasterData
│  ├─ Workers
│  └─ Members
└─ auth/
   ├─ AuthProvider
   ├─ ProtectedRoute
   └─ RoleGuard
```

구현 원칙:

- 서버 DTO 필드명을 임의로 바꾸지 않는다.
- UI 전용 한국어 라벨은 별도 formatter/상태 맵으로 처리한다.
- API 성공 전 화면 목록에 낙관적으로 데이터를 확정하지 않는다.
- 빈 배열을 mock 데이터로 대체하지 않는다.
- 날짜는 Backend의 ISO `LocalDateTime` 문자열을 기준으로 표시한다.
- 표 검색, 필터, 상세 패널 상태는 페이지 컴포넌트에서 관리한다.
- 공통 API 오류 형식을 처리하고 사용자에게 이해 가능한 한국어 메시지를 보여준다.

## 12. 샘플 화면 데이터

디자인 프리뷰에만 아래 샘플을 사용한다. 실제 앱에서는 API 응답으로 교체한다.

```json
{
  "lot": {
    "lotId": 101,
    "lotNo": "EVR-LOT-20260721-001",
    "orderNo": "WO-20260721-001",
    "itemCode": "FG-EVR-001",
    "itemName": "EV Relay",
    "currentProcessCode": "OP60",
    "currentProcessName": "실링/가스충전",
    "lotType": "INITIAL",
    "productionRound": 1,
    "inputQty": 100,
    "okQty": 0,
    "ngQty": 0,
    "status": "RUNNING"
  },
  "productionLogs": [
    { "processCode": "OP20", "machineId": "EQ-WIND-01", "inputQty": 100, "okQty": 97, "ngQty": 3, "status": "COMPLETED" },
    { "processCode": "OP30", "machineId": "EQ-WELD-01", "inputQty": 100, "okQty": 95, "ngQty": 5, "status": "COMPLETED" },
    { "processCode": "OP40_OP50", "machineId": "EQ-ASSY-01", "inputQty": 95, "okQty": 94, "ngQty": 1, "status": "COMPLETED" },
    { "processCode": "OP60", "machineId": "EQ-SEAL-01", "inputQty": 94, "okQty": 40, "ngQty": 1, "status": "RUNNING" }
  ],
  "machines": [
    { "machineId": "EQ-WIND-01", "processCode": "OP20", "status": "IDLE" },
    { "machineId": "EQ-WELD-01", "processCode": "OP30", "status": "IDLE" },
    { "machineId": "EQ-ASSY-01", "processCode": "OP40_OP50", "status": "IDLE" },
    { "machineId": "EQ-SEAL-01", "processCode": "OP60", "status": "RUNNING" },
    { "machineId": "EQ-TEST-01", "processCode": "OP70", "status": "IDLE" },
    { "machineId": "EQ-PACK-01", "processCode": "OP80", "status": "IDLE" }
  ],
  "activeAlarm": {
    "machineId": "EQ-SEAL-01",
    "alarmCode": "CHAMBER_PRESSURE_ERROR",
    "alarmName": "챔버 압력 이상",
    "alarmLevel": "ERROR",
    "message": "Pressure outside expected range",
    "cleared": false
  }
}
```

## 13. 반드시 포함할 UI 상태

각 주요 페이지에 다음 상태의 디자인을 만든다.

- Skeleton 또는 spinner가 있는 로딩 상태
- 데이터가 없는 빈 상태
- API 연결 실패 상태와 재시도 버튼
- 권한 부족 상태
- 세션 만료 상태
- 폼 유효성 오류
- 삭제/상태 변경 확인 모달
- 저장 성공 toast
- 서버 검증 오류 toast 또는 인라인 메시지
- 실시간 갱신 중임을 보여주는 작은 상태 표시

## 14. 생성 결과 체크리스트

- [ ] OP20과 OP30이 병렬이고 OP40_OP50에서 합류하는 흐름이 정확하다.
- [ ] 6개 설비 ID와 공정 코드가 정확히 매칭된다.
- [ ] OP80 OK 수량을 최종 완제품 수량으로 사용한다.
- [ ] OP70은 측정값·기준 범위·제품 순번 중심으로 표현한다.
- [ ] 설비, 작업지시, LOT, 명령 상태 코드가 Backend enum과 일치한다.
- [ ] 작업지시에서 LOT 생성과 보충 LOT 생성으로 이동할 수 있다.
- [ ] LOT 상세에서 명령, 생산, 검사, 불량, 담당자를 함께 추적할 수 있다.
- [ ] 오류 후 재개 시 한 공정에 여러 생산 실적 행이 있을 수 있음을 반영한다.
- [ ] 통신 장애를 `COMM_TIMEOUT`과 `COMM_DISCONNECTED`로 표시한다.
- [ ] Collector 전용 API를 React가 호출하지 않는다.
- [ ] 인증은 Spring Security 세션과 CSRF를 전제로 한다.
- [ ] 기존 frontend의 mock/localStorage 데이터를 가져오지 않는다.
- [ ] 근태, 공지, 교육, 인수인계처럼 Backend에 없는 기능을 추가하지 않는다.
