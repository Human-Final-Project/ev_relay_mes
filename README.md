# Final Project - EV Relay Mini MES

## 1. 프로젝트 개요

본 프로젝트는 **EV Relay 생산 공정**을 주제로 한 교육용 미니 MES(Manufacturing Execution System) 팀 프로젝트입니다.

실제 제조 현장의 MES 전체 기능을 완벽하게 구현하는 것이 아니라, 교육과정에서 요구되는 **Spring Boot JPA + MySQL + React + C 기반 풀스택 개발 능력**, **쓰레드 관리 및 TCP/IP 통신**, **BOM / LOT 관리**를 하나의 흐름으로 보여주는 것을 목표로 합니다.

> React, Spring Boot JPA, MySQL 기반의 웹 시스템을 구현하고, C로 제작한 장비 시뮬레이터가 TCP/IP 통신으로 생산 이벤트를 전송하면, 백엔드 수집 쓰레드가 해당 데이터를 처리하여 LOT 상태, 공정 이력, 품질 결과를 저장하고, React 화면에서 BOM 정보와 LOT 진행 현황을 확인할 수 있는 미니 MES를 제작합니다.

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
| Simulator | C |
| Communication | TCP/IP Socket |
| Thread | Spring Boot TCP Listener Thread |
| Version Control | Git / GitHub |

> 회원가입, 로그인, 관리자 권한 기능은 구현합니다. 단, JWT는 필수 포함 기술이 아니므로 Session 또는 JWT 중 팀 상황에 맞는 방식을 선택합니다.

---

## 5. 포함 기술 및 구현 방향

| 요구 기술 | 프로젝트 내 구현 내용 |
|---|---|
| Spring Boot JPA | Entity, Repository, Service, Controller 기반 MES API 구현 |
| MySQL | 사용자, 제품, BOM, LOT, 공정 이력, 품질 결과 저장 |
| React | 회원가입/로그인, 관리자 화면, BOM 관리, LOT 조회, 대시보드 구현 |
| C | 가상 장비/수집 대상 시뮬레이터 구현 |
| 쓰레드 관리 | TCP Listener를 별도 쓰레드로 실행하고 수신 루프 관리 |
| TCP/IP 통신 | C 장비 시뮬레이터와 Spring Boot 수집기 간 Socket 통신 |
| BOM 관리 | 제품별 구성품, 필요 수량, BOM 등록/조회/수정 관리 |
| LOT 관리 | LOT 생성, 상태 전이, 공정 이력, 검사 결과 추적 |

---

## 6. 시스템 구조

```text
[ C Equipment Simulator ]
          |
          | TCP/IP Socket
          v
[ Spring Boot TCP Listener Thread ]
          |
          v
[ MES Business Logic Layer ]
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
├ backend         # Spring Boot JPA 기반 REST API + TCP 수집기
├ simulator       # C 기반 장비/공정 시뮬레이터
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

Spring Boot JPA 기반 API 서버와 TCP 수집기를 담당합니다.

주요 구현 대상:

- 회원가입 / 로그인
- 관리자 권한 처리
- 제품 / BOM / 공정 / LOT REST API
- C 장비 시뮬레이터 TCP 데이터 수신
- TCP Listener Thread 관리
- 생산 이벤트 파싱
- LOT 상태 자동 변경
- 공정 이력 저장
- 품질 결과 PASS/FAIL 처리
- 불량 코드 저장
- 대시보드 통계 API 제공

### simulator

C언어 기반 장비 시뮬레이터를 담당합니다.

주요 구현 대상:

- TCP Client 구현
- Spring Boot TCP Listener 접속
- LOT 생산 이벤트 생성
- 공정 완료 이벤트 생성
- 검사 결과 PASS/FAIL 생성
- 불량 코드 생성
- 시연 모드 제공

예시 TCP 메시지:

```text
LOT-20260706-001,COIL_WINDING,EQ-001,COMPLETE,100,PASS,NONE
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
- 품질 검사 PASS/FAIL 처리
- 불량 이력 관리
- 생산 현황 대시보드

### BOM

BOM(Bill of Materials)은 제품을 만들기 위해 필요한 자재 목록입니다.

| 완제품 | 구성품 | 필요 수량 |
|---|---|---:|
| EV Relay | Coil | 1 |
| EV Relay | Contact | 2 |
| EV Relay | Housing | 1 |
| EV Relay | Spring | 1 |
| EV Relay | Terminal | 2 |
| EV Relay | Cover | 1 |

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
- Spring Boot TCP Listener Thread 수신
- 생산 이벤트 파싱
- MySQL 저장

### 품질 관리

- 검사 결과 PASS/FAIL 저장
- 불량 코드 저장
- LOT별 품질 결과 조회

### 대시보드

- 생산 LOT 수
- 진행 중 LOT 수
- 완료 LOT 수
- 불량 LOT 수
- 최근 생산 이벤트

---

## 11. 팀 역할 분담 예시

| 역할 | 담당 영역 |
|---|---|
| 팀원 1 | PM / 문서 / 일정 / 발표 / GitHub 관리 |
| 팀원 2 | 회원가입 / 로그인 / 관리자 권한 / 공통 백엔드 구조 |
| 팀원 3 | Product / BOM / Process / Lot / Quality API |
| 팀원 4 | C 시뮬레이터 / TCP Client / TCP Listener / 쓰레드 관리 |
| 팀원 5 | React 화면 / 대시보드 / BOM 관리 / LOT 조회 |

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
| 7일차 | TCP Listener | Spring Boot TCP 수신기 및 쓰레드 관리 구현 |
| 8일차 | 수집 통합 | TCP 수신 → 파싱 → 비즈니스 로직 → DB 저장 |
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

### Simulator

```bash
cd simulator
# 예시
gcc simulator.c -o simulator
./simulator --mode random
```

---

## 14. 시연 흐름

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
