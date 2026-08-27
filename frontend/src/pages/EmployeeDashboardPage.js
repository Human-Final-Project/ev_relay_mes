import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { getHandovers, saveHandover, subscribeHandovers } from "../utils/handoverStore";

// -------------------------------------------------------------
// [데이터 정의] 더미 데이터
// -------------------------------------------------------------
const STATUS_STYLE = {
  "진행중": { badge: "badge-qa", label: "진행중" },
  "대기": { badge: "badge-idle", label: "대기" },
  "완료": { badge: "badge-running", label: "완료" },
};

const workOrders = [
  { id: "WO-2023-0041", product: "EV-Relay Type-B (High Voltage)", current: 450, target: 600, due: "16:30", status: "진행중" },
  { id: "WO-2023-0042", product: "EV-Relay Type-A (Standard)", current: 0, target: 400, due: "18:00", status: "대기" },
  { id: "WO-2023-0039", product: "Auxiliary Connector Unit", current: 200, target: 200, due: "완료", status: "완료" },
];

const notices = [
  {
    id: 1,
    urgent: true,
    icon: "⚠️",
    title: "필독: 라인 클리닝 일정 변경",
    desc: "오후 3시 예정되었던 정기 클리닝이 설비 점검으로 인해 4시로 연기되었습니다.",
    footer: "최관리 팀장 | 14:10",
  },
  {
    id: 2,
    urgent: false,
    icon: "🦺",
    title: "신규 안전 보호구 착용 안내",
    desc: "내일부터 지급되는 신형 정전기 방지 장갑을 필히 착용해주시기 바랍니다.",
    footer: "품질관리팀 | 09:20",
  },
];

// -------------------------------------------------------------
// [CSS 스타일 정의] - 관리자 DashboardPage.js와 동일한 색상/컴포넌트 규칙 재사용
// -------------------------------------------------------------
const styles = `
  .mesdash-container {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    color: #333333;
    background-color: #f8fafc;
    padding: 24px;
    min-height: 100vh;
    box-sizing: border-box;
  }

  .btn-outline {
    background: #ffffff;
    border: 1px solid #cbd5e1;
    padding: 8px 16px;
    border-radius: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #475569;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .btn-primary {
    background: #02639a;
    border: none;
    padding: 8px 16px;
    border-radius: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #ffffff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    transition: background 0.15s;
  }
  .btn-primary:hover { background: #014c77; }
  .btn-primary:disabled { background: #94a3b8; cursor: not-allowed; }

  .link-more { font-size: 13px; color: #02639a; text-decoration: none; font-weight: bold; cursor: pointer; background: none; border: none; }
  .link-more:hover { text-decoration: underline; }

  /* 작업자 프로필 */
  .profile-section {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    padding: 24px;
    margin-bottom: 24px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.02);
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 16px;
  }
  .profile-left { display: flex; align-items: center; gap: 16px; }
  .profile-icon-circle {
    width: 52px; height: 52px; border-radius: 50%;
    background-color: #dbeafe; color: #1d4ed8;
    display: flex; align-items: center; justify-content: center;
    font-size: 22px; flex-shrink: 0;
  }
  .profile-title { font-size: 15px; font-weight: 800; color: #1e293b; margin-bottom: 6px; }
  .profile-meta { display: flex; flex-wrap: wrap; gap: 16px; font-size: 13px; color: #64748b; font-weight: 600; }
  .profile-meta strong { color: #1e293b; font-weight: 700; }
  .profile-actions { display: flex; gap: 10px; }

  /* 4분할 요약 카드 (summary-card 재사용, 4열로 확장) */
  .summary-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 16px;
    margin-bottom: 24px;
  }
  @media (max-width: 1000px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  .summary-card {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    padding: 20px;
    position: relative;
    box-shadow: 0 1px 3px rgba(0,0,0,0.02);
  }
  .summary-card .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
  .summary-card .card-header .label { font-size: 13px; font-weight: 600; color: #64748b; }
  .summary-card .card-header .icon { font-size: 16px; }
  .summary-card .value { font-size: 26px; font-weight: 800; color: #0f172a; margin-bottom: 8px; }
  .summary-card .value span { font-size: 13px; font-weight: 500; color: #64748b; margin-left: 4px; }
  .summary-card .trend { font-size: 12px; font-weight: 600; }
  .summary-card .trend.up { color: #0284c7; }
  .summary-card .trend.down { color: #dc2626; }
  .mini-progress-bg { background: #e2e8f0; height: 6px; border-radius: 3px; overflow: hidden; margin-top: 12px; }
  .mini-progress-fill { background: #02639a; height: 100%; }

  /* 공통 섹션 카드 */
  .section-card {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    padding: 24px;
    margin-bottom: 24px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.02);
  }
  .section-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
  .section-card-header h3 { margin: 0; font-size: 18px; font-weight: 800; color: #1e293b; }

  .main-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 24px; align-items: start; }
  @media (max-width: 1000px) { .main-grid { grid-template-columns: 1fr; } }

  /* 작업 지시서 테이블 */
  .wo-table-card { padding: 8px; }
  .wo-table { width: 100%; border-collapse: collapse; text-align: left; }
  .wo-table th { background: #f8fafc; color: #64748b; font-weight: 700; font-size: 12px; padding: 14px 16px; border-bottom: 1px solid #e2e8f0; }
  .wo-table td { padding: 16px; border-bottom: 1px solid #e2e8f0; font-size: 13px; vertical-align: top; color: #1e293b; }
  .wo-table tbody tr:last-child td { border-bottom: none; }
  .wo-table tbody tr:hover td { background: #f8fafc; }
  .wo-id { font-weight: 700; color: #0f172a; font-family: monospace; }

  .status-badge { display: inline-block; padding: 4px 10px; border-radius: 12px; font-size: 11px; font-weight: 700; }
  .badge-running { background-color: #dcfce7; color: #15803d; }
  .badge-idle { background-color: #fef08a; color: #854d0e; }
  .badge-error { background-color: #fee2e2; color: #b91c1c; }
  .badge-qa { background-color: #dbeafe; color: #1d4ed8; }

  /* 관리자 공지 (alert-box 패턴 재사용) */
  .alert-box { border-radius: 8px; padding: 16px; display: flex; gap: 12px; margin-bottom: 12px; }
  .alert-box:last-child { margin-bottom: 0; }
  .alert-box-icon { font-size: 18px; margin-top: 2px; }
  .alert-box-content { flex: 1; }
  .alert-box-title { font-size: 13px; font-weight: 700; color: #1e293b; margin-bottom: 4px; }
  .alert-box-desc { font-size: 12px; color: #64748b; line-height: 1.4; margin-bottom: 8px; }
  .alert-box-footer { font-size: 11px; color: #94a3b8; }

  .alert-red { background-color: #fff1f2; border: 1px solid #fecdd3; }
  .alert-red .alert-box-icon { color: #dc2626; }
  .alert-blue { background-color: #eff6ff; border: 1px solid #bfdbfe; }
  .alert-blue .alert-box-icon { color: #2563eb; }

  /* 교대 인수인계 */
  .handover-textarea {
    width: 100%; box-sizing: border-box; min-height: 90px; padding: 10px 12px;
    border: 1px solid #cbd5e1; border-radius: 6px; font-size: 13px; font-family: inherit;
    outline: none; resize: vertical; margin-bottom: 12px; transition: border 0.15s;
  }
  .handover-textarea:focus { border-color: #02639a; }

  .handover-history { margin-top: 16px; padding-top: 14px; border-top: 1px solid #e2e8f0; }
  .handover-history-title { font-size: 12px; font-weight: 700; color: #64748b; margin-bottom: 10px; }
  .handover-empty { font-size: 12.5px; color: #94a3b8; text-align: center; padding: 16px 0; }
  .handover-item { padding: 10px 0; border-top: 1px solid #f1f5f9; }
  .handover-item:first-child { border-top: none; }
  .handover-item-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
  .handover-writer { font-size: 12.5px; font-weight: 700; color: #1e293b; }
  .handover-time { font-size: 11px; color: #94a3b8; }
  .handover-note { font-size: 12.5px; color: #475569; line-height: 1.5; white-space: pre-wrap; }
`;

function EmployeeDashboardPage() {
  const navigate = useNavigate();

  const worker = { nameKo: "김철수", nameEn: "Kim Cheol-su", line: "Line A", shift: "오전 (Morning)" };
  const stats = { goal: 1200, progress: 842, efficiency: 98.4, safeDays: 432, updatedAgo: "2분 전" };
  const achievedPct = Math.round((stats.progress / stats.goal) * 100);

  const [workStarted, setWorkStarted] = useState(false);
  const [handoverNote, setHandoverNote] = useState("");
  const [handovers, setHandovers] = useState([]);

  useEffect(() => {
    setHandovers(getHandovers());
    const unsubscribe = subscribeHandovers(setHandovers);
    return unsubscribe;
  }, []);

  // 같은 라인에 남겨진 인계 기록만 보여줍니다 (다른 라인 사원과 섞이지 않도록)
  const lineHandovers = handovers.filter((h) => h.line === worker.line);

  const handleStartWork = () => {
    setWorkStarted(true);
    alert(`${worker.nameKo}님, ${worker.line} 작업을 시작합니다.`);
  };

  const handleSaveHandover = () => {
    if (!handoverNote.trim()) {
      alert("인계할 특이사항을 입력해주세요.");
      return;
    }
    saveHandover({ line: worker.line, writer: worker.nameKo, note: handoverNote });
    alert("다음 근무자에게 인계 사항이 저장되었습니다.");
    setHandoverNote("");
  };

  return (
    <div className="mesdash-container">
      <style>{styles}</style>

      {/* 작업자 프로필 */}
      <div className="profile-section">
        <div className="profile-left">
          <div className="profile-icon-circle">🧑‍🔧</div>
          <div>
            <div className="profile-title">작업자 프로필 (Worker Profile)</div>
            <div className="profile-meta">
              <span>👤 <strong>{worker.nameKo}</strong> ({worker.nameEn})</span>
              <span>📅 배정 라인: <strong>{worker.line}</strong></span>
              <span>🕐 근무 시프트: <strong>{worker.shift}</strong></span>
            </div>
          </div>
        </div>
        <div className="profile-actions">
          <button className="btn-primary" onClick={handleStartWork} disabled={workStarted}>
            {workStarted ? "✓ 작업 진행중" : "➦ 작업 시작"}
          </button>
          <button className="btn-outline" onClick={() => navigate("/mypage")}>정보 수정</button>
        </div>
      </div>

      {/* 4분할 요약 카드 */}
      <div className="summary-grid">
        <div className="summary-card">
          <div className="card-header"><span className="label">금일 목표 (GOAL)</span><span className="icon">🎯</span></div>
          <div className="value">{stats.goal.toLocaleString()} <span>Units</span></div>
          <div className="mini-progress-bg"><div className="mini-progress-fill" style={{ width: "100%" }}></div></div>
        </div>

        <div className="summary-card">
          <div className="card-header"><span className="label">현재 진행 (PROGRESS)</span><span className="icon">📈</span></div>
          <div className="value">{stats.progress.toLocaleString()} <span>/ {stats.goal.toLocaleString()}</span></div>
          <div className="trend up">↗ {achievedPct}% 달성 · {stats.updatedAgo}</div>
        </div>

        <div className="summary-card">
          <div className="card-header"><span className="label">개인 효율 (EFFICIENCY)</span><span className="icon">⚡</span></div>
          <div className="value">{stats.efficiency}<span>%</span></div>
          <div className="trend up">↗ 목표치 상회</div>
        </div>

        <div className="summary-card">
          <div className="card-header"><span className="label">무재해 기록 (SAFE DAYS)</span><span className="icon">🛡️</span></div>
          <div className="value">{stats.safeDays} <span>Days</span></div>
          <div className="trend up">● 안전 작업 준수 중</div>
        </div>
      </div>

      {/* 작업 지시서 + 교대 인수인계 (왼쪽) / 관리자 공지 (오른쪽) */}
      <div className="main-grid">
        <div>
          <div className="section-card wo-table-card">
            <div className="section-card-header" style={{ padding: "16px 16px 0" }}>
              <h3>배정된 작업 지시서 (Work Orders)</h3>
              <button className="link-more" onClick={() => navigate("/production")}>전체 보기 ❯</button>
            </div>
            <table className="wo-table">
              <thead>
                <tr>
                  <th>지시 번호 (ID)</th>
                  <th>제품명 (PRODUCT)</th>
                  <th>수량 (QTY)</th>
                  <th>기한 (DUE)</th>
                  <th>상태 (STATUS)</th>
                </tr>
              </thead>
              <tbody>
                {workOrders.map((wo) => (
                  <tr key={wo.id}>
                    <td className="wo-id">{wo.id}</td>
                    <td>{wo.product}</td>
                    <td>{wo.current} / {wo.target}</td>
                    <td>{wo.due}</td>
                    <td><span className={`status-badge ${STATUS_STYLE[wo.status].badge}`}>{STATUS_STYLE[wo.status].label}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 교대 인수인계 */}
          <div className="section-card" style={{ marginBottom: 0 }}>
            <div className="section-card-header">
              <h3>📋 교대 인수인계</h3>
            </div>
            <textarea
              className="handover-textarea"
              placeholder="다음 근무자를 위한 특이사항을 입력하세요..."
              value={handoverNote}
              onChange={(e) => setHandoverNote(e.target.value)}
            />
            <button className="btn-primary" style={{ width: "100%" }} onClick={handleSaveHandover}>
              인계 사항 저장
            </button>

            <div className="handover-history">
              <div className="handover-history-title">이전 근무자의 인계 기록</div>
              {lineHandovers.length === 0 ? (
                <div className="handover-empty">아직 남겨진 인계 사항이 없습니다.</div>
              ) : (
                lineHandovers.slice(0, 5).map((h) => (
                  <div key={h.id} className="handover-item">
                    <div className="handover-item-top">
                      <span className="handover-writer">{h.writer}</span>
                      <span className="handover-time">{h.time}</span>
                    </div>
                    <div className="handover-note">{h.note}</div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* 관리자 공지 */}
        <div className="section-card" style={{ marginBottom: 0 }}>
          <div className="section-card-header">
            <h3>📢 관리자 공지</h3>
            <button className="link-more" onClick={() => navigate("/notices")}>전체 보기 ❯</button>
          </div>
          {notices.slice(0, 2).map((n) => (
            <div key={n.id} className={`alert-box ${n.urgent ? "alert-red" : "alert-blue"}`}>
              <div className="alert-box-icon">{n.icon}</div>
              <div className="alert-box-content">
                <div className="alert-box-title">{n.title}</div>
                <div className="alert-box-desc">{n.desc}</div>
                <div className="alert-box-footer">{n.footer}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default EmployeeDashboardPage;