# Final Project - EV Relay Mini MES

## 1. 프로젝트 개요

본 프로젝트는 **EV Relay 생산 공정**을 주제로 한 교육용 미니 MES(Manufacturing Execution System) 팀 프로젝트입니다.

실제 제조 현장의 MES 전체 기능을 완벽하게 구현하는 것이 아니라, 교육과정에서 요구되는 **Spring Boot JPA + MySQL + React + C 기반 풀스택 개발 능력**, **쓰레드 관리 및 TCP/IP 통신**, **BOM / LOT 관리**를 하나의 흐름으로 보여주는 것을 목표로 합니다.

> C로 제작한 L1 설비 시뮬레이터가 TCP/IP로 생산 이벤트를 전송하면 C 기반 L2 수집기가 메시지를 파싱·검증하고, Spring Boot REST API로 전달하여 LOT 상태, 공정별 생산 요약, 품질 결과와 알람을 저장합니다. React 화면에서는 BOM 정보와 LOT 진행 현황을 확인합니다.

---

## 2. 프로젝트 주제

- **주제:** C TCP 장비 시뮬레이터 기반 EV Relay 생산 Mini MES
- **팀원:** 5명
- **기간:** 약 3주 / 평일 기준 약 15일
- **핵심 목표:** 풀스택 웹 시스템 + C 장비 시뮬레이터 + TCP/IP 수집 구조 + BOM/LOT 관리 구현

---

## 3. 포함 되어야 할 기술

```text
1. Spring Boot JPA + MySQL + React + C
2. 쓰레드 관리 + TCP/IP 통신
3. BOM / LOT 관리
```

---

## 4. 기술 스택

| 구분 | 기술 |
|---|---|
| Frontend | React |
| Backend | Spring Boot JPA |
| Database | MySQL |
| L1 Simulator / L2 Collector | C |
| Communication | TCP/IP Socket |
| Thread | L2 연결당 `pthread` 작업 스레드 |
| Version Control | Git / GitHub |

> 회원가입, 로그인, 관리자 권한 기능은 구현합니다. 단, JWT는 필수 포함 기술이 아니므로 Session 또는 JWT 중 팀 상황에 맞는 방식을 선택합니다.

---

## 5. 포함 기술 및 구현 방향

| 요구 기술 | 프로젝트 내 구현 내용 |
|---|---|
| Spring Boot JPA | Entity, Repository, Service, Controller 기반 MES API 구현 |
| MySQL | 사용자, 제품, BOM, LOT, 공정 이력, 품질 결과 저장 |
| React | 회원가입/로그인, 관리자 화면, BOM 관리, LOT 조회, 대시보드 구현 |
| C | L1 가상 설비와 L2 수집기 구현 |
| 쓰레드 관리 | L2가 L1 연결마다 `pthread` 작업 스레드를 생성하여 수신 버퍼 관리 |
| TCP/IP 통신 | L1 설비 시뮬레이터와 L2 수집기 간 Socket 통신 |
| REST API | L2가 검증한 생산 이벤트를 Spring Boot Collector API로 전달 |
| REST Polling | L2가 Backend의 대기 작업명령을 1초마다 조회 |
| BOM 관리 | 제품별 구성품, 필요 수량, BOM 등록/조회/수정 관리 |
| LOT 관리 | LOT 생성, 상태 전이, 공정 이력, 검사 결과 추적 |

---

## 6. 시스템 구조

```text
[ C L1 Equipment Simulators (6) ]
          |
          | TCP/IP CSV messages
          v
[ C L2 Collector / pthread workers ]
          |
          | HTTP REST / JSON
          v
[ Spring Boot MES API / Business Logic ]
          |
          v
[ MySQL Database ]
          |
          v
[ Spring Boot REST API ]
          |
          v
[ React Frontend ]
```

---

## 7. 프로젝트 구조

```text
final_project
├ frontend        # React 기반 MES 화면
├ backend         # Spring Boot JPA 기반 MES/Collector REST API
├ embedded-c
│  ├ mes_L1       # C 기반 L1 설비 시뮬레이터
│  └ mes_collector # C 기반 L2 TCP 수집기
├ docs            # 요구사항, ERD, API 명세, TCP 프로토콜, 테스트 문서
└ README.md       # 프로젝트 소개 문서
```

---

## 8. 폴더 설명

### frontend

React 기반 사용자 화면을 담당합니다.

주요 구현 대상:

- 회원가입 / 로그인
- 관리자 화면
- 제품 관리
- BOM 관리
- LOT 목록 / 상세 조회
- 공정 진행률 표시
- 검사 결과 조회
- 불량 이력 조회
- 생산 현황 대시보드

### backend

Spring Boot JPA 기반 MES API 서버와 DB 저장 로직을 담당합니다. TCP 수신은 L2가 담당하며 Backend에는 TCP 패키지를 두지 않습니다.

주요 구현 대상:

- 회원가입 / 로그인
- 관리자 권한 처리
- 제품 / BOM / 공정 / LOT REST API
- L2 전용 Collector 요청 DTO / Controller / Service
- L2가 전달한 JSON 검증 및 업무 처리
- LOT 상태 자동 변경
- 공정별 생산 요약 저장
- 품질 결과 OK/NG 처리
- 불량 코드 저장
- 대시보드 통계 API 제공

### embedded-c

C언어 기반 L1 설비 시뮬레이터와 L2 수집기를 담당합니다.

주요 구현 대상:

- L1 TCP Client 및 6개 공정 이벤트 생성
- L2 TCP Server 및 연결당 `pthread` 작업 스레드
- L2 메시지 파싱·검증 및 Backend REST 전송
- L2 REST Polling 및 L1 시작·정지·재개 명령 전달
- `HELLO`, `HEARTBEAT`, 재연결과 통신 Timeout 처리
- 공정 완료 시 생산 요약 생성
- 오류 발생 시 불량·알람 이벤트 생성

예시 TCP 메시지:

```text
V1,PRODUCTION,EQ-WIND-01,OP20,EVR-LOT-20260708-001,100,97,3,COMPLETED
```

권장 시연 모드:

```bash
./simulator --mode pass
./simulator --mode fail
./simulator --mode random
```

### docs

프로젝트 산출물을 관리합니다.

추천 구조:

```text
docs
├ requirements.md       # 요구사항 정의
├ backlog.md            # 기능 백로그
├ erd.md                # ERD 및 DB 설계
├ api-spec.md           # REST API 명세
├ tcp-protocol.md       # TCP 메시지 포맷
├ screen.md             # 화면 설계
├ test-case.md          # 테스트 케이스
├ troubleshooting.md    # 문제 해결 기록
└ presentation          # 발표 자료
```

---

## 9. 핵심 도메인 개념

### MES

MES는 제조 현장의 생산 실행을 관리하는 시스템입니다. 본 프로젝트에서는 MES 전체가 아니라 다음 핵심 흐름을 축소 구현합니다.

- 제품/BOM 기준정보 관리
- LOT 생성 및 상태 관리
- 공정 진행 이력 저장
- 장비 시뮬레이터 데이터 수집
- 품질 검사 OK/NG 처리
- 불량 이력 관리
- 생산 현황 대시보드

### BOM

BOM(Bill of Materials)은 제품을 만들기 위해 필요한 자재 목록입니다.

공정별 상세 BOM과 품목 코드는 [`docs/reference/sql_code_ver.2.md`](docs/reference/sql_code_ver.2.md)의 `boms` 초기 데이터를 기준으로 합니다.

핵심 공정 흐름은 OP20과 OP30이 병렬로 실행된 뒤 OP40_OP50에서 합류하는 구조입니다.

```text
OP20 코일 어셈블리 ───────┐
                          ├→ OP40_OP50 본체 조립 → OP60 → OP70 → OP80
OP30 접점 어셈블리 ───────┘
```

OP40_OP50에서 제품 1개를 조립하려면 `SA-COIL-001` 1개와 `SA-CONTACT-001` 1개가 필요합니다. 다른 원자재가 충분하다고 가정하면 조립 가능 수량은 `min(OP20 okQty, OP30 okQty)`입니다.

### LOT

LOT은 같은 조건에서 생산되는 제품 묶음 단위입니다.

LOT을 기준으로 생산 이력, 공정 이력, 검사 이력, 불량 이력을 추적합니다.

추천 상태:

```text
CREATED
IN_PROGRESS
INSPECTION
COMPLETED
FAILED
```

---

## 10. 주요 기능

### 사용자 / 권한

- 회원가입
- 로그인
- 관리자 / 일반 사용자 권한 구분
- 관리자 메뉴 접근 제어

### 기준정보 관리

- 제품 관리
- 자재 관리
- BOM 관리
- 공정 관리

### 생산 관리

- LOT 생성
- LOT 목록 조회
- LOT 상세 조회
- LOT 상태 관리
- 공정 이력 조회

### 설비 데이터 수집

- C 시뮬레이터 실행
- TCP/IP Socket 통신
- L2 TCP 수신 및 메시지 파싱
- L2에서 Backend REST API로 JSON 전달
- MySQL 저장

### 품질 관리

- 검사 결과 OK/NG 저장
- 불량 코드 저장
- LOT별 품질 결과 조회

### 대시보드

- 생산 LOT 수
- 진행 중 LOT 수
- 완료 LOT 수
- 불량 LOT 수
- 최근 생산 이벤트

---

## 11. 팀 역할 분담

| 역할 | 담당 영역 |
|---|---|
| Backend 홍준희 / 김도형 | MES API, Collector DTO·Controller·Service, JPA/DB 저장과 LOT 수량 계산 |
| Frontend 강성민 | React 화면, 대시보드, BOM/LOT 및 공정별 수량 표시 |
| L2 변후민 | C 수집기, TCP Server, 파서, `pthread`, Backend REST 전송 |
| L1 박민 | C 설비 시뮬레이터, TCP Client, 생산·불량·알람 이벤트 생성 |

상황에 따라 Backend와 Frontend는 기능 단위로 교차 지원합니다.

---

## 12. 3주 개발 일정

| 일차 | 목표 | 주요 작업 |
|---:|---|---|
| 1일차 | 기획/설계 | 요구 기술 확정, MVP 범위, ERD/API/TCP 메시지 초안 |
| 2일차 | DB/JPA | Entity, Repository, MySQL 연결 |
| 3일차 | 회원/권한 | 회원가입/로그인, 관리자/일반 사용자 구분 |
| 4일차 | 기준정보 API | Product, Material, BOM, Process API |
| 5일차 | LOT API | LOT 생성, LOT 목록/상세, LOT 상태 전이 |
| 6일차 | C TCP Client | C 시뮬레이터 TCP 접속/메시지 송신 |
| 7일차 | L2 TCP 수집기 | C TCP Server, 파서 및 연결별 `pthread` 구현 |
| 8일차 | 수집 통합 | L1 TCP → L2 파싱 → Backend REST → DB 저장 |
| 9일차 | React 기본 화면 | 로그인, 메뉴, 대시보드 레이아웃 |
| 10일차 | BOM 화면 | 제품/BOM 목록, BOM 상세, BOM 등록/수정 |
| 11일차 | LOT 화면 | LOT 목록/상세, 공정 진행률 |
| 12일차 | 대시보드 | 생산 현황, 불량률, 최근 이벤트 |
| 13일차 | 통합 테스트 | 전체 흐름 점검, 버그 수정 |
| 14일차 | 문서/PPT | README, ERD, API, TCP 프로토콜, 발표자료 |
| 15일차 | 최종 리허설 | 시연 데이터, 최종 점검, 발표 리허설 |

---

## 13. 실행 방법

> 세부 실행 방법은 개발 진행 후 갱신합니다.

### Frontend

```bash
cd frontend
npm install
npm start
```

### Backend

```bash
cd backend
./gradlew bootRun
```

### L1 Simulator

```bash
cd embedded-c/mes_L1
make
./l1_simulator
```

### L2 Collector

```bash
cd embedded-c/mes_collector
make
./mes_collector
```

---

## 14. 시연 흐름

```text
1. 관리자 로그인
2. 제품/BOM 정보 확인
3. 신규 LOT 생성
4. C 시뮬레이터 실행
5. OP20과 OP30 L1을 병렬 실행하여 TCP 생산 이벤트 전송
6. L2가 메시지를 수신·검증하고 Backend REST API로 전달
7. MySQL에 LOT당 공정별 생산 요약 저장
8. OP20·OP30 완료 후 OP40_OP50, OP60, OP70, OP80 순서로 진행
9. React LOT 상세 화면에서 공정별 투입/OK/NG 확인
10. OP80 `okQty` 기준 최종 완제품 수량과 불량·알람 확인
```

---

## 15. 프로젝트 목표

본 프로젝트의 최종 목표는 단순 CRUD 웹 애플리케이션이 아니라, EV Relay 생산 흐름을 교육용 MES 관점에서 구현하는 것입니다.

핵심 목표:

- Spring Boot JPA + MySQL 기반 MES 데이터 관리
- React 기반 BOM/LOT/대시보드 화면 제공
- C 기반 장비 시뮬레이터 구현
- 쓰레드 기반 TCP/IP 수집 구조 구현
- BOM 기반 제품 구성 관리
- LOT 단위 생산 추적
- 공정별 생산 이력 기록
- 검사 및 불량 이력 관리
- 회원가입, 로그인, 관리자 권한 기능 제공
