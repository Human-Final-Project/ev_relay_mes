import React, { useState, useEffect } from "react";
import "./DashboardPage.css"; // 분리된 CSS 파일 import

function DashboardPage() {
  const [activeTab, setActiveTab] = useState("Overview");
  const [currentTime, setCurrentTime] = useState("");

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setCurrentTime(now.toTimeString().split(" ")[0]);
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="mesdash">
      <div className="dashboard-layout">
        {/* 좌측 사이드 내비게이션 */}
        <aside className="sidebar">
          <div className="sidebar-brand">
            <h1>Mini MES</h1>
            <p>EV 릴레이 생산라인</p>
          </div>

          <nav className="sidebar-nav">
            <a
              href="#"
              className={`nav-item ${activeTab === "Overview" ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                setActiveTab("Overview");
              }}
            >
              <span className="material-symbols-outlined">dashboard</span>
              <span>대시보드</span>
            </a>
            <a
              href="#"
              className={`nav-item ${activeTab === "MasterData" ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                setActiveTab("MasterData");
              }}
            >
              <span className="material-symbols-outlined">database</span>
              <span>기준 정보</span>
            </a>
            <a
              href="#"
              className={`nav-item ${activeTab === "Production" ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                setActiveTab("Production");
              }}
            >
              <span className="material-symbols-outlined">
                precision_manufacturing
              </span>
              <span>생산 관리</span>
            </a>
            <a
              href="#"
              className={`nav-item ${activeTab === "Quality" ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                setActiveTab("Quality");
              }}
            >
              <span className="material-symbols-outlined">verified</span>
              <span>품질 관리</span>
            </a>
            <a
              href="#"
              className={`nav-item ${activeTab === "Settings" ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                setActiveTab("Settings");
              }}
            >
              <span className="material-symbols-outlined">
                settings_applications
              </span>
              <span>시스템 설정</span>
            </a>
          </nav>

          <div className="sidebar-footer">
            <a href="#" className="nav-item">
              <span className="material-symbols-outlined">help</span>
              <span>고객지원</span>
            </a>
            <a href="#" className="nav-item">
              <span className="material-symbols-outlined">terminal</span>
              <span>시스템 로그</span>
            </a>
          </div>
        </aside>

        {/* 메인 컨테이너 */}
        <div className="main-container">
          {/* 상단 헤더 */}
          <header className="top-header">
            <div className="header-left">
              <h2>EV 릴레이 생산 시스템</h2>
              <nav className="header-tabs">
                <a href="#" className="active">
                  시스템 개요
                </a>
                <a href="#">실시간 모니터링</a>
              </nav>
            </div>
            <div className="header-right">
              <div className="tcp-status">
                <span className="status-dot glow-pulse"></span>
                <span>TCP 리스너 활성</span>
              </div>
              <span className="current-time">{currentTime}</span>
              <div className="user-actions">
                <button className="material-symbols-outlined">
                  notifications
                </button>
                <div className="user-avatar">
                  <img
                    src="https://lh3.googleusercontent.com/aida-public/AB6AXuAPVhs8r4P8Dz9lrCAtHBvcULEvTgkaDGaTHCykZYCOxFOn1Wm4WMMzCxMn6Qxze5kjVqTifxVNfdhB2nHoJdj39dsJ4-BpFEKqOQ_IQIz0BP9P5DlqdtC449cuvftcqfHQXi0MTsABK_vb3borr9oCyvrxFw-BY5pmlyU1YxIX-9yhKfat0PA0AN4X-xHN-sB9PyuTHCTWm86D72A64nhe7EXFxoBceEVAifG12H05xLVoVFUyCyNjHwdMzbFGbwZEno_bJQy3YvY"
                    alt="Profile"
                  />
                </div>
              </div>
            </div>
          </header>

          {/* 메인 콘텐츠 영역 */}
          <main className="content-area custom-scrollbar">
            {activeTab === "Overview" && (
              <div className="dashboard-grid">
                {/* 좌측 및 중앙 콘텐츠 */}
                <div className="grid-main">
                  {/* 상단 행: 실적 및 상태 */}
                  <div className="row-2col">
                    {/* 목표 대비 실적 */}
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

                    {/* 설비 가동 상태 */}
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

                  {/* 중단 행: 불량 코드 TOP 5 */}
                  <div className="card">
                    <div className="card-header-flex mb-md">
                      <h3>주간 주요 불량 코드 TOP 5</h3>
                      <span className="subtitle-date">
                        2024.05.01 - 2024.05.07
                      </span>
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
                          <td className="text-right text-error font-bold">
                            11%
                          </td>
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

                  {/* 하단 행: 검사/출하 진행 현황 */}
                  <div className="progress-section">
                    <div className="progress-card">
                      <div className="progress-header">
                        <h4>주간 검사 진행 현황</h4>
                      </div>
                      <div className="progress-grid">
                        <div className="stat-col">
                          <span className="stat-label">계획 수량</span>
                          <span className="stat-val text-on-surface">
                            10,000,000
                          </span>
                        </div>
                        <div className="stat-col">
                          <span className="stat-label">판정 수량</span>
                          <span className="stat-val text-on-surface">
                            1,000,000
                          </span>
                        </div>
                        <div className="stat-col">
                          <span className="stat-label text-primary">
                            합격 수량
                          </span>
                          <span className="stat-val text-primary">
                            1,000,100
                          </span>
                        </div>
                        <div className="stat-col">
                          <span className="stat-label text-error">
                            불합격 수량
                          </span>
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
                          <span className="stat-label text-secondary">
                            진척률
                          </span>
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
                          <span className="stat-val text-on-surface">
                            10,000,000
                          </span>
                        </div>
                        <div className="stat-col">
                          <span className="stat-label">포장 완료</span>
                          <span className="stat-val text-on-surface">
                            1,000,000
                          </span>
                        </div>
                        <div className="stat-col">
                          <span className="stat-label text-primary">
                            출하 완료
                          </span>
                          <span className="stat-val text-primary">
                            1,000,100
                          </span>
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
                          <span className="stat-label text-tertiary">
                            출하율
                          </span>
                          <span className="stat-val text-tertiary">100%</span>
                        </div>
                        <div className="stat-col">
                          <span className="stat-label text-secondary">
                            목표치
                          </span>
                          <span className="stat-val text-secondary">10%</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* 우측 사이드바: 알림 및 정보 */}
                <div className="grid-side">
                  {/* 알림 로그 */}
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
                          <p className="alert-desc">
                            라인 B 피드 속도 편차 감지됨
                          </p>
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
                          <p className="alert-desc">
                            교대 근무 시작: C팀 가동 중
                          </p>
                          <p className="alert-time">14:00:00</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* 공지사항 */}
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
          </main>

          {/* 푸터 */}
          <footer className="footer">
            <div className="footer-links">
              <span>헬프데스크: 02-3311-2300</span>
              <span>개인정보처리방침</span>
              <span>PDA 전용 앱 다운로드</span>
            </div>
            <div className="footer-copyright">
              <span>
                COPYRIGHT 2024 MIRACOM INC CO., LTD. ALL RIGHTS RESERVED.
              </span>
              <strong>MIRACOM</strong>
            </div>
          </footer>
        </div>
      </div>
    </div>
  );
}

export default DashboardPage;
