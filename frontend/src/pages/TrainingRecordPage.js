import React, { useMemo, useState } from "react";

// -------------------------------------------------------------
// [데이터 정의] 더미 교육/자격 데이터
// 실제 연동 시 HR/교육관리 API에서 이수 기록과 자격증 만료일을 받아오면 됩니다.
// -------------------------------------------------------------
const trainings = [
  { id: 1, name: "정기 안전 보건 교육 (2분기)", date: "2026-06-25", hours: "4h", validUntil: "2026-09-25", status: "valid" },
  { id: 2, name: "신규 정전기 방지 장비 사용법", date: "2026-07-10", hours: "1h", validUntil: "-", status: "valid" },
  { id: 3, name: "화재 대피 훈련", date: "2026-05-14", hours: "2h", validUntil: "2026-08-14", status: "soon" },
  { id: 4, name: "MSDS(물질안전보건자료) 교육", date: "2025-12-02", hours: "2h", validUntil: "2026-06-02", status: "expired" },
  { id: 5, name: "신입 사원 공정 안전 교육", date: "2025-03-04", hours: "8h", validUntil: "-", status: "valid" },
];

const certifications = [
  { id: 1, name: "전기기능사", issued: "2023-02-10", expiry: "2028-02-09", status: "valid" },
  { id: 2, name: "지게차운전기능사", issued: "2021-11-20", expiry: "2026-08-20", status: "soon" },
  { id: 3, name: "산업안전기사", issued: "2020-05-15", expiry: "2025-05-14", status: "expired" },
];

const STATUS_META = {
  valid: { label: "유효", cls: "tr-valid" },
  soon: { label: "만료 임박", cls: "tr-soon" },
  expired: { label: "만료됨", cls: "tr-expired" },
};

function TrainingRecordPage() {
  const [tab, setTab] = useState("training"); // "training" | "cert"

  const isAdmin = localStorage.getItem("userRole") === "admin";

  const summary = useMemo(() => {
    const soonCount =
      trainings.filter((t) => t.status === "soon").length +
      certifications.filter((c) => c.status === "soon").length;
    const expiredCount =
      trainings.filter((t) => t.status === "expired").length +
      certifications.filter((c) => c.status === "expired").length;
    return { trainingCount: trainings.length, certCount: certifications.length, soonCount, expiredCount };
  }, []);

  const styles = `
    .tr { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; }

    .tr-header { margin-bottom: 20px; }
    .tr-header h2 { margin: 0; font-size: 22px; font-weight: 800; color: #0f172a; }
    .tr-header p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; }

    .tr-summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 22px; }
    @media (max-width: 900px) { .tr-summary-grid { grid-template-columns: repeat(2, 1fr); } }
    .tr-summary-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; }
    .tr-summary-card .num { font-size: 22px; font-weight: 800; color: #0f172a; }
    .tr-summary-card .lbl { font-size: 11.5px; color: #64748b; font-weight: 600; margin-top: 4px; }
    .tr-summary-card.soon .num { color: #a16207; }
    .tr-summary-card.expired .num { color: #dc2626; }

    .tr-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 22px; box-shadow: 0 1px 3px rgba(0,0,0,0.03); }

    .tr-tabs { display: flex; gap: 8px; margin-bottom: 18px; border-bottom: 1px solid #e2e8f0; }
    .tr-tab-btn { padding: 10px 4px; margin-right: 18px; border: none; background: none; font-size: 14px; font-weight: 700; color: #94a3b8; cursor: pointer; border-bottom: 2px solid transparent; }
    .tr-tab-btn.active { color: #0566d9; border-bottom-color: #0566d9; }

    .tr-table { width: 100%; border-collapse: collapse; text-align: left; }
    .tr-table th { background: #f8fafc; color: #64748b; font-weight: 700; font-size: 12px; padding: 12px 14px; border-bottom: 1px solid #e2e8f0; }
    .tr-table td { padding: 13px 14px; border-bottom: 1px solid #f1f5f9; font-size: 13px; color: #1e293b; }
    .tr-table tbody tr:last-child td { border-bottom: none; }
    .tr-table tbody tr:hover td { background: #f8fafc; }

    .tr-tag { font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 12px; white-space: nowrap; }
    .tr-valid { background: #dcfce7; color: #16803d; }
    .tr-soon { background: #fef9c3; color: #a16207; }
    .tr-expired { background: #fee2e2; color: #b91c1c; }

    .tr-notice { margin-top: 16px; background: #fefce8; border: 1px solid #fef08a; border-radius: 8px; padding: 12px 14px; font-size: 12.5px; color: #854d0e; }
  `;

  return (
    <div className="tr">
      <style>{styles}</style>

      <div className="tr-header">
        <h2>🎓 교육 · 자격 이력</h2>
        <p>이수한 안전 교육과 보유 자격증의 유효기간을 확인할 수 있습니다.</p>
      </div>

      <div className="tr-summary-grid">
        <div className="tr-summary-card">
          <div className="num">{summary.trainingCount}건</div>
          <div className="lbl">이수한 교육</div>
        </div>
        <div className="tr-summary-card">
          <div className="num">{summary.certCount}개</div>
          <div className="lbl">보유 자격증</div>
        </div>
        <div className="tr-summary-card soon">
          <div className="num">{summary.soonCount}건</div>
          <div className="lbl">만료 임박</div>
        </div>
        <div className="tr-summary-card expired">
          <div className="num">{summary.expiredCount}건</div>
          <div className="lbl">만료됨 · 재이수 필요</div>
        </div>
      </div>

      {!isAdmin && (summary.soonCount > 0 || summary.expiredCount > 0) && (
        <div className="tr-notice" style={{ marginBottom: 16 }}>
          ⚠ 만료되었거나 곧 만료되는 항목이 있습니다. 재교육 또는 갱신 일정을 관리자와 확인해주세요.
        </div>
      )}

      <div className="tr-card">
        <div className="tr-tabs">
          <button
            type="button"
            className={`tr-tab-btn ${tab === "training" ? "active" : ""}`}
            onClick={() => setTab("training")}
          >
            이수 교육 ({trainings.length})
          </button>
          <button
            type="button"
            className={`tr-tab-btn ${tab === "cert" ? "active" : ""}`}
            onClick={() => setTab("cert")}
          >
            보유 자격증 ({certifications.length})
          </button>
        </div>

        {tab === "training" ? (
          <table className="tr-table">
            <thead>
              <tr>
                <th>교육명</th>
                <th>이수일</th>
                <th>이수 시간</th>
                <th>유효기간</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {trainings.map((t) => (
                <tr key={t.id}>
                  <td>{t.name}</td>
                  <td>{t.date}</td>
                  <td>{t.hours}</td>
                  <td>{t.validUntil}</td>
                  <td><span className={`tr-tag ${STATUS_META[t.status].cls}`}>{STATUS_META[t.status].label}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <table className="tr-table">
            <thead>
              <tr>
                <th>자격증명</th>
                <th>취득일</th>
                <th>만료일</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {certifications.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td>{c.issued}</td>
                  <td>{c.expiry}</td>
                  <td><span className={`tr-tag ${STATUS_META[c.status].cls}`}>{STATUS_META[c.status].label}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default TrainingRecordPage;