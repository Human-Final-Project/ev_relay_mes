import React, { useEffect, useMemo, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import MesApi from "../api/MesApi";

const TYPES = {
  all: { label: "전체" },
  warn: { label: "필독 · 경고", icon: "⚠️" },
  info: { label: "안전 안내", icon: "🦺" },
  plain: { label: "일반 공지", icon: "📋" },
};
const BACKEND_TO_KEY = { WARNING: "warn", INFO: "info", GENERAL: "plain" };

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
  const [notices, setNotices] = useState([]);
  const [isLoading, setIsLoading] = useState(false);

  const fetchNotices = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await MesApi.getNotices();
      const now = new Date();
      // 예약 게시 시각이 아직 안 지난 공지는 게시판에 노출하지 않는다.
      const published = (res.data || []).filter(
        (n) => !n.publishAt || new Date(n.publishAt) <= now
      );
      setNotices(published);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "공지 목록을 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchNotices();
  }, [fetchNotices]);

  const filtered = useMemo(() => {
    const list =
      filter === "all"
        ? notices
        : notices.filter((n) => BACKEND_TO_KEY[n.noticeType] === filter);
    return [...list].sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0));
  }, [filter, notices]);

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

      {isLoading ? (
        <div className="nbp-empty">불러오는 중...</div>
      ) : filtered.length === 0 ? (
        <div className="nbp-empty">해당 유형의 공지가 없습니다.</div>
      ) : (
        filtered.map((n) => {
          const key = BACKEND_TO_KEY[n.noticeType] || "plain";
          return (
            <div key={n.noticeId} className={`nbp-card ${key}`}>
              <div className="nbp-icon">{TYPES[key].icon}</div>
              <div className="nbp-content">
                <div className="nbp-top">
                  <span className="nbp-title">{n.title}</span>
                  {n.pinned && <span className="nbp-pin">📌 고정</span>}
                </div>
                <div className="nbp-desc">{n.content}</div>
                <div className="nbp-footer">
                  {n.createdByName || "-"} | {new Date(n.createdAt).toLocaleString()}
                </div>
              </div>
            </div>
          );
        })
      )}
    </div>
  );
}

export default NoticeBoardPage;
