import React, { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

// -------------------------------------------------------------
// [데이터 정의] 더미 데이터
// 실제로는 관리자 공지 작성(AdminNoticeEditor)에서 저장한 데이터를
// API/상태관리를 통해 그대로 받아오면 됩니다.
// -------------------------------------------------------------
const allNotices = [
  {
    id: 1,
    type: "warn",
    title: "필독: 라인 클리닝 일정 변경",
    desc: "오후 3시 예정되었던 정기 클리닝이 설비 점검으로 인해 4시로 연기되었습니다.",
    footer: "최관리 팀장 | 오늘 14:10",
    pinned: true,
  },
  {
    id: 2,
    type: "info",
    title: "신규 안전 보호구 착용 안내",
    desc: "내일부터 지급되는 신형 정전기 방지 장갑을 필히 착용해주시기 바랍니다.",
    footer: "품질관리팀 | 오늘 09:20",
    pinned: false,
  },
  {
    id: 3,
    type: "plain",
    title: "7월 정기 안전 교육 일정 안내",
    desc: "이번 달 정기 안전 교육은 25일(금) 오전 9시, 3층 교육장에서 진행됩니다.",
    footer: "안전관리팀 | 어제 17:40",
    pinned: false,
  },
  {
    id: 4,
    type: "warn",
    title: "필독: 정전 예정 안내",
    desc: "설비 점검을 위해 내일 새벽 2시~4시 사이 B동 전체 정전이 예정되어 있습니다.",
    footer: "설비관리팀 | 2일 전 11:05",
    pinned: false,
  },
  {
    id: 5,
    type: "info",
    title: "라인 A 작업대 배치 변경 안내",
    desc: "동선 개선을 위해 라인 A의 2번, 3번 작업대 위치가 서로 바뀌었습니다.",
    footer: "생산관리팀 | 3일 전 08:30",
    pinned: false,
  },
  {
    id: 6,
    type: "plain",
    title: "사내 식당 여름철 운영시간 변경",
    desc: "7월부터 8월까지 중식 운영시간이 11:30~13:30으로 조정됩니다.",
    footer: "총무팀 | 4일 전 10:00",
    pinned: false,
  },
];

const TYPES = {
  all: { label: "전체" },
  warn: { label: "필독 · 경고", icon: "⚠️" },
  info: { label: "안전 안내", icon: "🦺" },
  plain: { label: "일반 공지", icon: "📋" },
};

const styles = `
  .nbp-container {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    color: #333333;
    background-color: #f8fafc;
    padding: 24px;
    min-height: 100vh;
    box-sizing: border-box;
  }

  .nbp-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 20px;
  }
  .nbp-head-left { display: flex; align-items: center; gap: 10px; }
  .nbp-head h1 { margin: 0; font-size: 20px; font-weight: 800; color: #1e293b; }
  .nbp-back {
    background: #ffffff;
    border: 1px solid #cbd5e1;
    padding: 7px 14px;
    border-radius: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #475569;
    cursor: pointer;
  }
  .nbp-back:hover { background: #f1f5f9; }

  .nbp-filters { display: flex; gap: 8px; margin-bottom: 18px; flex-wrap: wrap; }
  .nbp-filter-btn {
    border: 1.5px solid #e2e8f0;
    background: #ffffff;
    color: #475569;
    padding: 7px 14px;
    border-radius: 999px;
    font-size: 12.5px;
    font-weight: 700;
    cursor: pointer;
  }
  .nbp-filter-btn.active {
    background: #02639a;
    border-color: #02639a;
    color: #ffffff;
  }

  .nbp-card {
    border-radius: 8px;
    padding: 18px;
    display: flex;
    gap: 14px;
    margin-bottom: 12px;
    background: #ffffff;
    border: 1px solid #e2e8f0;
    box-shadow: 0 1px 3px rgba(0,0,0,0.02);
  }
  .nbp-card.warn { background-color: #fff1f2; border-color: #fecdd3; }
  .nbp-card.info { background-color: #eff6ff; border-color: #bfdbfe; }
  .nbp-card.plain { background-color: #f0fdf4; border-color: #bbf7d0; }

  .nbp-icon { font-size: 19px; margin-top: 2px; }
  .nbp-content { flex: 1; min-width: 0; }
  .nbp-top { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
  .nbp-title { font-size: 14.5px; font-weight: 800; color: #1e293b; }
  .nbp-pin { font-size: 11px; font-weight: 800; color: #94a3b8; margin-left: auto; }
  .nbp-desc { font-size: 13px; color: #475569; line-height: 1.55; margin-bottom: 8px; white-space: pre-wrap; }
  .nbp-footer { font-size: 11.5px; color: #94a3b8; font-weight: 600; }

  .nbp-empty {
    background: #ffffff;
    border: 1px dashed #cbd5e1;
    border-radius: 10px;
    padding: 40px;
    text-align: center;
    color: #94a3b8;
    font-size: 13.5px;
  }
`;

function NoticeBoardPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState("all");

  const filtered = useMemo(() => {
    const list =
      filter === "all"
        ? allNotices
        : allNotices.filter((n) => n.type === filter);
    return [...list].sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0));
  }, [filter]);

  return (
    <div className="nbp-container">
      <style>{styles}</style>

      <div className="nbp-head">
        <div className="nbp-head-left">
          <h1>📢 관리자 공지 전체보기</h1>
        </div>
        <button className="nbp-back" onClick={() => navigate("/dashboard")}>
          ❮ 대시보드로
        </button>
      </div>

      <div className="nbp-filters">
        {Object.entries(TYPES).map(([key, t]) => (
          <button
            key={key}
            className={`nbp-filter-btn ${filter === key ? "active" : ""}`}
            onClick={() => setFilter(key)}
          >
            {t.icon ? `${t.icon} ` : ""}
            {t.label}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <div className="nbp-empty">해당 유형의 공지가 없습니다.</div>
      ) : (
        filtered.map((n) => (
          <div key={n.id} className={`nbp-card ${n.type}`}>
            <div className="nbp-icon">{TYPES[n.type].icon}</div>
            <div className="nbp-content">
              <div className="nbp-top">
                <span className="nbp-title">{n.title}</span>
                {n.pinned && <span className="nbp-pin">📌 고정</span>}
              </div>
              <div className="nbp-desc">{n.desc}</div>
              <div className="nbp-footer">{n.footer}</div>
            </div>
          </div>
        ))
      )}
    </div>
  );
}

export default NoticeBoardPage;