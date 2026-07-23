import React, { useEffect, useMemo, useState } from "react";
import { getIssues, updateIssueStatus, subscribeIssues } from "../utils/equipmentIssueStore";

const SEVERITY_STYLE = {
  low: { label: "낮음", cls: "ai-sev-low" },
  mid: { label: "보통", cls: "ai-sev-mid" },
  urgent: { label: "긴급", cls: "ai-sev-urgent" },
};

const STATUS_FLOW = [
  { key: "received", label: "접수됨", cls: "ai-status-received" },
  { key: "checking", label: "확인중", cls: "ai-status-checking" },
  { key: "resolved", label: "해결됨", cls: "ai-status-resolved" },
];

const STATUS_FILTERS = [{ key: "all", label: "전체" }, ...STATUS_FLOW];

function AdminEquipmentIssuePage() {
  const [issues, setIssues] = useState([]);
  const [statusFilter, setStatusFilter] = useState("all");

  useEffect(() => {
    setIssues(getIssues());
    const unsubscribe = subscribeIssues(setIssues);
    return unsubscribe;
  }, []);

  const filtered = useMemo(() => {
    const list =
      statusFilter === "all" ? issues : issues.filter((i) => i.status === statusFilter);
    return [...list].sort((a, b) => b.createdAt - a.createdAt);
  }, [issues, statusFilter]);

  const summary = useMemo(() => {
    return {
      total: issues.length,
      received: issues.filter((i) => i.status === "received").length,
      checking: issues.filter((i) => i.status === "checking").length,
      resolved: issues.filter((i) => i.status === "resolved").length,
      urgentOpen: issues.filter((i) => i.severity === "urgent" && i.status !== "resolved").length,
    };
  }, [issues]);

  const styles = `
    .ai { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; }

    .ai-header { margin-bottom: 20px; }
    .ai-header h2 { margin: 0; font-size: 22px; font-weight: 800; color: #0f172a; }
    .ai-header p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; }

    .ai-summary-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 20px; }
    @media (max-width: 1000px) { .ai-summary-grid { grid-template-columns: repeat(2, 1fr); } }
    .ai-summary-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; }
    .ai-summary-card .num { font-size: 22px; font-weight: 800; color: #0f172a; }
    .ai-summary-card .lbl { font-size: 11.5px; color: #64748b; font-weight: 600; margin-top: 4px; }
    .ai-summary-card.received .num { color: #64748b; }
    .ai-summary-card.checking .num { color: #a16207; }
    .ai-summary-card.resolved .num { color: #16803d; }
    .ai-summary-card.urgent .num { color: #dc2626; }

    .ai-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 22px; box-shadow: 0 1px 3px rgba(0,0,0,0.03); }

    .ai-filter-row { display: flex; gap: 8px; margin-bottom: 18px; flex-wrap: wrap; }
    .ai-filter-btn { border: 1.5px solid #e2e8f0; background: #fff; color: #64748b; padding: 7px 14px; border-radius: 999px; font-size: 12px; font-weight: 700; cursor: pointer; }
    .ai-filter-btn.active { background: #0566d9; border-color: #0566d9; color: #fff; }

    .ai-item { padding: 16px 4px; border-top: 1px solid #f1f5f9; }
    .ai-item:first-child { border-top: none; }
    .ai-item-top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
    .ai-item-eq { font-size: 14px; font-weight: 800; color: #0f172a; }
    .ai-item-type { font-size: 12.5px; color: #64748b; }
    .ai-item-reporter { font-size: 11.5px; color: #94a3b8; margin-left: auto; }
    .ai-item-note { font-size: 13px; color: #334155; line-height: 1.5; margin: 6px 0; }
    .ai-item-photo { font-size: 12px; color: #64748b; margin-bottom: 6px; }
    .ai-item-thumb { display: block; max-width: 220px; max-height: 160px; border-radius: 8px; border: 1px solid #e2e8f0; margin-bottom: 8px; object-fit: cover; cursor: zoom-in; }
    .ai-item-bottom { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin-top: 8px; }
    .ai-item-time { font-size: 11.5px; color: #94a3b8; }

    .ai-tag { font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 12px; white-space: nowrap; }
    .ai-sev-low { background: #dcfce7; color: #16803d; }
    .ai-sev-mid { background: #fef9c3; color: #a16207; }
    .ai-sev-urgent { background: #fee2e2; color: #b91c1c; }
    .ai-status-received { background: #f1f5f9; color: #64748b; }
    .ai-status-checking { background: #fef9c3; color: #a16207; }
    .ai-status-resolved { background: #dcfce7; color: #16803d; }

    .ai-status-actions { display: flex; gap: 6px; }
    .ai-status-btn { border: 1.5px solid #e2e8f0; background: #fff; color: #64748b; padding: 6px 12px; border-radius: 8px; font-size: 11.5px; font-weight: 700; cursor: pointer; }
    .ai-status-btn:hover { border-color: #94a3b8; }
    .ai-status-btn.current.received { border-color: #64748b; background: #f1f5f9; color: #334155; }
    .ai-status-btn.current.checking { border-color: #a16207; background: #fef9c3; color: #a16207; }
    .ai-status-btn.current.resolved { border-color: #16803d; background: #dcfce7; color: #16803d; }

    .ai-empty { text-align: center; padding: 30px; color: #94a3b8; font-size: 13px; }
  `;

  return (
    <div className="ai">
      <style>{styles}</style>

      <div className="ai-header">
        <h2>🛠 설비 이상 신고 관리</h2>
        <p>사원이 접수한 설비 이상 신고를 확인하고 처리 상태를 갱신하세요.</p>
      </div>

      <div className="ai-summary-grid">
        <div className="ai-summary-card">
          <div className="num">{summary.total}건</div>
          <div className="lbl">전체 신고</div>
        </div>
        <div className="ai-summary-card received">
          <div className="num">{summary.received}건</div>
          <div className="lbl">접수됨</div>
        </div>
        <div className="ai-summary-card checking">
          <div className="num">{summary.checking}건</div>
          <div className="lbl">확인중</div>
        </div>
        <div className="ai-summary-card resolved">
          <div className="num">{summary.resolved}건</div>
          <div className="lbl">해결됨</div>
        </div>
        <div className="ai-summary-card urgent">
          <div className="num">{summary.urgentOpen}건</div>
          <div className="lbl">미해결 긴급</div>
        </div>
      </div>

      <div className="ai-card">
        <div className="ai-filter-row">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.key}
              type="button"
              className={`ai-filter-btn ${statusFilter === f.key ? "active" : ""}`}
              onClick={() => setStatusFilter(f.key)}
            >
              {f.label}
            </button>
          ))}
        </div>

        {filtered.length === 0 ? (
          <div className="ai-empty">해당 조건의 신고가 없습니다.</div>
        ) : (
          filtered.map((issue) => (
            <div key={issue.id} className="ai-item">
              <div className="ai-item-top">
                <span className="ai-item-eq">{issue.equipment}</span>
                <span className="ai-item-type">· {issue.issueType}</span>
                <span className={`ai-tag ${SEVERITY_STYLE[issue.severity].cls}`}>
                  {SEVERITY_STYLE[issue.severity].label}
                </span>
                <span className="ai-item-reporter">신고자: {issue.reporter}</span>
              </div>
              <div className="ai-item-note">{issue.note}</div>
              {issue.photoDataUrl ? (
                <a href={issue.photoDataUrl} target="_blank" rel="noopener noreferrer" title="원본 크기로 보기">
                  <img className="ai-item-thumb" src={issue.photoDataUrl} alt={issue.photoName || "첨부 사진"} />
                </a>
              ) : (
                issue.photoName && <div className="ai-item-photo">📎 {issue.photoName}</div>
              )}

              <div className="ai-item-bottom">
                <span className="ai-item-time">{issue.time}</span>
                <div className="ai-status-actions">
                  {STATUS_FLOW.map((s) => (
                    <button
                      key={s.key}
                      type="button"
                      className={`ai-status-btn ${issue.status === s.key ? `current ${s.key}` : ""}`}
                      onClick={() => updateIssueStatus(issue.id, s.key)}
                    >
                      {s.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export default AdminEquipmentIssuePage;