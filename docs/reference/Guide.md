# EV Relay Mini MES 3주 개발 가이드

## 1. 프로젝트 방향 재정의

이번 프로젝트는 실제 제조 현장의 완전한 MES를 구현하는 프로젝트가 아니라, 교육과정의 핵심 기술을 하나의 완성된 흐름으로 보여주는 **미니 MES 풀스택 프로젝트**입니다.

## 포함 되어야 할 기술

```text
1. Spring Boot JPA + MySQL + React + C
2. 쓰레드 관리 + TCP/IP 통신
3. BOM / LOT 관리
```

핵심 흐름은 다음과 같습니다.

```text
C 장비 시뮬레이터
→ TCP/IP Socket 통신
→ Spring Boot TCP Listener Thread
→ 백엔드 비즈니스 로직
→ MySQL 저장
→ Spring Boot REST API
→ React 화면 / BOM 관리 / LOT 조회 / 대시보드
```

따라서 프로젝트 범위는 다음 문장으로 고정하는 것이 좋습니다.

> React + Spring Boot JPA + MySQL 기반 미니 MES 시스템을 구현하고, C로 제작한 장비 시뮬레이터가 TCP/IP 통신으로 생산 이벤트를 전송하면, 백엔드 수집 쓰레드가 해당 데이터를 처리하여 LOT 상태, 공정 이력, 품질 결과를 저장하고, React 화면에서 BOM 정보와 LOT 진행 현황을 확인할 수 있도록 한다.

로그인, 관리자 권한 기능은 구현하되, JWT는 필수 포함 기술로 강조하지 않습니다. 인증 방식은 Session 또는 JWT 중 팀 상황에 맞게 선택하면 됩니다.

---

## 2. 필수 포함 기술 매핑

| 포함 기술       | 구현 위치           | 구현 방식                                         |
| --------------- | ------------------- | ------------------------------------------------- |
| Spring Boot JPA | Backend             | Entity / Repository / Service / Controller 구조   |
| MySQL           | Database            | 사용자, 제품, BOM, LOT, 공정 이력, 품질 결과 저장 |
| React           | Frontend            | 로그인, 관리자 화면, BOM 관리, LOT 조회, 대시보드 |
| C               | Simulator           | 가상 장비 역할의 TCP Client 구현                  |
| 쓰레드 관리     | Backend             | TCP Listener를 별도 쓰레드로 실행, 수신 루프 관리 |
| TCP/IP 통신     | Simulator / Backend | C Client → Spring Boot TCP Listener Socket 통신   |
| BOM 관리        | Backend / Frontend  | 제품별 구성품, 필요 수량, BOM 등록/조회/수정      |
| LOT 관리        | Backend / Frontend  | LOT 생성, 상태 전이, 공정 이력, 품질 결과 추적    |

### 인증/권한 기능 위치

| 기능        | 권장 범위                                          |
| ----------- | -------------------------------------------------- |
| 로그인      | Session 또는 JWT 중 선택                           |
| 관리자 권한 | BOM/LOT/공정 관리 메뉴 접근 제어, 신규 사용자 등록 |
| 일반 사용자 | 조회 화면 중심 접근                                |

발표에서는 인증 방식보다 **BOM/LOT 흐름, TCP/IP 통신, 쓰레드 기반 수집 구조**를 더 강조하는 것이 좋습니다.

---

## 3. 15일 Sprint 운영 계획

|   일차 | 목표            | 주요 작업                                         |
| -----: | --------------- | ------------------------------------------------- |
|  1일차 | 기획/설계       | 포함 기술 확정, MVP 범위, ERD/API/TCP 메시지 초안 |
|  2일차 | DB/JPA          | Entity, Repository, MySQL 연결                    |
|  3일차 | 회원/권한       | 로그인, 관리자/일반 사용자 구분                   |
|  4일차 | 기준정보 API    | Product, Material, BOM, Process API               |
|  5일차 | LOT API         | LOT 생성, LOT 목록/상세, LOT 상태 전이            |
|  6일차 | C TCP Client    | C 시뮬레이터 TCP 접속/메시지 송신                 |
|  7일차 | TCP Listener    | Spring Boot TCP 수신기와 쓰레드 관리 구현         |
|  8일차 | 수집 통합       | TCP 수신 → 파싱 → 비즈니스 로직 → DB 저장         |
|  9일차 | React 기본 화면 | 로그인, 대시보드 레이아웃, 메뉴 구성              |
| 10일차 | BOM 화면        | 제품/BOM 목록, BOM 상세, BOM 등록/수정            |
| 11일차 | LOT 화면        | LOT 목록/상세, 공정 진행률, 공정 이력             |
| 12일차 | 대시보드        | 생산 현황, 불량률, 최근 이벤트                    |
| 13일차 | 통합 테스트     | 전체 흐름 점검, 버그 수정                         |
| 14일차 | 문서/PPT        | README, ERD, API, TCP 프로토콜, 발표자료          |
| 15일차 | 최종 리허설     | 시연 데이터, 최종 점검, 발표 리허설               |

---

## 4. 1주차 개발 가이드

### 1일차: 기획/설계

정해야 할 것:

```text
프로젝트명
MVP 범위
팀원 역할
DB 테이블 초안
REST API 초안
TCP 메시지 포맷
화면 메뉴 구조
```

권장 프로젝트명:

```text
C TCP 장비 시뮬레이터 기반 EV Relay Mini MES
```

완료 기준:

```text
GitHub 기본 구조 생성
frontend / backend / simulator / docs 폴더 생성
ERD 초안 작성
API 초안 작성
TCP 메시지 포맷 초안 작성
```

---

### 2일차: DB/JPA

우선 테이블:

```text
users
roles
products
materials
bom
bom_items
processes
lots
production_histories
quality_results
defects
equipment_logs
```

완료 기준:

```text
Entity 작성
Repository 작성
MySQL 테이블 생성 확인
샘플 데이터 insert 가능
```

---

### 3일차: 로그인/관리자 권한

필수 기능:

```text
로그인
관리자/일반 사용자 권한 구분
관리자 메뉴 접근 제한
```

주의:

```text
JWT는 필수 기술이 아니므로, 구현이 부담되면 Session 방식도 가능
보안 고도화보다 권한 구분 흐름이 보이는 것이 우선
```

완료 기준:

```text
로그인 가능
관리자 계정으로 BOM/LOT 관리 메뉴 접근 가능
관리자 계정으로 신규 사용자 등록
일반 사용자는 조회 화면 중심으로 접근
```

---

### 4일차: 기준정보 API

구현 API 예시:

```text
GET /api/products
POST /api/products
GET /api/products/{productId}/bom
POST /api/products/{productId}/bom
GET /api/processes
```

완료 기준:

```text
제품 조회 가능
BOM 조회 가능
BOM 등록 가능
공정 목록 조회 가능
```

---

### 5일차: LOT API

구현 API 예시:

```text
POST /api/lots
GET /api/lots
GET /api/lots/{lotId}
GET /api/lots/{lotId}/histories
```

LOT 생성 예시:

```json
{
  "productId": 1,
  "lotCode": "LOT-20260706-001",
  "targetQuantity": 100
}
```

완료 기준:

```text
LOT 생성 가능
LOT 목록 조회 가능
LOT 상세 조회 가능
LOT 상태 변경 로직 준비
```

---

## 5. 2주차 개발 가이드

### 6일차: C TCP Client

C 시뮬레이터 역할:

```text
TCP 서버 접속
LOT 코드 생성 또는 입력
공정별 메시지 생성
PASS/FAIL 생성
불량 코드 생성
메시지 송신
```

권장 메시지 포맷:

```text
lotCode,processCode,equipmentCode,eventType,quantity,result,defectCode
```

예시:

```text
LOT-20260706-001,COIL_WINDING,EQ-001,COMPLETE,100,PASS,NONE
LOT-20260706-001,INSPECTION,EQ-003,COMPLETE,100,FAIL,CONTACT_DEFECT
```

완료 기준:

```text
C 프로그램 실행 가능
서버 IP/PORT로 접속 시도 가능
콘솔에 전송 메시지 출력
```

---

### 7일차: Spring Boot TCP Listener + 쓰레드 관리

역할:

```text
TCP 포트 오픈
Client 접속 수락
수신 루프를 별도 쓰레드로 실행
라인 단위 메시지 수신
CSV 파싱
ProductionEventService 호출
```

권장 포트:

```text
React: 3000
Spring REST API: 8080
TCP Listener: 9000
MySQL: 3306
```

완료 기준:

```text
Spring Boot 실행 시 TCP Listener Thread 시작
C에서 보낸 메시지를 백엔드 로그에서 확인
잘못된 메시지에 대한 예외 처리 가능
```

---

### 8일차: TCP 수집 통합

흐름:

```text
C 메시지 송신
→ TCP Listener Thread 수신
→ 메시지 파싱
→ ProductionEventService
→ Lot 상태 변경
→ ProductionHistory 저장
→ QualityResult 저장
→ Defect 저장
```

완료 기준:

```text
C 시뮬레이터 실행 후 DB에 생산 이력 저장
FAIL 메시지 수신 시 불량 이력 저장
대시보드 API에서 수치 변화 확인 가능
```

---

### 9~12일차: React 화면

필수 화면:

```text
로그인 화면
관리자 대시보드
제품/BOM 관리 화면
사용자 관리 화면
LOT 목록 화면
LOT 상세 화면
공정 이력 테이블
품질 결과 표시
```

화면 우선순위:

```text
1. LOT 목록/상세
2. BOM 조회/관리
3. 대시보드
4. 회원/관리자 화면
```

완료 기준:

```text
C 시뮬레이터 실행 후 LOT 상세 화면에서 진행 상태 확인 가능
관리자 권한으로 BOM 관리 가능
대시보드에서 생산/불량 수치 확인 가능
```

---

## 6. 우선순위 정리

## 1순위 — 반드시 구현

```text
Spring Boot JPA + MySQL 연동
React 화면 구현
C TCP Client 구현
Spring Boot TCP Listener 구현
TCP 수신 쓰레드 관리
BOM 조회/관리
LOT 생성/조회/상태 관리
로그인/관리자 권한
```

## 2순위 — 가능하면 구현

```text
LOT 검색/필터
공정 진행률 표시
최근 생산 이벤트 로그
불량 코드별 통계
시연 모드
```

## 3순위 — 시간 남으면 구현

```text
차트 고도화
자동 새로고침
제품/BOM 수정/삭제 고도화
설비 관리
엑셀 다운로드
```

---

## 7. 발표 시연 흐름

발표 시연은 다음 흐름 하나로 고정하는 것이 안전합니다.

```text
1. 관리자 로그인
2. 제품/BOM 정보 확인
3. 신규 LOT 생성
4. C 시뮬레이터 실행
5. TCP/IP 통신으로 공정 이벤트 전송
6. Spring Boot TCP Listener Thread가 메시지 수신
7. MySQL에 생산 이력 저장
8. React LOT 상세 화면에서 공정 진행 확인
9. 검사 PASS/FAIL 및 불량 정보 확인
10. 대시보드 수치 변경 확인
```

---

## 8. 최종 정리

이번 프로젝트는 다음 세 가지를 확실히 보여주면 됩니다.

```text
1. Spring Boot JPA + MySQL + React + C를 연결한 풀스택 구조
2. 쓰레드 관리가 포함된 TCP/IP 장비 데이터 수집 구조
3. BOM / LOT 중심의 MES 도메인 흐름
```

즉, 기능을 많이 늘리기보다 **C 시뮬레이터 → TCP/IP → Spring Boot 수집 쓰레드 → MySQL 저장 → React LOT/BOM 화면** 흐름을 안정적으로 완성하는 것이 가장 중요합니다.
