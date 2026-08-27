// -------------------------------------------------------------
// 사원 목록 공용 저장소 (localStorage 기반)
// notificationBus.js / handoverStore.js / equipmentIssueStore.js와 동일한 패턴입니다.
// 백엔드가 준비되면 이 파일의 함수 내부만 MesApi 호출로 교체하면 됩니다.
// -------------------------------------------------------------

const STORAGE_KEY = "mes_employees";
const EVENT_NAME = "mes-employees-update";

const initialEmployees = [
  { id: "admin", name: "admin", email: "1234@gmail.com", dept: "경영지원", phone: "1234", status: "재직중", line: "사무실 (생산 외)", role: "admin" },
  { id: "EMP26001", name: "강석진", email: "sj.kang@medisone.com", dept: "생산관리", phone: "010-1234-5678", status: "재직중", line: "사무실 (생산 외)", role: "admin" },
  { id: "EMP26002", name: "이민아", email: "ma.lee@medisone.com", dept: "생산1팀", phone: "010-2345-6789", status: "재직중", line: "Line A", role: "user" },
  { id: "EMP26003", name: "박지민", email: "jm.park@medisone.com", dept: "생산2팀", phone: "010-3456-7890", status: "재직중", line: "Line B", role: "user" },
  { id: "EMP26004", name: "최유진", email: "yj.choi@medisone.com", dept: "품질관리", phone: "010-4567-8901", status: "재직중", line: "사무실 (생산 외)", role: "user" },
  { id: "EMP26005", name: "김철수", email: "cs.kim@medisone.com", dept: "자재관리", phone: "010-5678-9012", status: "휴직", line: "사무실 (생산 외)", role: "user" },
  { id: "EMP26006", name: "정수민", email: "sm.jung@medisone.com", dept: "생산1팀", phone: "010-6789-0123", status: "재직중", line: "Line A", role: "user" },
];

export function getEmployees() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(initialEmployees));
      return initialEmployees;
    }
    return JSON.parse(raw);
  } catch {
    return initialEmployees;
  }
}

export function saveEmployees(list) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  window.dispatchEvent(new Event(EVENT_NAME));
}

export function subscribeEmployees(callback) {
  const handler = () => callback(getEmployees());
  window.addEventListener(EVENT_NAME, handler);
  window.addEventListener("storage", handler);
  return () => {
    window.removeEventListener(EVENT_NAME, handler);
    window.removeEventListener("storage", handler);
  };
}
