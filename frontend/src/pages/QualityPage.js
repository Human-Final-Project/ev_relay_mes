import React, { useState } from "react";

function QualityPage() {
  const [searchSerial, setSearchInput] = useState("");

  const styles = `
    .mesdash .quality-header-flex { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: var(--xl); }
    .mesdash .quality-header-flex h2 { margin: 0; font-size: 32px; font-weight: 600; line-height: 40px; color: var(--on-surface); }
    .mesdash .quality-header-flex p { margin: 4px 0 0 0; font-size: 16px; color: var(--on-surface-variant); }
    
    .mesdash .btn-group { display: flex; gap: var(--sm); }
    .mesdash .btn-secondary-outline { padding: var(--sm) var(--md); border: 1px solid var(--outline-variant); border-radius: 4px; background: var(--surface-container-lowest); font-size: 14px; font-weight: 700; color: var(--on-surface); cursor: pointer; display: flex; align-items: center; gap: var(--xs); }
    .mesdash .btn-secondary-outline:hover { background-color: var(--surface-container-low); }
    .mesdash .btn-primary-filled { padding: var(--sm) var(--md); border: none; border-radius: 4px; background-color: var(--primary); font-size: 14px; font-weight: 700; color: #ffffff; cursor: pointer; display: flex; align-items: center; gap: var(--xs); }
    .mesdash .btn-primary-filled:hover { opacity: 0.9; }

    .mesdash .bento-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: var(--md); margin-bottom: var(--md); }
    .mesdash .bento-left { grid-column: span 3 / span 3; display: flex; flex-direction: column; gap: var(--md); }
    .mesdash .bento-center { grid-column: span 6 / span 6; }
    .mesdash .bento-right { grid-column: span 3 / span 3; }

    .mesdash .card { background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 8px; padding: var(--md); box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); display: flex; flex-direction: column; }
    .mesdash .card h3 { margin: 0; font-size: 20px; font-weight: 600; line-height: 28px; color: var(--on-surface); }
    
    /* 종합 수율 프로그레스 */
    .mesdash .yield-meta { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--sm); font-size: 12px; font-weight: 700; color: var(--on-surface-variant); }
    .mesdash .yield-badge { padding: var(--xs) 6px; background-color: #d1e7dd; color: var(--tertiary); font-size: 11px; border-radius: 4px; }
    .mesdash .yield-value { display: flex; align-items: baseline; gap: var(--xs); margin-bottom: var(--sm); }
    .mesdash .yield-value .main-num { font-size: 32px; font-weight: 700; color: var(--primary); }
    .mesdash .line-track { height: 6px; width: 100%; background-color: var(--surface-container); border-radius: 9999px; overflow: hidden; }
    .mesdash .line-fill { height: 100%; background-color: var(--primary); border-radius: 9999px; }

    /* 수량 스킨 */
    .mesdash .qty-flex { display: flex; justify-content: space-between; align-items: center; margin-top: var(--xs); }
    .mesdash .qty-flex .total-num { font-size: 24px; font-weight: 700; color: var(--on-surface); }
    .mesdash .qty-sub { display: flex; flex-direction: column; align-items: flex-end; font-size: 12px; font-weight: 700; }

    /* 서클 그래프 */
    .mesdash .circle-flex { display: flex; align-items: center; gap: var(--md); margin-top: var(--xs); }
    .mesdash .circle-graph { position: relative; width: 48px; height: 48px; display: flex; align-items: center; justify-content: center; }
    .mesdash .circle-svg { width: 100%; height: 100%; transform: rotate(-90deg); }
    .mesdash .circle-svg circle { fill: transparent; stroke-width: 4; }
    .mesdash .circle-svg .bg { stroke: var(--surface-container); }
    .mesdash .circle-svg .val { stroke: var(--primary); }
    .mesdash .circle-inner-txt { position: absolute; font-size: 11px; font-weight: 700; }

    /* 공정별 수율 차트 */
    .mesdash .chart-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--lg); }
    .mesdash .chart-select { border: none; font-size: 12px; font-weight: 700; color: var(--on-surface-variant); background: transparent; cursor: pointer; outline: none; }
    .mesdash .bar-container { display: flex; align-items: flex-end; justify-content: space-between; height: 192px; padding: 0 var(--md); }
    .mesdash .bar-column { flex: 1; display: flex; flex-direction: column; align-items: center; gap: var(--sm); }
    .mesdash .bar-track { width: 100%; max-width: 32px; background-color: var(--primary-container); border-top-left-radius: 4px; border-top-right-radius: 4px; position: relative; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; overflow: hidden; }
    .mesdash .bar-fill { width: 100%; background-color: var(--primary); border-top-left-radius: 4px; border-top-right-radius: 4px; transition: height 0.3s; }
    .mesdash .bar-label { font-size: 11px; font-weight: 700; color: var(--on-surface-variant); white-space: nowrap; }
    .mesdash .bar-pct { font-family: "JetBrains Mono", monospace; font-size: 11px; color: var(--primary); font-weight: 700; }

    /* 불량 유형 프로그레스 바 */
    .mesdash .defect-list { display: flex; flex-direction: column; gap: var(--md); margin-top: var(--sm); }
    .mesdash .defect-row { display: flex; flex-direction: column; gap: var(--xs); }
    .mesdash .defect-info { display: flex; justify-content: space-between; font-size: 13px; color: var(--on-surface); }
    .mesdash .defect-info span:last-child { font-weight: 700; }
    .mesdash .defect-bar-track { height: 6px; width: 100%; background-color: var(--surface-container); border-radius: 9999px; overflow: hidden; }
    .mesdash .defect-bar-fill { height: 100%; background-color: var(--error); border-radius: 9999px; }

    /* 하단 테이블 그리드 배치 */
    .mesdash .bottom-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: var(--md); }
    .mesdash .bottom-left { grid-column: span 4 / span 4; }
    .mesdash .bottom-right { grid-column: span 8 / span 8; }
    
    .mesdash .card-header-tool { display: flex; justify-content: space-between; align-items: center; padding-bottom: var(--md); border-bottom: 1px solid var(--outline-variant); margin-bottom: var(--md); }
    .mesdash .search-wrapper { position: relative; display: flex; align-items: center; }
    .mesdash .search-wrapper .search-icon { position: absolute; left: var(--sm); color: var(--outline); font-size: 18px; }
    .mesdash .search-input { padding: 6px var(--md) 6px 32px; border: 1px solid var(--outline-variant); border-radius: 4px; font-size: 13px; outline: none; width: 200px; }
    .mesdash .search-input:focus { border-color: var(--primary); }

    .mesdash .data-table { width: 100%; text-align: left; border-collapse: collapse; }
    .mesdash .data-table th { padding: var(--sm) var(--md); border-bottom: 1px solid var(--outline-variant); font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--on-surface-variant); background-color: var(--surface-container-low); }
    .mesdash .data-table td { padding: var(--sm) var(--md); font-size: 13px; border-bottom: 1px solid rgba(197, 198, 205, 0.3); }
    .mesdash .data-table tbody tr:hover { background-color: var(--surface-container-low); }
    .mesdash .font-code { font-family: "JetBrains Mono", monospace; font-size: 12px; }

    .mesdash .status-tag { padding: var(--xs) var(--sm); border-radius: 4px; font-size: 11px; font-weight: 700; }
    .mesdash .status-tag.isolate { background-color: var(--primary-container); color: var(--primary); }
    .mesdash .status-tag.inspect { background-color: var(--surface-container-high); color: var(--on-surface-variant); }
    .mesdash .status-tag.scrap { background-color: #ffdad6; color: var(--error); }

    /* 페이지네이션 */
    .mesdash .table-footer { display: flex; justify-content: space-between; align-items: center; margin-top: var(--md); font-size: 12px; color: var(--on-surface-variant); }
    .mesdash .page-btn-group { display: flex; gap: var(--xs); }
    .mesdash .page-btn { padding: var(--xs) var(--sm); border: 1px solid var(--outline-variant); background: var(--surface-container-lowest); border-radius: 4px; cursor: pointer; font-size: 11px; font-weight: 700; }
    .mesdash .page-btn:hover { background-color: var(--surface-container-low); }
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
          <button type="button" className="btn-secondary-outline">
            <span className="material-symbols-outlined">filter_list</span>필터
          </button>
          <button type="button" className="btn-primary-filled">
            <span className="material-symbols-outlined">download</span>리포트
            내보내기
          </button>
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
              <span style={{ fontSize: "12px", color: "var(--outline)" }}>
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
                <div style={{ fontSize: "11px", color: "var(--outline)" }}>
                  효율 최적화됨
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="bento-center card">
          <div className="chart-header">
            <h3>공정별 수율 현황</h3>
            <select className="chart-select">
              <option>A조: 06:00 - 14:00</option>
            </select>
          </div>
          <div className="bar-container">
            {[
              { label: "S-01 권선", pct: "99.5%", h: "99.5%" },
              { label: "S-02 레이저", pct: "97.2%", h: "97.2%" },
              { label: "S-03 하우징", pct: "94.8%", h: "94.8%" },
              { label: "S-04 레진", pct: "98.9%", h: "98.9%" },
              { label: "S-05 최종검사", pct: "96.5%", h: "96.5%" },
            ].map((bar, idx) => (
              <div key={idx} className="bar-column">
                <div className="bar-track">
                  <div className="bar-fill" style={{ height: bar.h }}></div>
                </div>
                <span className="bar-label">{bar.label}</span>
                <span className="bar-pct">{bar.pct}</span>
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
              style={{ color: "var(--outline)", cursor: "pointer" }}
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
                  <div style={{ fontWeight: "700", color: "var(--error)" }}>
                    ERR-402
                  </div>
                  <div style={{ fontSize: "10px", color: "var(--outline)" }}>
                    임피던스 초과
                  </div>
                </td>
                <td>
                  <span className="status-tag scrap">격리됨</span>
                </td>
              </tr>
              <tr>
                <td className="font-code text-primary">L-20231024-005</td>
                <td>
                  <div style={{ fontWeight: "700", color: "var(--error)" }}>
                    ERR-109
                  </div>
                  <div style={{ fontSize: "10px", color: "var(--outline)" }}>
                    정렬 오류
                  </div>
                </td>
                <td>
                  <span className="status-tag inspect">검사중</span>
                </td>
              </tr>
              <tr>
                <td className="font-code text-primary">L-20231023-142</td>
                <td>
                  <div style={{ fontWeight: "700", color: "var(--error)" }}>
                    ERR-304
                  </div>
                  <div style={{ fontSize: "10px", color: "var(--outline)" }}>
                    경화 불량
                  </div>
                </td>
                <td>
                  <span
                    className="status-tag inspect"
                    style={{ backgroundColor: "#191c20", color: "#fff" }}
                  >
                    폐기
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="bottom-right card">
          <div className="card-header-tool">
            <h3>상세 검사 결과</h3>
            <div style={{ display: "flex", gap: "var(--sm)" }}>
              <div className="search-wrapper">
                <span className="material-symbols-outlined search-icon">
                  search
                </span>
                <input
                  type="text"
                  className="search-input"
                  placeholder="시리얼 ID 검색..."
                  value={searchSerial}
                  onChange={(e) => setSearchInput(e.target.value)}
                />
              </div>
              <button
                type="button"
                className="btn-secondary-outline"
                style={{ padding: "6px var(--sm)" }}
              >
                <span
                  className="material-symbols-outlined"
                  style={{ margin: 0 }}
                >
                  tune
                </span>
              </button>
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
              {[
                {
                  id: "EV-R-49201-AX",
                  test: "저항",
                  val: "4.21 Ω (오차: ±0.2)",
                  ok: true,
                  time: "14:42:15",
                },
                {
                  id: "EV-R-49202-AX",
                  test: "외관",
                  val: "98.2% 투명도",
                  ok: true,
                  time: "14:42:30",
                },
                {
                  id: "EV-R-49203-AX",
                  test: "압력",
                  val: "0.12 MPa 누출",
                  ok: false,
                  time: "14:42:58",
                },
                {
                  id: "EV-R-49204-AX",
                  test: "저항",
                  val: "4.18 Ω",
                  ok: true,
                  time: "14:43:12",
                },
                {
                  id: "EV-R-49205-AX",
                  test: "압력",
                  val: "0.01 MPa 누출",
                  ok: true,
                  time: "14:43:45",
                },
              ]
                .filter((row) =>
                  row.id.toLowerCase().includes(searchSerial.toLowerCase()),
                )
                .map((row, idx) => (
                  <tr key={idx}>
                    <td className="font-code text-primary">{row.id}</td>
                    <td>{row.test}</td>
                    <td>{row.val}</td>
                    <td
                      style={{
                        fontWeight: "700",
                        color: row.ok ? "var(--tertiary)" : "var(--error)",
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
                    <td style={{ color: "var(--on-surface-variant)" }}>
                      {row.time}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
          <div className="table-footer">
            <span>총 1,285개 중 1-5 표시 중</span>
            <div className="page-btn-group">
              <button type="button" className="page-btn">
                이전
              </button>
              <button type="button" className="page-btn">
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
