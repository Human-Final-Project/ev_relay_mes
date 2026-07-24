import React from "react";

const LABELS = {
  IDLE: "대기", RUNNING: "가동 중", ERROR: "이상", STOPPED: "정지",
  CREATED: "생성", RELEASED: "실행 준비", COMPLETED: "완료", CANCELED: "취소",
  WAITING: "대기", HOLD: "보류", SCRAPPED: "폐기",
  PENDING: "전송 대기", DISPATCHED: "전송됨", ACCEPTED: "설비 수락", REJECTED: "설비 거부",
  AVAILABLE: "사용 가능", USED: "사용 완료", DISCARDED: "폐기",
  ACTIVE: "활성", LOCKED: "잠김", RETIRED: "퇴직", INACTIVE: "비활성",
  OK: "OK", NG: "NG", INITIAL: "최초", SUPPLEMENT: "보충",
  ADMIN: "관리자", MANAGER: "매니저", OPERATOR: "작업자", VIEWER: "조회자",
  RM: "원자재", SA: "반제품", FG: "완제품", INFO: "정보", WARN: "경고", WARNING: "경고", CRITICAL: "심각",
};

export function formatDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR", { hour12: false });
}

export function statusLabel(value) {
  return LABELS[value] || value || "-";
}

export function StatusBadge({ value }) {
  return <span className={`mes-status status-${String(value || "unknown").toLowerCase()}`}>{statusLabel(value)}</span>;
}

export function PageHeader({ title, description, actions }) {
  return <div className="mes-page-header"><div><h1>{title}</h1>{description && <p>{description}</p>}</div><div className="mes-actions">{actions}</div></div>;
}

export function LoadingState() {
  return <div className="mes-state" role="status"><span className="mes-spinner" />데이터를 불러오는 중입니다.</div>;
}

export function ErrorState({ error, onRetry }) {
  const message = error?.response?.data?.message || error?.message || "데이터를 불러오지 못했습니다.";
  return <div className="mes-state mes-error"><strong>요청 실패</strong><span>{message}</span>{onRetry && <button className="btn secondary" onClick={onRetry}>다시 시도</button>}</div>;
}

export function EmptyState({ message = "표시할 데이터가 없습니다." }) {
  return <div className="mes-state">{message}</div>;
}

export function Modal({ title, children, onClose, footer }) {
  return <div className="mes-modal-backdrop" onMouseDown={onClose}><section className="mes-modal" onMouseDown={(e) => e.stopPropagation()}><header><h2>{title}</h2><button type="button" className="icon-button" onClick={onClose} aria-label="닫기">×</button></header><div className="mes-modal-body">{children}</div>{footer && <footer>{footer}</footer>}</section></div>;
}

export function Field({ label, children }) {
  return <label className="mes-field"><span>{label}</span>{children}</label>;
}
