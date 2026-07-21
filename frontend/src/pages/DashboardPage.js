import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

function DashboardPage() {
  const [activeTab] = useState("Overview");
  const navigate = useNavigate();
  
  // 로그인된 사용자의 권한 상태를 관리합니다. (기본값은 'user' 혹은 'admin')
  // 💡 버튼이 잘 작동하는지 보시려면 이 값을 "admin"으로 설정하여 확인하실 수 있습니다.
  const [userRole, setUserRole] = useState("admin");

  // 💡 [기존] 알림 모달 제어를 위한 상태 State
  const [isAlertModalOpen, setIsAlertModalOpen] = useState(false);

  // 💡 [신규] 신규 작업 지시 모달 제어를 위한 상태 State
  const [isOrderModalOpen, setIsOrderModalOpen] = useState(false);

  useEffect(() => {
    const storedRole = localStorage.getItem("userRole") || "admin"; 
    setUserRole(storedRole);
  }, []);

  // 💡 알림 모달에 표시할 데이터
  const allAlerts = [
    { id: 1, type: "error", title: "Line C - 모터 과열", desc: "긴급 장비 점검 필요. 생산이 일시 중단되었습니다.", time: "방금 전", dept: "유지보수팀 호출", icon: "⚠️" },
    { id: 2, type: "warning", title: "자재 부족 (RP-Sensor)", desc: "Line B의 원자재 잔량이 15% 미만입니다.", time: "15분 전", dept: "창고 요청 발송", icon: "📦" },
    { id: 3, type: "success", title: "품질 검사 완료", desc: "Line A 루트 #A-2024 품질 검사 통과.", time: "1시간 전", dept: "QA 승인", icon: "✅" },
    { id: 4, type: "warning", title: "Line B - 컨베이어 속도 저하", desc: "컨베이어 속도가 표준 이하로 감속되어 병목 현상이 예상됩니다.", time: "2시간 전", dept: "오퍼레이터 확인 요망", icon: "📦" },
    { id: 5, type: "error", title: "Line A - 압력 제어 이상", desc: "압력 한계치 초과 (6.8 bar). 즉시 밸브 제어 필요.", time: "3시간 전", dept: "생산 기술팀 긴급 전달", icon: "⚠️" },
    { id: 6, type: "success", title: "정기 설비 점검 완료", desc: "Line C 로봇 팔 그리스 주입 및 센서 보정이 정상 완료되었습니다.", time: "오늘 오전", dept: "종합 설비 관리", icon: "✅" }
  ];

  const chartData = [
    { date: "11.15", target: "50%", actual: "45%" },
    { date: "11.14", target: "90%", actual: "88%" },
    { date: "11.13", target: "80%", actual: "78%" },
    { date: "11.12", target: "85%", actual: "82%" },
    { date: "11.11", target: "60%", actual: "55%" },
  ];

  const styles = `
    .mesdash-container {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      color: #333333;
      background-color: #f8fafc;
      padding: 24px;
      min-height: 100vh;
      box-sizing: border-box;
    }
    
    /* 대시보드 헤더 */
    .mesdash-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      margin-bottom: 24px;
    }
    .mesdash-header .title-area h2 {
      margin: 0 0 6px 0;
      font-size: 18px;
      font-weight: 700;
      color: #1e293b;
    }
    .mesdash-header .title-area p {
      margin: 0;
      font-size: 13px;
      color: #64748b;
    }
    .mesdash-header .btn-group {
      display: flex;
      gap: 12px;
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
      gap: 6px;
      transition: background 0.15s;
    }
    .btn-primary:hover {
      background: #014c77;
    }

    /* 3개 요약 카드 그리드 */
    .summary-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }
    .summary-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 20px;
      position: relative;
      box-shadow: 0 1px 3px rgba(0,0,0,0.02);
    }
    .summary-card .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }
    .summary-card .card-header .label {
      font-size: 13px;
      font-weight: 600;
      color: #64748b;
    }
    .summary-card .card-header .icon {
      font-size: 16px;
    }
    .summary-card .value {
      font-size: 28px;
      font-weight: 800;
      color: #0f172a;
      margin-bottom: 8px;
    }
    .summary-card .value span {
      font-size: 14px;
      font-weight: 500;
      color: #64748b;
      margin-left: 4px;
    }
    .summary-card .trend {
      font-size: 12px;
      font-weight: 600;
    }
    .summary-card .trend.up { color: #0284c7; }
    .summary-card .trend.down { color: #dc2626; }
    
    .mini-progress-bg {
      background: #e2e8f0;
      height: 6px;
      border-radius: 3px;
      overflow: hidden;
      margin-top: 12px;
    }
    .mini-progress-fill {
      background: #02639a;
      height: 100%;
    }

    /* 공통 카드 스타일 */
    .section-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 24px;
      margin-bottom: 24px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.02);
    }
    .section-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }
    .section-card-header h3 {
      margin: 0;
      font-size: 18px;
      font-weight: 800;
      color: #1e293b;
    }
    .section-card-header .subtitle {
      font-size: 12px;
      color: #64748b;
      margin-top: 4px;
    }
    .section-card-header .legend {
      display: flex;
      gap: 16px;
      font-size: 12px;
      font-weight: 700;
      color: #1e293b;
    }
    .legend-item {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .legend-box {
      width: 14px;
      height: 14px;
      border-radius: 3px;
    }

    /* 세로 막대 그래프 디자인 */
    .chart-container {
      display: flex;
      align-items: flex-end;
      justify-content: space-around;
      height: 200px;
      border-bottom: 1px solid #e2e8f0;
      padding-bottom: 12px;
      margin-top: 20px;
    }
    .chart-bar-col {
      display: flex;
      flex-direction: column;
      align-items: center;
      height: 100%;
      justify-content: flex-end;
      width: 60px;
    }
    .bar-wrapper {
      display: flex;
      align-items: flex-end;
      gap: 4px;
      height: 100%;
      width: 100%;
      justify-content: center;
    }
    .bar-single {
      width: 20px;
      border-top-left-radius: 6px;
      border-top-right-radius: 6px;
      transition: height 0.3s ease;
    }
    .bar-single.target {
      background-color: #60a5fa;
    }
    .bar-single.actual {
      background-color: #066a3e;
    }
    .chart-date {
      margin-top: 12px;
      font-size: 13px;
      font-weight: 700;
      color: #475569;
    }

    /* 실시간 작업 현황 */
    .pipeline-wrapper {
      display: flex;
      align-items: center;
      gap: 10px;
      background: #fdfdfd;
      padding: 20px;
      border-radius: 8px;
    }
    .pipeline-left-branch {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .pipeline-connector-svg {
      width: 60px;
      height: 180px;
      flex-shrink: 0;
    }
    .pipeline-right-stream {
      display: flex;
      align-items: center;
      gap: 16px;
      flex: 1;
    }
    
    .pipeline-card {
      width: 170px;
      background: #ffffff;
      border-radius: 8px;
      padding: 16px;
      text-align: center;
      box-shadow: 0 1px 3px rgba(0,0,0,0.05);
      box-sizing: border-box;
    }
    .pipeline-card .title {
      font-size: 14px;
      font-weight: 700;
      color: #1e293b;
      margin-bottom: 12px;
    }
    .pipeline-card .icon-circle {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      margin: 0 auto 12px auto;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
    }
    .pipeline-card .status-badge {
      display: inline-block;
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 11px;
      font-weight: 700;
      margin-bottom: 8px;
    }
    .pipeline-card .sub-desc {
      font-size: 11px;
      color: #94a3b8;
    }

    .card-running { border: 1.5px solid #22c55e; background-color: #f0fdf4; }
    .icon-running { background-color: #dcfce7; color: #15803d; }
    .badge-running { background-color: #dcfce7; color: #15803d; }
    
    .card-idle { border: 1.5px solid #eab308; background-color: #fef9c3; }
    .icon-idle { background-color: #fef08a; color: #854d0e; }
    .badge-idle { background-color: #fef08a; color: #854d0e; }

    .card-error { border: 1.5px solid #ef4444; background-color: #fef2f2; }
    .icon-error { background-color: #fee2e2; color: #b91c1c; }
    .badge-error { background-color: #fee2e2; color: #b91c1c; }

    .card-qa { border: 1.5px solid #3b82f6; background-color: #eff6ff; }
    .icon-qa { background-color: #dbeafe; color: #1d4ed8; }
    .badge-qa { background-color: #dbeafe; color: #1d4ed8; }

    .pipeline-arrow {
      color: #94a3b8;
      font-weight: bold;
      font-size: 18px;
      user-select: none;
    }

    /* 실시간 생산 알림 */
    .alert-header-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    .alert-count-badge {
      background-color: #fee2e2;
      color: #ef4444;
      font-size: 11px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 4px;
    }
    .alerts-flex-row {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px;
      margin-bottom: 20px;
    }
    .alert-box {
      border-radius: 8px;
      padding: 16px;
      display: flex;
      gap: 12px;
    }
    .alert-box-icon {
      font-size: 18px;
      margin-top: 2px;
    }
    .alert-box-content {
      flex: 1;
    }
    .alert-box-title {
      font-size: 13px;
      font-weight: 700;
      color: #1e293b;
      margin-bottom: 4px;
    }
    .alert-box-desc {
      font-size: 12px;
      color: #64748b;
      line-height: 1.4;
      margin-bottom: 8px;
    }
    .alert-box-footer {
      font-size: 11px;
      color: #94a3b8;
    }

    .alert-red { background-color: #fff1f2; border: 1px solid #fecdd3; }
    .alert-red .alert-box-icon { color: #dc2626; }
    .alert-yellow { background-color: #fefcbf; border: 1px solid #fef08a; }
    .alert-yellow .alert-box-icon { color: #d97706; }
    .alert-blue { background-color: #eff6ff; border: 1px solid #bfdbfe; }
    .alert-blue .alert-box-icon { color: #2563eb; }

    .btn-all-alerts {
      display: block;
      width: 100%;
      background: #eff6ff;
      border: none;
      color: #2563eb;
      padding: 10px;
      font-size: 12px;
      font-weight: 700;
      border-radius: 6px;
      cursor: pointer;
      text-align: center;
    }
    .btn-all-alerts:hover {
      background: #dbeafe;
    }

    .link-more {
      font-size: 13px;
      color: #02639a;
      text-decoration: none;
      font-weight: bold;
      cursor: pointer;
      background: none;
      border: none;
      padding: 0;
    }
    .link-more:hover {
      text-decoration: underline;
    }

    /* =========================================================================
       전체 알림 내역 모달창 오버레이 및 타임라인 CSS
       ========================================================================= */
    .alert-modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(15, 23, 42, 0.4);
      backdrop-filter: blur(5px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 99999;
      animation: alertFadeIn 0.2s ease-out;
    }
    .alert-modal-wrapper {
      background: #ffffff;
      width: 100%;
      max-width: 580px;
      max-height: 75vh;
      border-radius: 12px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.15);
      border: 1px solid #e2e8f0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      animation: alertScaleUp 0.25s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .alert-modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 18px 24px;
      border-bottom: 1px solid #f1f5f9;
    }
    .alert-modal-title-group {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .alert-modal-title { font-size: 15px; font-weight: 800; color: #0f172a; }
    .modal-count-badge {
      background: #fee2e2;
      color: #ef4444;
      font-size: 11px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 10px;
    }
    .alert-modal-close-x {
      background: transparent;
      border: none;
      font-size: 18px;
      color: #94a3b8;
      cursor: pointer;
    }
    .alert-modal-body {
      padding: 24px;
      overflow-y: auto;
      flex: 1;
    }
    .alert-modal-subtext {
      font-size: 12px;
      color: #64748b;
      margin: 0 0 16px 0;
    }
    .alert-history-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .alert-history-item {
      display: flex;
      gap: 14px;
      padding: 14px;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
      background: #ffffff;
    }
    .state-error { border-left: 4px solid #ef4444; background-color: #fff1f2; }
    .state-warning { border-left: 4px solid #eab308; background-color: #fffde6; }
    .state-success { border-left: 4px solid #22c55e; background-color: #f0fdf4; }

    .history-icon { font-size: 18px; }
    .history-details { flex: 1; display: flex; flex-direction: column; gap: 3px; }
    .history-row { display: flex; justify-content: space-between; align-items: center; }
    .history-title { font-size: 13px; font-weight: 700; color: #1e293b; }
    .history-time { font-size: 11px; color: #94a3b8; }
    .history-desc { margin: 0; font-size: 12px; color: #475569; line-height: 1.4; }
    .history-meta { font-size: 11px; color: #64748b; margin-top: 2px; }

    .alert-modal-footer {
      padding: 14px 24px;
      border-top: 1px solid #f1f5f9;
      display: flex;
      justify-content: flex-end;
      background: #f8fafc;
    }
    .btn-alert-close {
      background: #02639a;
      color: white;
      border: none;
      padding: 8px 16px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
    }

    @keyframes alertFadeIn { from { opacity: 0; } to { opacity: 1; } }
    @keyframes alertScaleUp { from { transform: scale(0.97); opacity: 0; } to { transform: scale(1); opacity: 1; } }

    /* =========================================================================
       신규 작업 지시 발행 모달 (Work Order Modal) CSS
       ========================================================================= */
    .order-modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(15, 23, 42, 0.4);
      backdrop-filter: blur(5px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 99999;
      animation: alertFadeIn 0.2s ease-out;
    }
    .order-modal-wrapper {
      background: #ffffff;
      width: 100%;
      max-width: 500px;
      border-radius: 12px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.15);
      border: 1px solid #e2e8f0;
      overflow: hidden;
      animation: alertScaleUp 0.25s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .order-modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 18px 24px;
      border-bottom: 1px solid #f1f5f9;
    }
    .order-modal-title-group {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .order-modal-title { font-size: 15px; font-weight: 800; color: #0f172a; }
    .order-modal-close-x {
      background: transparent;
      border: none;
      font-size: 18px;
      color: #94a3b8;
      cursor: pointer;
    }
    .order-modal-body {
      padding: 24px;
    }
    .order-modal-subtext {
      font-size: 12px;
      color: #64748b;
      margin: 0 0 20px 0;
      line-height: 1.5;
    }
    .order-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .order-form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .order-form-group label {
      font-size: 12px;
      font-weight: 700;
      color: #475569;
    }
    .order-form-group select,
    .order-form-group input {
      padding: 10px 12px;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 13px;
      outline: none;
      transition: border 0.15s;
    }
    .order-form-group select:focus,
    .order-form-group input:focus {
      border-color: #02639a;
    }
    .input-unit-wrapper {
      position: relative;
      display: flex;
      align-items: center;
    }
    .input-unit-wrapper input {
      width: 100%;
      padding-right: 50px;
    }
    .unit-text-badge {
      position: absolute;
      right: 12px;
      font-size: 12px;
      font-weight: 700;
      color: #94a3b8;
    }
    .order-modal-footer {
      padding: 16px 24px;
      border-top: 1px solid #f1f5f9;
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: #f8fafc;
    }
    .btn-order-cancel {
      background: transparent;
      border: none;
      color: #64748b;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      text-decoration: underline;
    }
    .btn-order-submit {
      background: #02639a;
      color: #ffffff;
      border: none;
      padding: 10px 20px;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 700;
      cursor: pointer;
    }
  `;

  // 신규 작업 등록을 제출했을 때 일어나는 동작
  const handleOrderSubmit = (e) => {
    e.preventDefault();
    alert("🚀 신규 작업 지시가 성공적으로 전송되었습니다!\n생산 라인 단말기로 지시서가 즉시 동기화됩니다.");
    setIsOrderModalOpen(false);
  };

  return (
    <>
      <style>{styles}</style>
      {activeTab === "Overview" && (
        <div className="mesdash-container">
          {/* 헤더 영역 */}
          <div className="mesdash-header">
            <div className="title-area">
              <h2>생산 관리 대시보드</h2>
              <p>2024년 5월 22일 수요일 | 실시간 공정 현황</p>
            </div>
            <div className="btn-group">
              <button className="btn-outline">📥 보고서 내보내기</button>
              
              {/* 💡 [수정] userRole이 admin일 때만 버튼 렌더링 (일반 유저일 때는 아예 노출되지 않고 숨겨짐) */}
              {userRole === "admin" && (
                <button className="btn-primary" onClick={() => setIsOrderModalOpen(true)}>
                  ➕ 신규 작업 지시
                </button>
              )}
            </div>
          </div>

          {/* 상단 3개 요약 카드 */}
          <div className="summary-grid">
            <div className="summary-card">
              <div className="card-header">
                <span className="label">금일 총 생산량</span>
                <span className="icon">📊</span>
              </div>
              <div className="value">
                12,480 <span>EA</span>
              </div>
              <div className="trend up">
                ↗ 전일 대비 4.2% 증가
              </div>
            </div>

            <div className="summary-card">
              <div className="card-header">
                <span className="label">실시간 완제품 수율</span>
                <span className="icon">📋</span>
              </div>
              <div className="value">
                91.2% <span>양품률</span>
              </div>
              <div className="mini-progress-bg">
                <div className="mini-progress-fill" style={{ width: "91.2%" }}></div>
              </div>
            </div>

            <div className="summary-card">
              <div className="card-header">
                <span className="label">실시간 불량률</span>
                <span className="icon" style={{ color: "#dc2626" }}>⚠️</span>
              </div>
              <div className="value" style={{ color: "#dc2626" }}>
                0.42%
              </div>
              <div className="trend down">
                ↘ 지난 시간 대비 0.05% 상승
              </div>
            </div>
          </div>

          {/* 목표 대비 생산 실적 현황 */}
          <div className="section-card">
            <div className="section-card-header">
              <div>
                <h3>목표 대비 생산 실적 현황</h3>
                <div className="subtitle">일일 생산 수율</div>
              </div>
              <div className="legend">
                <span className="legend-item">
                  목표 <div className="legend-box" style={{ backgroundColor: "#60a5fa" }}></div>
                </span>
                <span className="legend-item">
                  실적 <div className="legend-box" style={{ backgroundColor: "#066a3e" }}></div>
                </span>
              </div>
            </div>

            <div className="chart-container">
              {chartData.map((item, idx) => (
                <div key={idx} className="chart-bar-col">
                  <div className="bar-wrapper">
                    {item.target !== "0%" && (
                      <div
                        className="bar-single target"
                        style={{ height: item.target }}
                      ></div>
                    )}
                    <div
                      className="bar-single actual"
                      style={{ height: item.actual }}
                    ></div>
                  </div>
                  <span className="chart-date">{item.date}</span>
                </div>
              ))}
            </div>
          </div>

          {/* 실시간 작업 현황 */}
          <div className="section-card">
            <div className="section-card-header">
              <h3>실시간 작업 현황</h3>
              <button type="button" className="link-more" onClick={() => navigate("/production")}>
                상세 현황 ❯
              </button>
            </div>

            <div className="pipeline-wrapper">
              <div className="pipeline-left-branch">
                <div className="pipeline-card card-running">
                  <div className="title">Line A</div>
                  <div className="icon-circle icon-running">⚙️</div>
                  <div className="status-badge badge-running">가동 중 (Running)</div>
                  <div className="sub-desc">RP-Module-77X</div>
                </div>
                <div className="pipeline-card card-idle">
                  <div className="title">Line B</div>
                  <div className="icon-circle icon-idle">⏸</div>
                  <div className="status-badge badge-idle">대기 중 (Idle)</div>
                  <div className="sub-desc">RP-Sensor-01S</div>
                </div>
              </div>

              <svg className="pipeline-connector-svg" viewBox="0 0 60 180" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M 0 45 L 30 45 C 45 45, 45 90, 60 90" stroke="#94a3b8" strokeWidth="2.5" fill="none" />
                <path d="M 0 135 L 30 135 C 45 135, 45 90, 60 90" stroke="#94a3b8" strokeWidth="2.5" fill="none" />
              </svg>

              <div className="pipeline-right-stream">
                <div className="pipeline-card card-error">
                  <div className="title">Line C</div>
                  <div className="icon-circle icon-error">❗</div>
                  <div className="status-badge badge-error">장애 발생 (Error)</div>
                  <div className="sub-desc">RP-Logic-P5</div>
                </div>

                <div className="pipeline-arrow">➔</div>

                <div className="pipeline-card card-qa">
                  <div className="title">검수 단계</div>
                  <div className="icon-circle icon-qa">☑️</div>
                  <div className="status-badge badge-qa">검수 대기</div>
                  <div className="sub-desc">Lot. #522-QC</div>
                </div>
              </div>
            </div>
          </div>

          {/* 실시간 생산 알림 */}
          <div className="section-card">
            <div className="alert-header-row">
              <h3 style={{ margin: 0, fontSize: "15px", fontWeight: 700 }}>실시간 생산 알림</h3>
              <span className="alert-count-badge">3 NEW ALERTS</span>
            </div>

            <div className="alerts-flex-row">
              <div className="alert-box alert-red">
                <div className="alert-box-icon">⚠️</div>
                <div className="alert-box-content">
                  <div className="alert-box-title">Line C - 모터 과열</div>
                  <div className="alert-box-desc">
                    긴급 장비 점검 필요. 생산이 일시 중단되었습니다.
                  </div>
                  <div className="alert-box-footer">방금 전 | 유지보수팀 호출</div>
                </div>
              </div>

              <div className="alert-box alert-yellow">
                <div className="alert-box-icon">📦</div>
                <div className="alert-box-content">
                  <div className="alert-box-title">자재 부족 (RP-Sensor)</div>
                  <div className="alert-box-desc">
                    Line B의 원자재 잔량이 15% 미만입니다.
                  </div>
                  <div className="alert-box-footer">15분 전 | 창고 요청 발송</div>
                </div>
              </div>

              <div className="alert-box alert-blue">
                <div className="alert-box-icon">✅</div>
                <div className="alert-box-content">
                  <div className="alert-box-title">품질 검사 완료</div>
                  <div className="alert-box-desc">
                    Line A 루트 #A-2024 품질 검사 통과.
                  </div>
                  <div className="alert-box-footer">1시간 전 | QA 승인</div>
                </div>
              </div>
            </div>

            <button className="btn-all-alerts" onClick={() => setIsAlertModalOpen(true)}>
              전체 알림 기록 보기
            </button>
          </div>
        </div>
      )}

      {/* =========================================================================
          레이어 팝업 형태의 실시간 전체 알림 이력 기록 모달 
         ========================================================================= */}
      {isAlertModalOpen && (
        <div className="alert-modal-overlay" onClick={() => setIsAlertModalOpen(false)}>
          <div className="alert-modal-wrapper" onClick={(e) => e.stopPropagation()}>
            
            <div className="alert-modal-header">
              <div className="alert-modal-title-group">
                <span className="history-icon">🔔</span>
                <span className="alert-modal-title">전체 생산 알림 이력 기록</span>
                <span className="modal-count-badge">{allAlerts.length} 건</span>
              </div>
              <button className="alert-modal-close-x" onClick={() => setIsAlertModalOpen(false)}>✕</button>
            </div>

            <div className="alert-modal-body">
              <p className="alert-modal-subtext">공장 생산 라인에서 실시간 감지된 알림의 전체 타임라인 로그 내역입니다.</p>
              
              <div className="alert-history-list">
                {allAlerts.map((alert) => (
                  <div key={alert.id} className={`alert-history-item state-${alert.type}`}>
                    <div className="history-icon">{alert.icon}</div>
                    <div className="history-details">
                      <div className="history-row">
                        <span className="history-title">{alert.title}</span>
                        <span className="history-time">{alert.time}</span>
                      </div>
                      <p className="history-desc">{alert.desc}</p>
                      <span className="history-meta">📍 {alert.dept}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="alert-modal-footer">
              <button className="btn-alert-close" onClick={() => setIsAlertModalOpen(false)}>
                확인 및 닫기
              </button>
            </div>

          </div>
        </div>
      )}

      {/* =========================================================================
          레이어 팝업 형태의 신규 작업 지시 발행 모달
         ========================================================================= */}
      {isOrderModalOpen && (
        <div className="order-modal-overlay" onClick={() => setIsOrderModalOpen(false)}>
          <div className="order-modal-wrapper" onClick={(e) => e.stopPropagation()}>
            
            {/* 헤더 */}
            <div className="order-modal-header">
              <div className="order-modal-title-group">
                <span>📋</span>
                <span className="order-modal-title">신규 작업 지시 발행 (New Work Order)</span>
              </div>
              <button className="order-modal-close-x" onClick={() => setIsOrderModalOpen(false)}>✕</button>
            </div>

            {/* 본문 폼 */}
            <form onSubmit={handleOrderSubmit}>
              <div className="order-modal-body">
                <p className="order-modal-subtext">
                  선택한 공정 생산 라인으로 새로운 작업 명령을 발행합니다.<br />
                  품목 정보와 타겟 목표 수량을 기입해 주세요.
                </p>

                <div className="order-form">
                  <div className="order-form-group">
                    <label>생산 대상 라인</label>
                    <select required name="targetLine">
                      <option value="Line A">Line A (가동 중 - RP-Module-77X)</option>
                      <option value="Line B">Line B (대기 중 - RP-Sensor-01S)</option>
                      <option value="Line C">Line C (장애 발생 - RP-Logic-P5)</option>
                    </select>
                  </div>

                  <div className="order-form-group">
                    <label>생산 대상 품목 코드/명</label>
                    <input 
                      type="text" 
                      placeholder="예: RP-Module-88Y" 
                      required 
                    />
                  </div>

                  <div className="order-form-group">
                    <label>목표 생산 수량</label>
                    <div className="input-unit-wrapper">
                      <input 
                        type="number" 
                        placeholder="예: 1200" 
                        min="1"
                        required 
                      />
                      <span className="unit-text-badge">EA</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* 푸터 버튼군 */}
              <div className="order-modal-footer">
                <button 
                  type="button" 
                  className="btn-order-cancel" 
                  onClick={() => setIsOrderModalOpen(false)}
                >
                  취소하기
                </button>
                <button type="submit" className="btn-order-submit">
                  🚀 지시서 발행
                </button>
              </div>
            </form>

          </div>
        </div>
      )}
    </>
  );
}

export default DashboardPage;