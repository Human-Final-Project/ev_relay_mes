// -------------------------------------------------------------
// 교대 인수인계 공용 저장소 (localStorage 기반)
// notificationBus.js와 동일한 방식으로, 실제 서비스에서는
// 서버 API(등록/조회)로 교체하면 됩니다.
// -------------------------------------------------------------

const STORAGE_KEY = "mes_handovers";
const EVENT_NAME = "mes-handover-update";
const MAX_ITEMS = 30;

export function getHandovers() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export function saveHandover({ line, writer, note }) {
  const list = getHandovers();
  const entry = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    line,
    writer,
    note,
    time: new Date().toLocaleString("ko-KR", {
      month: "numeric",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }),
    createdAt: Date.now(),
  };
  const next = [entry, ...list].slice(0, MAX_ITEMS);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  window.dispatchEvent(new Event(EVENT_NAME));
  return entry;
}

export function subscribeHandovers(callback) {
  const handler = () => callback(getHandovers());
  window.addEventListener(EVENT_NAME, handler);
  window.addEventListener("storage", handler);
  return () => {
    window.removeEventListener(EVENT_NAME, handler);
    window.removeEventListener("storage", handler);
  };
}
