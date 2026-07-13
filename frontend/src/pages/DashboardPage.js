import React, { useState } from "react";

function DashboardPage() {
  const [activeTab] = useState("Overview");

  const styles = `
    .mesdash .dashboard-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: var(--md); height: 100%; }
    .mesdash .grid-main { grid-column: span 9 / span 9; display: flex; flex-direction: column; gap: var(--md); }
    .mesdash .grid-side { grid-column: span 3 / span 3; display: flex; flex-direction: column; gap: var(--md); }
    .mesdash .card { background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 8px; padding: var(--md); box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); display: flex; flex-direction: column; }
    .mesdash .card h3 { margin: 0; font-size: 20px; font-weight: 600; line-height: 28px; color: var(--on-surface); }
    .mesdash .card-header-flex { display: flex; justify-content: space-between; align-items: flex-end; }
    .mesdash .card-header-flex h3 { white-space: nowrap; }
    .mesdash .legend { flex-shrink: 0; display: flex; gap: var(--md); font-size: 11px; font-weight: 700; letter-spacing: 0.05em; }
    .mesdash .legend-item { display: flex; align-items: center; gap: var(--xs); }
    .mesdash .color-box { width: 12px; height: 12px; }
    .mesdash .color-box.secondary { background-color: var(--target-tint); }
    .mesdash .color-box.tertiary { background-color: var(--tertiary); }
    .mesdash .mb-md { margin-bottom: var(--md); }
    .mesdash .mb-sm { margin-bottom: var(--sm); }
    .mesdash .pb-xs { padding-bottom: var(--xs); }
    .mesdash .border-bottom { border-bottom: 1px solid var(--outline-variant); }
    .mesdash .subtitle, .mesdash .subtitle-bold, .mesdash .subtitle-date { margin: 0; font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--on-surface-variant); }
    .mesdash .text-primary { color: var(--primary); }
    .mesdash .text-secondary { color: var(--secondary); }
    .mesdash .text-tertiary { color: var(--tertiary); }
    .mesdash .text-error { color: var(--error); }
    .mesdash .text-on-surface { color: var(--on-surface); }
    .mesdash .text-outline { color: var(--outline); }
    .mesdash .text-right { text-align: right; }
    .mesdash .font-bold { font-weight: bold; }
    .mesdash .font-code { font-family: "JetBrains Mono", monospace; font-size: 12px; }
    .mesdash .row-2col { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--md); }
    .mesdash .h-60p { height: 60%; }
    .mesdash .h-25p { height: 25%; }
    .mesdash .flex-col { flex-direction: column; }
    .mesdash .chart-area { flex: 1; display: flex; align-items: flex-end; justify-content: space-between; gap: var(--md); height: 192px; padding: 0 var(--md); border-bottom: 1px solid rgba(197, 198, 205, 0.2); margin-bottom: 8px; }
    .mesdash .chart-bar-group { flex: 1; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; gap: 4px; height: 100%; }
    .mesdash .bar-pair { display: flex; align-items: flex-end; justify-content: center; gap: 4px; width: 100%; height: 100%; }
    .mesdash .bar-pair .bar { flex: 1; max-width: 22px; }
    .mesdash .bar { border-top-left-radius: 4px; border-top-right-radius: 4px; transition: all 0.3s; }
    .mesdash .bar.target { background-color: var(--target-tint); }
    .mesdash .bar.actual { background-color: var(--tertiary); }
    .mesdash .chart-bar-group:hover .bar.target { background-color: rgba(5, 102, 217, 0.55); }
    .mesdash .chart-bar-group:hover .bar.actual { filter: brightness(0.9); }
    .mesdash .chart-label { font-family: "JetBrains Mono", monospace; font-size: 11px; color: var(--on-surface-variant); margin-top: 8px; }
    .mesdash .equipment-status { flex: 1; display: flex; align-items: center; justify-content: center; gap: var(--xl); }
    .mesdash .donut-chart-container { position: relative; width: 128px; height: 128px; display: flex; align-items: center; justify-content: center; }
    .mesdash .donut-chart { width: 100%; height: 100%; transform: rotate(-90deg); }
    .mesdash .donut-chart circle { fill: none; stroke-width: 3; }
    .mesdash .donut-chart .bg { stroke: var(--surface-container-high); }
    .mesdash .donut-chart .val1 { stroke: var(--primary); }
    .mesdash .donut-chart .val2 { stroke: var(--tertiary); }
    .mesdash .donut-inner { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }
    .mesdash .donut-inner .number { font-size: 36px; font-weight: 700; line-height: 1; letter-spacing: -0.02em; }
    .mesdash .donut-inner .label { font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--on-surface-variant); }
    .mesdash .status-list { display: flex; flex-direction: column; gap: var(--sm); flex: 1; }
    .mesdash .status-row { display: grid; grid-template-columns: 90px 1fr; align-items: center; font-size: 14px; }
    .mesdash .status-row .label { display: flex; align-items: center; gap: var(--sm); color: var(--on-surface); }
    .mesdash .status-row .value { text-align: right; font-family: "JetBrains Mono", monospace; font-size: 12px; color: var(--on-surface-variant); }
    .mesdash .dot { width: 8px; height: 8px; border-radius: 50%; }
    .mesdash .dot.primary { background-color: var(--primary); }
    .mesdash .dot.secondary { background-color: var(--secondary); }
    .mesdash .dot.tertiary { background-color: var(--tertiary); }
    .mesdash .dot.outline { background-color: var(--outline); }
    .mesdash .data-table { width: 100%; text-align: left; border-collapse: collapse; }
    .mesdash .data-table th { padding: var(--sm) var(--md); border-bottom: 1px solid var(--outline-variant); font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--on-surface-variant); }
    .mesdash .data-table td { padding: var(--sm) var(--md); font-size: 14px; border-bottom: 1px solid rgba(197, 198, 205, 0.3); }
    .mesdash .data-table tbody tr:hover { background-color: var(--surface-container); }
    .mesdash .progress-section { display: flex; flex-direction: column; gap: var(--md); }
    .mesdash .progress-card { background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 8px; overflow: hidden; box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
    .mesdash .progress-header { background-color: var(--surface-container-high); padding: var(--sm) var(--md); border-bottom: 1px solid var(--outline-variant); }
    .mesdash .progress-header h4 { margin: 0; font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--on-surface-variant); }
    .mesdash .progress-grid { display: grid; grid-template-columns: repeat(7, minmax(110px, 1fr)); gap: var(--xs); text-align: center; padding: var(--md); border-bottom: 1px solid rgba(197, 198, 205, 0.3); background-color: var(--surface-container-lowest); }
    .mesdash .stat-col { display: flex; flex-direction: column; gap: var(--xs); min-width: 0; }
    .mesdash .stat-label { font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--on-surface-variant); }
    .mesdash .stat-val { font-size: 22px; font-weight: 700; line-height: 1.2; white-space: nowrap; }
    .mesdash .btn-link { background: none; border: none; color: var(--primary); font-size: 11px; font-weight: 700; letter-spacing: 0.05em; cursor: pointer; padding: 0; }
    .mesdash .btn-link:hover { text-decoration: underline; }
    .mesdash .alerts-list { flex: 1; display: flex; flex-direction: column; gap: var(--md); overflow-y: auto; padding-right: var(--xs); }
    .mesdash .alert-item { display: flex; gap: var(--sm); padding: var(--sm); border-radius: 4px; border-left: 4px solid; }
    .mesdash .alert-item.info { border-left-color: var(--primary); background-color: rgba(5, 102, 217, 0.05); }
    .mesdash .alert-item.warn { border-left-color: var(--secondary); background-color: rgba(5, 102, 217, 0.05); }
    .mesdash .alert-item.error { border-left-color: var(--error); background-color: rgba(186, 26, 26, 0.05); }
    .mesdash .alert-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 6px; flex-shrink: 0; }
    .mesdash .alert-item.info .alert-dot { background-color: var(--primary); }
    .mesdash .alert-item.warn .alert-dot { background-color: var(--secondary); }
    .mesdash .alert-item.error .alert-dot { background-color: var(--error); }
    .mesdash .alert-content p { margin: 0; }
    .mesdash .alert-title { font-size: 14px; font-weight: bold; }
    .mesdash .alert-item.info .alert-title { color: var(--primary); }
    .mesdash .alert-item.warn .alert-title { color: var(--secondary); }
    .mesdash .alert-item.error .alert-title { color: var(--error); }
    .mesdash .alert-desc { font-size: 12px; color: var(--on-surface-variant); }
    .mesdash .alert-time { font-family: "JetBrains Mono", monospace; font-size: 11px; color: var(--outline); margin-top: 4px; }
    .mesdash .notice-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: var(--sm); font-size: 14px; }
    .mesdash .notice-list li { display: flex; justify-content: space-between; align-items: center; cursor: pointer; }
    .mesdash .notice-title { color: var(--on-surface); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; padding-right: var(--sm); transition: color 0.2s; }
    .mesdash .notice-list li:hover .notice-title { color: var(--primary); }
    .mesdash .notice-date { font-family: "JetBrains Mono", monospace; font-size: 11px; color: var(--outline); flex-shrink: 0; }
    .mesdash .text-sm { font-size: 16px; }
  `;

  return (
    <>
      <style>{styles}</style>
      {activeTab === "Overview" && (
        <div className="dashboard-grid">
          <div className="grid-main">
            <div className="row-2col">
              <div className="card">
                <div className="card-header-flex">
                  <div>
                    <h3>목표 대비 생산 실적 현황</h3>
                    <p className="subtitle">일일 생산 수율</p>
                  </div>
                  <div className="legend">
                    <span className="legend-item">
                      <span className="color-box secondary"></span> 목표
                    </span>
                    <span className="legend-item">
                      <span className="color-box tertiary"></span> 실적
                    </span>
                  </div>
                </div>
                <div className="chart-area">
                  {[
                    { date: "11.11", target: "70%", actual: "55%" },
                    { date: "11.12", target: "85%", actual: "82%" },
                    { date: "11.13", target: "65%", actual: "78%" },
                    { date: "11.14", target: "90%", actual: "88%" },
                    { date: "11.15", target: "75%", actual: "45%" },
                  ].map((item, idx) => (
                    <div key={idx} className="chart-bar-group">
                      <div className="bar-pair">
                        <div
                          className="bar target"
                          style={{ height: item.target }}
                        ></div>
                        <div
                          className="bar actual"
                          style={{ height: item.actual }}
                        ></div>
                      </div>
                      <span className="chart-label">{item.date}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="card">
                <h3>설비 가동 상태</h3>
                <div className="equipment-status">
                  <div className="donut-chart-container">
                    <svg className="donut-chart" viewBox="0 0 36 36">
                      <circle className="bg" cx="18" cy="18" r="16" />
                      <circle
                        className="val1"
                        cx="18"
                        cy="18"
                        r="16"
                        strokeDasharray="75, 100"
                      />
                      <circle
                        className="val2"
                        cx="18"
                        cy="18"
                        r="16"
                        strokeDasharray="20, 100"
                        strokeDashoffset="-75"
                      />
                    </svg>
                    <div className="donut-inner">
                      <span className="number">13</span>
                      <span className="label">LOTS</span>
                    </div>
                  </div>
                  <div className="status-list">
                    <div className="status-row">
                      <span className="label">
                        <span className="dot primary"></span> 라인 A
                      </span>
                      <span className="value">305,000 (55%)</span>
                    </div>
                    <div className="status-row">
                      <span className="label">
                        <span className="dot secondary"></span> 라인 B
                      </span>
                      <span className="value">125,000 (28%)</span>
                    </div>
                    <div className="status-row">
                      <span className="label">
                        <span className="dot tertiary"></span> 라인 C
                      </span>
                      <span className="value">5,000 (5%)</span>
                    </div>
                    <div className="status-row">
                      <span className="label">
                        <span className="dot outline"></span> 라인 D
                      </span>
                      <span className="value">15,000 (12%)</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="card">
              <div className="card-header-flex mb-md">
                <h3>주간 주요 불량 코드 TOP 5</h3>
                <span className="subtitle-date">2024.05.01 - 2024.05.07</span>
              </div>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>순위</th>
                    <th>불량 코드</th>
                    <th>상세 설명</th>
                    <th className="text-right">수량</th>
                    <th className="text-right">점유율</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>1</td>
                    <td className="text-primary font-code">LW1001</td>
                    <td>레이저 용접 불량</td>
                    <td className="text-right">350 ea</td>
                    <td className="text-right text-error font-bold">11%</td>
                  </tr>
                  <tr>
                    <td>2</td>
                    <td className="text-primary font-code">CW2003</td>
                    <td>코일 권선 텐션 오류</td>
                    <td className="text-right">170 ea</td>
                    <td className="text-right">5%</td>
                  </tr>
                  <tr>
                    <td>3</td>
                    <td className="text-primary font-code">IR1001</td>
                    <td>절연 저항 미달</td>
                    <td className="text-right">60 ea</td>
                    <td className="text-right">4%</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div className="progress-section">
              <div className="progress-card">
                <div className="progress-header">
                  <h4>주간 검사 진행 현황</h4>
                </div>
                <div className="progress-grid">
                  <div className="stat-col">
                    <span className="stat-label">계획 수량</span>
                    <span className="stat-val text-on-surface">10,000,000</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label">판정 수량</span>
                    <span className="stat-val text-on-surface">1,000,000</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label text-primary">합격 수량</span>
                    <span className="stat-val text-primary">1,000,100</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label text-error">불합격 수량</span>
                    <span className="stat-val text-error">0</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label">보류</span>
                    <span className="stat-val text-on-surface">100</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label text-tertiary">수율</span>
                    <span className="stat-val text-tertiary">99%</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label text-secondary">진척률</span>
                    <span className="stat-val text-secondary">10%</span>
                  </div>
                </div>
              </div>

              <div className="progress-card">
                <div className="progress-header">
                  <h4>주간 출하 진행 현황</h4>
                </div>
                <div className="progress-grid">
                  <div className="stat-col">
                    <span className="stat-label">요청 수량</span>
                    <span className="stat-val text-on-surface">10,000,000</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label">포장 완료</span>
                    <span className="stat-val text-on-surface">1,000,000</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label text-primary">출하 완료</span>
                    <span className="stat-val text-primary">1,000,100</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label text-error">반품</span>
                    <span className="stat-val text-error">0</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label">버퍼 수량</span>
                    <span className="stat-val text-on-surface">100</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label text-tertiary">출하율</span>
                    <span className="stat-val text-tertiary">100%</span>
                  </div>
                  <div className="stat-col">
                    <span className="stat-label text-secondary">목표치</span>
                    <span className="stat-val text-secondary">10%</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="grid-side">
            <div className="card h-60p flex-col">
              <div className="card-header-flex border-bottom pb-xs mb-md">
                <h3>시스템 알림</h3>
                <button className="btn-link">전체 보기</button>
              </div>
              <div className="alerts-list custom-scrollbar">
                <div className="alert-item info">
                  <span className="alert-dot"></span>
                  <div className="alert-content">
                    <p className="alert-title">정보 SPC2001</p>
                    <p className="alert-desc">
                      SPC 알람 (Stream-A), 파라미터: Sigma+0.5
                    </p>
                    <p className="alert-time">14:40:12</p>
                  </div>
                </div>
                <div className="alert-item warn">
                  <span className="alert-dot"></span>
                  <div className="alert-content">
                    <p className="alert-title">경고 SPC0032</p>
                    <p className="alert-desc">라인 B 피드 속도 편차 감지됨</p>
                    <p className="alert-time">14:38:05</p>
                  </div>
                </div>
                <div className="alert-item error">
                  <span className="alert-dot"></span>
                  <div className="alert-content">
                    <p className="alert-title">오류 ALM1020</p>
                    <p className="alert-desc">
                      레이저 용접 헤드 #4 과열 (92°C)
                    </p>
                    <p className="alert-time">14:35:59</p>
                  </div>
                </div>
                <div className="alert-item info">
                  <span className="alert-dot"></span>
                  <div className="alert-content">
                    <p className="alert-title">정보 COM01</p>
                    <p className="alert-desc">교대 근무 시작: C팀 가동 중</p>
                    <p className="alert-time">14:00:00</p>
                  </div>
                </div>
              </div>
            </div>

            <div className="card h-25p flex-col">
              <div className="card-header-flex mb-sm">
                <h4 className="subtitle-bold">공지사항</h4>
                <span className="material-symbols-outlined text-outline text-sm">
                  more_horiz
                </span>
              </div>
              <ul className="notice-list">
                <li>
                  <span className="notice-title">
                    3분기 안전 점검 실시 안내
                  </span>
                  <span className="notice-date">2024.05.20</span>
                </li>
                <li>
                  <span className="notice-title">
                    라인 C 작업자 교육 프로토콜 변경
                  </span>
                  <span className="notice-date">2024.05.18</span>
                </li>
                <li>
                  <span className="notice-title">
                    휴일 설비 유지보수 일정 공지
                  </span>
                  <span className="notice-date">2024.05.15</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      )}
      {activeTab !== "Overview" && (
        <div className="placeholder-content">
          <h2>{activeTab} Module</h2>
          <p>Content for {activeTab} will be displayed here.</p>
        </div>
      )}
    </>
  );
}

export default DashboardPage;
