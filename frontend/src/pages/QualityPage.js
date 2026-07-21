import React, { useState } from "react";

// -------------------------------------------------------------
// [더미 데이터] 조별 공정 수율
// -------------------------------------------------------------
const shiftYieldData = {
  A: {
    label: "A조: 06:00 - 14:00",
    bars: [
      { label: "S-01 권선", pct: 99.5 },
      { label: "S-02 레이저", pct: 97.2 },
      { label: "S-03 하우징", pct: 94.8 },
      { label: "S-04 레진", pct: 98.9 },
      { label: "S-05 최종검사", pct: 96.5 },
    ],
  },
  B: {
    label: "B조: 14:00 - 22:00",
    bars: [
      { label: "S-01 권선", pct: 98.1 },
      { label: "S-02 레이저", pct: 95.6 },
      { label: "S-03 하우징", pct: 92.3 },
      { label: "S-04 레진", pct: 97.4 },
      { label: "S-05 최종검사", pct: 93.8 },
    ],
  },
  C: {
    label: "C조: 22:00 - 06:00",
    bars: [
      { label: "S-01 권선", pct: 96.7 },
      { label: "S-02 레이저", pct: 93.4 },
      { label: "S-03 하우징", pct: 89.9 },
      { label: "S-04 레진", pct: 95.2 },
      { label: "S-05 최종검사", pct: 90.1 },
    ],
  },
};

// -------------------------------------------------------------
// [더미 데이터] 상세 검사 결과 (페이지네이션 확인용으로 다건 등록)
// -------------------------------------------------------------
const allInspectionResults = [
  { id: "EV-R-49201-AX", test: "저항", val: "4.21 Ω (오차: ±0.2)", ok: true, time: "14:42:15" },
  { id: "EV-R-49202-AX", test: "외관", val: "98.2% 투명도", ok: true, time: "14:42:30" },
  { id: "EV-R-49203-AX", test: "압력", val: "0.12 MPa 누출", ok: false, time: "14:42:58" },
  { id: "EV-R-49204-AX", test: "저항", val: "4.18 Ω", ok: true, time: "14:43:12" },
  { id: "EV-R-49205-AX", test: "압력", val: "0.01 MPa 누출", ok: true, time: "14:43:45" },
  { id: "EV-R-49206-AX", test: "온도", val: "23.4°C", ok: true, time: "14:44:02" },
  { id: "EV-R-49207-AX", test: "절연저항", val: "5.2 MΩ", ok: true, time: "14:44:20" },
  { id: "EV-R-49208-AX", test: "외관", val: "94.1% 투명도", ok: false, time: "14:44:47" },
  { id: "EV-R-49209-AX", test: "저항", val: "4.35 Ω (오차 초과)", ok: false, time: "14:45:03" },
  { id: "EV-R-49210-AX", test: "압력", val: "0.02 MPa 누출", ok: true, time: "14:45:29" },
  { id: "EV-R-49211-AX", test: "온도", val: "24.0°C", ok: true, time: "14:45:51" },
  { id: "EV-R-49212-AX", test: "절연저항", val: "5.5 MΩ", ok: true, time: "14:46:10" },
  { id: "EV-R-49213-AX", test: "외관", val: "97.8% 투명도", ok: true, time: "14:46:33" },
];

function QualityPage() {
  const [searchSerial, setSearchInput] = useState("");
  const [selectedShift, setSelectedShift] = useState("A");
  const [showFilterPanel, setShowFilterPanel] = useState(false);
  const [statusFilter, setStatusFilter] = useState("all"); // all | pass | fail
  const [currentPage, setCurrentPage] = useState(1);
  const ITEMS_PER_PAGE = 5;

  // ---- 관리자 인증 상태: 실제 로그인 권한(localStorage)과 연동 ----
  // 리포트 내보내기처럼 데이터를 추출하는 기능만 관리자 전용으로 제한하고,
  // 수율/불량 조회 등 나머지 화면은 사원도 그대로 볼 수 있습니다.
  const isAdmin = localStorage.getItem("userRole") === "admin";

  const filteredResults = allInspectionResults
    .filter((row) => row.id.toLowerCase().includes(searchSerial.toLowerCase()))
    .filter((row) => {
      if (statusFilter === "pass") return row.ok;
      if (statusFilter === "fail") return !row.ok;
      return true;
    });

  const totalPages = Math.max(1, Math.ceil(filteredResults.length / ITEMS_PER_PAGE));
  const safePage = Math.min(currentPage, totalPages);
  const pageResults = filteredResults.slice(
    (safePage - 1) * ITEMS_PER_PAGE,
    safePage * ITEMS_PER_PAGE
  );
  const rangeStart = filteredResults.length === 0 ? 0 : (safePage - 1) * ITEMS_PER_PAGE + 1;
  const rangeEnd = Math.min(safePage * ITEMS_PER_PAGE, filteredResults.length);

  const handleSearchChange = (e) => {
    setSearchInput(e.target.value);
    setCurrentPage(1);
  };

  const handleStatusFilterChange = (value) => {
    setStatusFilter(value);
    setCurrentPage(1);
    setShowFilterPanel(false);
  };

  const styles = `
    .mesdash { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; }

    .mesdash .quality-header-flex { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .mesdash .quality-header-flex h2 { margin: 0; font-size: 22px; font-weight: 800; line-height: 1.3; color: #0f172a; }
    .mesdash .quality-header-flex p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; }

    .mesdash .btn-group { display: flex; gap: 8px; }
    .mesdash .btn-secondary-outline { padding: 9px 14px; border: 1px solid #cbd5e1; border-radius: 8px; background: #ffffff; font-size: 13px; font-weight: 700; color: #475569; cursor: pointer; display: flex; align-items: center; gap: 4px; }
    .mesdash .btn-secondary-outline:hover { background-color: #f1f5f9; }
    .mesdash .btn-primary-filled { padding: 9px 14px; border: none; border-radius: 8px; background-color: #0566d9; font-size: 13px; font-weight: 700; color: #ffffff; cursor: pointer; display: flex; align-items: center; gap: 4px; }
    .mesdash .btn-primary-filled:hover { opacity: 0.85; }
    .mesdash .material-symbols-outlined { font-size: 18px; }

    .mesdash .bento-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 16px; margin-bottom: 16px; }
    .mesdash .bento-left { grid-column: span 3 / span 3; display: flex; flex-direction: column; gap: 16px; }
    .mesdash .bento-center { grid-column: span 6 / span 6; }
    .mesdash .bento-right { grid-column: span 3 / span 3; }
    @media (max-width: 1100px) {
      .mesdash .bento-left, .mesdash .bento-center, .mesdash .bento-right { grid-column: span 12 / span 12; }
    }

    .mesdash .card { background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,0.03); display: flex; flex-direction: column; }
    .mesdash .card h3 { margin: 0; font-size: 15px; font-weight: 800; line-height: 1.3; color: #0f172a; }

    /* 종합 수율 프로그레스 */
    .mesdash .yield-meta { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 12px; font-weight: 700; color: #64748b; }
    .mesdash .yield-badge { padding: 3px 7px; background-color: #dcfce7; color: #16803d; font-size: 11px; border-radius: 6px; font-weight: 700; }
    .mesdash .yield-value { display: flex; align-items: baseline; gap: 4px; margin-bottom: 10px; }
    .mesdash .yield-value .main-num { font-size: 28px; font-weight: 800; color: #0566d9; }
    .mesdash .line-track { height: 6px; width: 100%; background-color: #f1f5f9; border-radius: 9999px; overflow: hidden; }
    .mesdash .line-fill { height: 100%; background-color: #0566d9; border-radius: 9999px; }

    /* 수량 스킨 */
    .mesdash .qty-flex { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; }
    .mesdash .qty-flex .total-num { font-size: 22px; font-weight: 800; color: #0f172a; }
    .mesdash .qty-sub { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; font-size: 12px; font-weight: 700; }

    /* 텍스트 유틸리티 */
    .mesdash .text-tertiary { color: #16803d; }
    .mesdash .text-error { color: #dc2626; }
    .mesdash .text-primary { color: #0566d9; }
    .mesdash .text-muted { color: #94a3b8; }

    /* 서클 그래프 */
    .mesdash .circle-flex { display: flex; align-items: center; gap: 14px; margin-top: 6px; }
    .mesdash .circle-graph { position: relative; width: 48px; height: 48px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .mesdash .circle-svg { width: 100%; height: 100%; transform: rotate(-90deg); }
    .mesdash .circle-svg circle { fill: transparent; stroke-width: 4; }
    .mesdash .circle-svg .bg { stroke: #f1f5f9; }
    .mesdash .circle-svg .val { stroke: #0566d9; stroke-linecap: round; }
    .mesdash .circle-inner-txt { position: absolute; font-size: 11px; font-weight: 800; color: #0f172a; }

    /* 공정별 수율 차트 */
    .mesdash .chart-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
    .mesdash .chart-select { border: 1px solid #e2e8f0; border-radius: 6px; padding: 4px 8px; font-size: 12px; font-weight: 700; color: #64748b; background: #f8fafc; cursor: pointer; outline: none; }
    .mesdash .bar-container { display: flex; align-items: flex-end; justify-content: space-between; height: 200px; padding: 0 8px; }
    .mesdash .bar-column { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; gap: 8px; height: 100%; }
    .mesdash .bar-track { width: 100%; max-width: 32px; background-color: #dbeafe; border-radius: 6px 6px 0 0; position: relative; height: 140px; display: flex; flex-direction: column; justify-content: flex-end; overflow: hidden; }
    .mesdash .bar-fill { width: 100%; background-color: #0566d9; border-radius: 6px 6px 0 0; transition: height 0.3s; }
    .mesdash .bar-label { font-size: 11px; font-weight: 700; color: #64748b; white-space: nowrap; }
    .mesdash .bar-pct { font-family: "JetBrains Mono", monospace; font-size: 11px; color: #0566d9; font-weight: 700; }

    /* 불량 유형 프로그레스 바 */
    .mesdash .defect-list { display: flex; flex-direction: column; gap: 16px; margin-top: 10px; }
    .mesdash .defect-row { display: flex; flex-direction: column; gap: 6px; }
    .mesdash .defect-info { display: flex; justify-content: space-between; font-size: 13px; color: #0f172a; }
    .mesdash .defect-info span:last-child { font-weight: 700; }
    .mesdash .defect-bar-track { height: 6px; width: 100%; background-color: #f1f5f9; border-radius: 9999px; overflow: hidden; }
    .mesdash .defect-bar-fill { height: 100%; background-color: #dc2626; border-radius: 9999px; }

    /* 하단 테이블 그리드 배치 */
    .mesdash .bottom-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 16px; }
    .mesdash .bottom-left { grid-column: span 4 / span 4; }
    .mesdash .bottom-right { grid-column: span 8 / span 8; }
    @media (max-width: 1100px) {
      .mesdash .bottom-left, .mesdash .bottom-right { grid-column: span 12 / span 12; }
    }

    .mesdash .card-header-tool { display: flex; justify-content: space-between; align-items: center; padding-bottom: 14px; border-bottom: 1px solid #e2e8f0; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
    .mesdash .search-wrapper { position: relative; display: flex; align-items: center; }
    .mesdash .search-wrapper .search-icon { position: absolute; left: 8px; color: #94a3b8; font-size: 16px; }
    .mesdash .search-input { padding: 8px 12px 8px 30px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; width: 200px; }
    .mesdash .search-input:focus { border-color: #0566d9; }

    .mesdash .data-table { width: 100%; text-align: left; border-collapse: collapse; }
    .mesdash .data-table th { padding: 10px 12px; border-bottom: 1px solid #e2e8f0; font-size: 11px; font-weight: 700; letter-spacing: 0.03em; color: #64748b; background-color: #f8fafc; }
    .mesdash .data-table td { padding: 12px; font-size: 13px; border-bottom: 1px solid #f1f5f9; }
    .mesdash .data-table tbody tr:last-child td { border-bottom: none; }
    .mesdash .data-table tbody tr:hover td { background-color: #f8fafc; }
    .mesdash .font-code { font-family: "JetBrains Mono", monospace; font-size: 12px; }

    .mesdash .status-tag { padding: 4px 8px; border-radius: 12px; font-size: 11px; font-weight: 700; white-space: nowrap; }
    .mesdash .status-tag.isolate { background-color: #fee2e2; color: #b91c1c; }
    .mesdash .status-tag.checking { background-color: #fef9c3; color: #a16207; }
    .mesdash .status-tag.discard { background-color: #0f172a; color: #ffffff; }

    /* 상세 검사 결과: 필터 드롭다운 */
    .mesdash .filter-wrapper { position: relative; }
    .mesdash .filter-panel { position: absolute; top: 44px; right: 0; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 8px 20px rgba(0,0,0,0.1); z-index: 20; min-width: 160px; padding: 6px; }
    .mesdash .filter-panel-title { font-size: 11px; font-weight: 700; color: #94a3b8; padding: 6px 8px 2px; }
    .mesdash .filter-option { padding: 8px; border-radius: 6px; font-size: 12px; font-weight: 600; color: #334155; cursor: pointer; display: flex; align-items: center; justify-content: space-between; }
    .mesdash .filter-option:hover { background: #f1f5f9; }
    .mesdash .filter-option.selected { background: #eaf2fc; color: #0566d9; font-weight: 700; }

    /* 페이지네이션 */
    .mesdash .table-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 14px; font-size: 12px; color: #64748b; flex-wrap: wrap; gap: 8px; }
    .mesdash .page-btn-group { display: flex; gap: 6px; }
    .mesdash .page-btn { padding: 6px 12px; border: 1px solid #cbd5e1; background: #ffffff; border-radius: 6px; cursor: pointer; font-size: 11px; font-weight: 700; color: #475569; }
    .mesdash .page-btn:hover { background-color: #f1f5f9; }
    .mesdash .page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .mesdash .page-btn:disabled:hover { background-color: #ffffff; }
    .mesdash .page-info { font-family: "JetBrains Mono", monospace; }

    .mesdash .readonly-tag { font-size: 11px; font-weight: 700; color: #64748b; background: #f1f5f9; padding: 6px 10px; border-radius: 8px; }
  `;

  return (
    <>
      <style>{styles}</style>

      {/* 상단 타이틀 바 */}
      <div className="quality-header-flex">
        <div>
          <h2>품질 관리</h2>
          <p>B-02 라인의 실시간 검사 분석 및 불량 추적 데이터입니다.</p>
        </div>
        <div className="btn-group">
          {isAdmin ? (
            <button type="button" className="btn-primary-filled">
              <span className="material-symbols-outlined">download</span>리포트
              내보내기
            </button>
          ) : (
            <span className="readonly-tag">조회 전용</span>
          )}
        </div>
      </div>

      {/* 상단 3개 Bento 분할 영역 */}
      <div className="bento-grid">
        <div className="bento-left">
          <div className="card">
            <div className="yield-meta">
              <span>종합 수율</span>
              <span className="yield-badge">+0.2%</span>
            </div>
            <div className="yield-value">
              <span className="main-num">98.42%</span>
              <span style={{ fontSize: "12px", color: "#94a3b8" }}>
                / 100%
              </span>
            </div>
            <div className="line-track">
              <div className="line-fill" style={{ width: "98.42%" }}></div>
            </div>
          </div>

          <div className="card">
            <span className="yield-meta" style={{ marginBottom: "4px" }}>
              금일 총 검사 수량
            </span>
            <div className="qty-flex">
              <span className="total-num">12,482</span>
              <div className="qty-sub">
                <span className="text-tertiary">양품 12,285</span>
                <span className="text-error">불량 197</span>
              </div>
            </div>
          </div>

          <div className="card">
            <span className="yield-meta" style={{ marginBottom: "4px" }}>
              재작업 역량
            </span>
            <div className="circle-flex">
              <div className="circle-graph">
                <svg className="circle-svg" viewBox="0 0 36 36">
                  <circle className="bg" cx="18" cy="18" r="16" />
                  <circle
                    className="val"
                    cx="18"
                    cy="18"
                    r="16"
                    strokeDasharray="75, 100"
                  />
                </svg>
                <span className="circle-inner-txt">75%</span>
              </div>
              <div style={{ fontSize: "13px" }}>
                <div style={{ fontWeight: "700" }}>148개 재작업 완료</div>
                <div style={{ fontSize: "11px", color: "#94a3b8" }}>
                  효율 최적화됨
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="bento-center card">
          <div className="chart-header">
            <h3>공정별 수율 현황</h3>
            <select
              className="chart-select"
              value={selectedShift}
              onChange={(e) => setSelectedShift(e.target.value)}
            >
              {Object.entries(shiftYieldData).map(([key, shift]) => (
                <option key={key} value={key}>{shift.label}</option>
              ))}
            </select>
          </div>
          <div className="bar-container">
            {shiftYieldData[selectedShift].bars.map((bar, idx) => (
              <div key={idx} className="bar-column">
                <div className="bar-track">
                  <div className="bar-fill" style={{ height: `${bar.pct}%` }}></div>
                </div>
                <span className="bar-label">{bar.label}</span>
                <span className="bar-pct">{bar.pct}%</span>
              </div>
            ))}
          </div>
        </div>

        <div className="bento-right card">
          <h3>불량 유형</h3>
          <div className="defect-list">
            {[
              { type: "코일 단선", ratio: "42%", w: "42%", op: "1" },
              { type: "하우징 스크래치", ratio: "28%", w: "28%", op: "0.7" },
              { type: "접점 소손", ratio: "15%", w: "15%", op: "0.5" },
              { type: "레진 누출", ratio: "15%", w: "15%", op: "0.3" },
            ].map((df, idx) => (
              <div key={idx} className="defect-row">
                <div className="defect-info">
                  <span>{df.type}</span>
                  <span>{df.ratio}</span>
                </div>
                <div className="defect-bar-track">
                  <div
                    className="defect-bar-fill"
                    style={{ width: df.w, opacity: df.op }}
                  ></div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 하단 테이블 분할 영역 */}
      <div className="bottom-grid">
        <div className="bottom-left card">
          <div className="card-header-tool">
            <h3>불량 LOT 로그</h3>
            <span
              className="material-symbols-outlined"
              style={{ color: "#94a3b8", cursor: "pointer" }}
            >
              history
            </span>
          </div>
          <table className="data-table">
            <thead>
              <tr>
                <th>LOT ID</th>
                <th>에러 코드</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="font-code text-primary">L-20231024-001</td>
                <td>
                  <div style={{ fontWeight: "700", color: "#dc2626" }}>
                    ERR-402
                  </div>
                  <div style={{ fontSize: "10px", color: "#94a3b8" }}>
                    임피던스 초과
                  </div>
                </td>
                <td>
                  <span className="status-tag isolate">격리됨</span>
                </td>
              </tr>
              <tr>
                <td className="font-code text-primary">L-20231024-005</td>
                <td>
                  <div style={{ fontWeight: "700", color: "#dc2626" }}>
                    ERR-109
                  </div>
                  <div style={{ fontSize: "10px", color: "#94a3b8" }}>
                    정렬 오류
                  </div>
                </td>
                <td>
                  <span className="status-tag checking">검사중</span>
                </td>
              </tr>
              <tr>
                <td className="font-code text-primary">L-20231023-142</td>
                <td>
                  <div style={{ fontWeight: "700", color: "#dc2626" }}>
                    ERR-304
                  </div>
                  <div style={{ fontSize: "10px", color: "#94a3b8" }}>
                    경화 불량
                  </div>
                </td>
                <td>
                  <span className="status-tag discard">폐기</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="bottom-right card">
          <div className="card-header-tool">
            <h3>상세 검사 결과</h3>
            <div style={{ display: "flex", gap: "8px" }}>
              <div className="search-wrapper">
                <span className="material-symbols-outlined search-icon">
                  search
                </span>
                <input
                  type="text"
                  className="search-input"
                  placeholder="시리얼 ID 검색..."
                  value={searchSerial}
                  onChange={handleSearchChange}
                />
              </div>
              <div className="filter-wrapper">
                <button
                  type="button"
                  className="btn-secondary-outline"
                  style={{ padding: "6px 8px" }}
                  onClick={() => setShowFilterPanel((prev) => !prev)}
                >
                  <span
                    className="material-symbols-outlined"
                    style={{ margin: 0 }}
                  >
                    tune
                  </span>
                </button>
                {showFilterPanel && (
                  <div className="filter-panel">
                    <div className="filter-panel-title">상태로 필터</div>
                    <div
                      className={`filter-option ${statusFilter === "all" ? "selected" : ""}`}
                      onClick={() => handleStatusFilterChange("all")}
                    >
                      전체 보기 {statusFilter === "all" && "✓"}
                    </div>
                    <div
                      className={`filter-option ${statusFilter === "pass" ? "selected" : ""}`}
                      onClick={() => handleStatusFilterChange("pass")}
                    >
                      합격만 보기 {statusFilter === "pass" && "✓"}
                    </div>
                    <div
                      className={`filter-option ${statusFilter === "fail" ? "selected" : ""}`}
                      onClick={() => handleStatusFilterChange("fail")}
                    >
                      불합격만 보기 {statusFilter === "fail" && "✓"}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
          <table className="data-table">
            <thead>
              <tr>
                <th>시리얼 ID</th>
                <th>테스트 항목</th>
                <th>측정값</th>
                <th>상태</th>
                <th>시간</th>
              </tr>
            </thead>
            <tbody>
              {pageResults.map((row, idx) => (
                  <tr key={idx}>
                    <td className="font-code text-primary">{row.id}</td>
                    <td>{row.test}</td>
                    <td>{row.val}</td>
                    <td
                      style={{
                        fontWeight: "700",
                        color: row.ok ? "#16803d" : "#dc2626",
                      }}
                    >
                      <span
                        className="material-symbols-outlined"
                        style={{
                          fontSize: "16px",
                          verticalAlign: "middle",
                          marginRight: "4px",
                        }}
                      >
                        {row.ok ? "check_circle" : "cancel"}
                      </span>
                      {row.ok ? "합격" : "불합격"}
                    </td>
                    <td style={{ color: "#64748b" }}>
                      {row.time}
                    </td>
                  </tr>
                ))}
              {pageResults.length === 0 && (
                <tr>
                  <td colSpan="5" style={{ textAlign: "center", padding: "30px", color: "#94a3b8" }}>
                    조건에 맞는 검사 결과가 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          <div className="table-footer">
            <span className="page-info">총 {filteredResults.length}개 중 {rangeStart}-{rangeEnd} 표시 중</span>
            <div className="page-btn-group">
              <button
                type="button"
                className="page-btn"
                disabled={safePage <= 1}
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              >
                이전
              </button>
              <button
                type="button"
                className="page-btn"
                disabled={safePage >= totalPages}
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              >
                다음
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

export default QualityPage;