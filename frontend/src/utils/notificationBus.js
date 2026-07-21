// -------------------------------------------------------------
// 알림 공용 버스 (localStorage 기반)
// - 실제 서비스에서는 WebSocket/서버 푸시로 교체하면 됩니다.
// - 지금은 브라우저 저장소 + 커스텀 이벤트로 "같은 브라우저 내 실시간 갱신"을 흉내냅니다.
//   (다른 탭에서 발생한 알림은 storage 이벤트로 전달되고,
//    같은 탭 안에서는 커스텀 이벤트로 즉시 반영됩니다.)
// -------------------------------------------------------------

const STORAGE_KEY = "mes_notifications";
const EVENT_NAME = "mes-notification-update";
const MAX_ITEMS = 50;

export function getNotifications() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

// targetRole: "admin" | "employee" | "both"
// type: "info" | "warn" | "error"
export function pushNotification({ targetRole, type, title, desc }) {
  const list = getNotifications();
  const entry = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    targetRole,
    type,
    title,
    desc,
    time: new Date().toLocaleTimeString("ko-KR", { hour12: false }),
    createdAt: Date.now(),
  };
  const next = [entry, ...list].slice(0, MAX_ITEMS);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  window.dispatchEvent(new Event(EVENT_NAME));
  return entry;
}

export function subscribeNotifications(callback) {
  const handler = () => callback(getNotifications());
  window.addEventListener(EVENT_NAME, handler);
  window.addEventListener("storage", handler);
  return () => {
    window.removeEventListener(EVENT_NAME, handler);
    window.removeEventListener("storage", handler);
  };
}

export function getLastSeen(role) {
  return Number(localStorage.getItem(`mes_notifications_seen_${role}`) || 0);
}

export function markSeen(role) {
  localStorage.setItem(`mes_notifications_seen_${role}`, String(Date.now()));
}
