// -------------------------------------------------------------
// 설비 이상 신고 공용 저장소 (localStorage 기반)
// notificationBus.js / handoverStore.js와 동일한 패턴입니다.
// 사원이 올린 신고를 관리자가 조회하고 상태를 변경할 수 있게 해줍니다.
// -------------------------------------------------------------

const STORAGE_KEY = "mes_equipment_issues";
const EVENT_NAME = "mes-equipment-issue-update";
const MAX_ITEMS = 100;

// 최초 1회, 저장된 신고가 없을 때 채워 넣을 샘플 데이터
const seedIssues = [
  {
    id: "seed-1",
    equipment: "레진 주입기 (S-04)",
    issueType: "온도 이상",
    severity: "mid",
    note: "히터 온도가 설정값보다 5도 낮게 유지됨.",
    photoName: null,
    reporter: "김철수",
    time: "어제 16:20",
    status: "resolved",
    createdAt: Date.now() - 1000 * 60 * 60 * 24,
  },
  {
    id: "seed-2",
    equipment: "권선기 (S-01)",
    issueType: "이상 소음·진동",
    severity: "low",
    note: "가동 시 미세한 떨림 소음 발생.",
    photoName: null,
    reporter: "김철수",
    time: "3일 전 09:40",
    status: "resolved",
    createdAt: Date.now() - 1000 * 60 * 60 * 24 * 3,
  },
];

export function getIssues() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(seedIssues));
      return seedIssues;
    }
    return JSON.parse(raw);
  } catch {
    return [];
  }
}

export function addIssue({ equipment, issueType, severity, note, photoName, reporter }) {
  const list = getIssues();
  const entry = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    equipment,
    issueType,
    severity, // "low" | "mid" | "urgent"
    note,
    photoName: photoName || null,
    reporter: reporter || "사원",
    time: `방금 · ${new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: false })}`,
    status: "received", // "received" | "checking" | "resolved"
    createdAt: Date.now(),
  };
  const next = [entry, ...list].slice(0, MAX_ITEMS);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  window.dispatchEvent(new Event(EVENT_NAME));
  return entry;
}

export function updateIssueStatus(id, status) {
  const list = getIssues();
  const next = list.map((i) => (i.id === id ? { ...i, status } : i));
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  window.dispatchEvent(new Event(EVENT_NAME));
}

export function subscribeIssues(callback) {
  const handler = () => callback(getIssues());
  window.addEventListener(EVENT_NAME, handler);
  window.addEventListener("storage", handler);
  return () => {
    window.removeEventListener(EVENT_NAME, handler);
    window.removeEventListener("storage", handler);
  };
}
