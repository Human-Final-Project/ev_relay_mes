import React, { useMemo, useState } from "react";

// -------------------------------------------------------------
// [데이터 정의] 더미 데이터
// 실제 연동 시 작업지시(WorkOrder) API에서 받아오면 됩니다.
// -------------------------------------------------------------
const workOrders = [
  { id: "WO-2023-0041", product: "EV-Relay Type-B (High Voltage)", lot: "L-20231024-041" },
  { id: "WO-2023-0042", product: "EV-Relay Type-A (Standard)", lot: "L-20231024-042" },
  { id: "WO-2023-0039", product: "Auxiliary Connector Unit", lot: "L-20231023-039" },
];

// QualityPage.js와 동일한 검사 항목 체계를 그대로 사용합니다.
const CHECK_ITEMS = [
  { key: "resistance", label: "저항", unit: "Ω", placeholder: "예: 4.21" },
  { key: "appearance", label: "외관", unit: "% 투명도", placeholder: "예: 98.2" },
  { key: "pressure", label: "압력", unit: "MPa 누출", placeholder: "예: 0.02" },
  { key: "temperature", label: "온도", unit: "°C", placeholder: "예: 23.4" },
  { key: "insulation", label: "절연저항", unit: "MΩ", placeholder: "예: 5.2" },
];

const DEFECT_TYPES = ["코일 단선", "하우징 스크래치", "접점 소손", "레진 누출", "기타"];

const emptyChecks = CHECK_ITEMS.reduce((acc, item) => {
  acc[item.key] = { value: "", result: null }; // result: null | "pass" | "fail"
  return acc;
}, {});

function nowTime() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:${String(d.getSeconds()).padStart(2, "0")}`;
}

function QualityInspectionPage() {
  const [targetMode, setTargetMode] = useState("wo"); // "wo" | "serial"
  const [selectedWoId, setSelectedWoId] = useState(workOrders[0].id);
  const [serialInput, setSerialInput] = useState("");

  const [checks, setChecks] = useState(emptyChecks);

  const [defectType, setDefectType] = useState(DEFECT_TYPES[0]);
  const [defectQty, setDefectQty] = useState("");
  const [defectComment, setDefectComment] = useState("");
  const [defectPhoto, setDefectPhoto] = useState(null);

  const [submittedList, setSubmittedList] = useState([]);
  const [flash, setFlash] = useState(false);

  const selectedWo = workOrders.find((w) => w.id === selectedWoId);

  const allChecked = CHECK_ITEMS.every((item) => checks[item.key].result !== null);
  const hasFail = CHECK_ITEMS.some((item) => checks[item.key].result === "fail");
  const overallResult = !allChecked ? null : hasFail ? "fail" : "pass";

  const targetLabel = useMemo(() => {
    if (targetMode === "wo") {
      return selectedWo ? `${selectedWo.product} (${selectedWo.id})` : "";
    }
    return serialInput || "시리얼 미입력";
  }, [targetMode, selectedWo, serialInput]);

  function setCheckValue(key, value) {
    setChecks((prev) => ({ ...prev, [key]: { ...prev[key], value } }));
  }

  function setCheckResult(key, result) {
    setChecks((prev) => ({ ...prev, [key]: { ...prev[key], result } }));
  }

  function resetForm() {
    setChecks(emptyChecks);
    setDefectType(DEFECT_TYPES[0]);
    setDefectQty("");
    setDefectComment("");
    setDefectPhoto(null);
    setSerialInput("");
  }

  function handleSubmit() {
    if (targetMode === "serial" && !serialInput.trim()) {
      alert("시리얼 ID를 입력해주세요.");
      return;
    }
    if (!allChecked) {
      alert("모든 검사 항목의 합격/불합격을 판정해주세요.");
      return;
    }
    if (overallResult === "fail" && !defectType) {
      alert("불량 유형을 선택해주세요.");
      return;
    }

    const record = {
      id: Date.now(),
      target: targetLabel,
      lot: targetMode === "wo" ? selectedWo?.lot : "-",
      result: overallResult,
      defectType: overallResult === "fail" ? defectType : null,
      defectQty: overallResult === "fail" ? defectQty || "1" : null,
      comment: overallResult === "fail" ? defectComment : "",
      photoName: overallResult === "fail" && defectPhoto ? defectPhoto.name : null,
      time: nowTime(),
    };

    setSubmittedList((prev) => [record, ...prev]);
    setFlash(true);
    setTimeout(() => setFlash(false), 1400);
    resetForm();
  }

  const styles = `
    .qi { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; }

    .qi-header { margin-bottom: 20px; }
    .qi-header h2 { margin: 0; font-size: 22px; font-weight: 800; color: #0f172a; }
    .qi-header p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; }

    .qi-grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: 20px; align-items: start; }
    @media (max-width: 1000px) { .qi-grid { grid-template-columns: 1fr; } }

    .qi-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 22px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.03); }
    .qi-card-title { font-size: 15px; font-weight: 800; color: #0f172a; margin-bottom: 16px; display: flex; align-items: center; gap: 6px; }

    .qi-mode-tabs { display: flex; gap: 8px; margin-bottom: 16px; }
    .qi-mode-btn { flex: 1; padding: 9px; border-radius: 8px; border: 1.5px solid #e2e8f0; background: #ffffff; font-size: 13px; font-weight: 700; color: #64748b; cursor: pointer; }
    .qi-mode-btn.active { border-color: #0566d9; background: #eaf2fc; color: #0566d9; }

    .qi-field { margin-bottom: 14px; }
    .qi-field:last-child { margin-bottom: 0; }
    .qi-field label { display: block; font-size: 12px; font-weight: 700; color: #64748b; margin-bottom: 6px; }
    .qi-select, .qi-input, .qi-textarea { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; font-family: inherit; background: #ffffff; }
    .qi-select:focus, .qi-input:focus, .qi-textarea:focus { border-color: #0566d9; }
    .qi-textarea { min-height: 70px; resize: vertical; }

    .qi-target-summary { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 14px; font-size: 13px; color: #334155; margin-top: 4px; }
    .qi-target-summary b { color: #0f172a; }

    .qi-check-row { display: grid; grid-template-columns: 90px 1fr auto; gap: 10px; align-items: center; padding: 12px 0; border-top: 1px solid #f1f5f9; }
    .qi-check-row:first-child { border-top: none; }
    .qi-check-label { font-size: 13px; font-weight: 700; color: #0f172a; }
    .qi-check-input-wrap { display: flex; align-items: center; gap: 6px; }
    .qi-check-input-wrap input { width: 100%; box-sizing: border-box; padding: 8px 10px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; }
    .qi-check-input-wrap input:focus { border-color: #0566d9; }
    .qi-check-unit { font-size: 11px; color: #94a3b8; white-space: nowrap; }
    .qi-check-toggle { display: flex; gap: 6px; }
    .qi-toggle-btn { padding: 7px 12px; border-radius: 8px; border: 1.5px solid #e2e8f0; background: #ffffff; font-size: 12px; font-weight: 700; cursor: pointer; color: #94a3b8; white-space: nowrap; }
    .qi-toggle-btn.pass.active { border-color: #16803d; background: #dcfce7; color: #16803d; }
    .qi-toggle-btn.fail.active { border-color: #dc2626; background: #fee2e2; color: #dc2626; }

    .qi-overall { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-radius: 10px; margin-top: 16px; font-size: 13px; font-weight: 700; }
    .qi-overall.pending { background: #f1f5f9; color: #94a3b8; }
    .qi-overall.pass { background: #dcfce7; color: #16803d; }
    .qi-overall.fail { background: #fee2e2; color: #b91c1c; }

    .qi-defect-box { margin-top: 16px; padding-top: 16px; border-top: 1px dashed #fecaca; }
    .qi-defect-box .qi-card-title { color: #b91c1c; }

    .qi-file-input { font-size: 12px; color: #64748b; }

    .qi-submit-bar { display: flex; gap: 10px; }
    .qi-btn { flex: 1; padding: 13px; border-radius: 10px; border: none; font-size: 14px; font-weight: 700; cursor: pointer; }
    .qi-btn-ghost { flex: 0 0 auto; padding: 13px 18px; background: #f1f5f9; color: #334155; }
    .qi-btn-ghost:hover { background: #e2e8f0; }
    .qi-btn-primary { background: #0566d9; color: #fff; }
    .qi-btn-primary:hover { opacity: 0.9; }

    .qi-history-empty { text-align: center; color: #94a3b8; font-size: 12.5px; padding: 30px 10px; }

    .qi-history-item { display: flex; align-items: flex-start; gap: 10px; padding: 12px 2px; border-top: 1px solid #f1f5f9; }
    .qi-history-item:first-child { border-top: none; }
    .qi-history-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 5px; flex-shrink: 0; }
    .qi-history-dot.pass { background: #16803d; }
    .qi-history-dot.fail { background: #dc2626; }
    .qi-history-content { flex: 1; min-width: 0; }
    .qi-history-target { font-size: 13px; font-weight: 700; color: #0f172a; }
    .qi-history-meta { font-size: 11.5px; color: #94a3b8; margin-top: 2px; }
    .qi-history-defect { font-size: 12px; color: #b91c1c; margin-top: 4px; }
    .qi-history-comment { font-size: 12px; color: #64748b; margin-top: 2px; font-style: italic; }

    .qi-summary-strip { display: flex; gap: 12px; margin-bottom: 20px; }
    .qi-summary-chip { flex: 1; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px 16px; }
    .qi-summary-chip .num { font-size: 22px; font-weight: 800; color: #0f172a; }
    .qi-summary-chip .lbl { font-size: 11.5px; color: #64748b; font-weight: 600; margin-top: 2px; }
    .qi-summary-chip.pass .num { color: #16803d; }
    .qi-summary-chip.fail .num { color: #dc2626; }
  `;

  const todayPass = submittedList.filter((r) => r.result === "pass").length;
  const todayFail = submittedList.filter((r) => r.result === "fail").length;

  return (
    <div className="qi">
      <style>{styles}</style>

      <div className="qi-header">
        <h2>🔍 품질 검사</h2>
        <p>작업지시 또는 시리얼 ID를 선택해 검사 결과를 기록하세요.</p>
      </div>

      <div className="qi-summary-strip">
        <div className="qi-summary-chip">
          <div className="num">{submittedList.length}</div>
          <div className="lbl">오늘 등록한 검사</div>
        </div>
        <div className="qi-summary-chip pass">
          <div className="num">{todayPass}</div>
          <div className="lbl">합격</div>
        </div>
        <div className="qi-summary-chip fail">
          <div className="num">{todayFail}</div>
          <div className="lbl">불합격</div>
        </div>
      </div>

      <div className="qi-grid">
        {/* 좌측: 검사 입력 폼 */}
        <div>
          <div className="qi-card">
            <div className="qi-card-title">🎯 검사 대상</div>

            <div className="qi-mode-tabs">
              <button
                type="button"
                className={`qi-mode-btn ${targetMode === "wo" ? "active" : ""}`}
                onClick={() => setTargetMode("wo")}
              >
                작업지시로 선택
              </button>
              <button
                type="button"
                className={`qi-mode-btn ${targetMode === "serial" ? "active" : ""}`}
                onClick={() => setTargetMode("serial")}
              >
                시리얼 ID 입력
              </button>
            </div>

            {targetMode === "wo" ? (
              <div className="qi-field">
                <label>작업지시 선택</label>
                <select
                  className="qi-select"
                  value={selectedWoId}
                  onChange={(e) => setSelectedWoId(e.target.value)}
                >
                  {workOrders.map((wo) => (
                    <option key={wo.id} value={wo.id}>
                      {wo.id} · {wo.product}
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <div className="qi-field">
                <label>시리얼 ID</label>
                <input
                  type="text"
                  className="qi-input"
                  placeholder="예: EV-R-49214-AX (스캐너로 스캔 가능)"
                  value={serialInput}
                  onChange={(e) => setSerialInput(e.target.value)}
                />
              </div>
            )}

            <div className="qi-target-summary">
              검사 대상: <b>{targetLabel}</b>
              {targetMode === "wo" && selectedWo && (
                <> · LOT <b>{selectedWo.lot}</b></>
              )}
            </div>
          </div>

          <div className="qi-card">
            <div className="qi-card-title">✅ 검사 항목</div>

            {CHECK_ITEMS.map((item) => (
              <div key={item.key} className="qi-check-row">
                <div className="qi-check-label">{item.label}</div>
                <div className="qi-check-input-wrap">
                  <input
                    type="text"
                    placeholder={item.placeholder}
                    value={checks[item.key].value}
                    onChange={(e) => setCheckValue(item.key, e.target.value)}
                  />
                  <span className="qi-check-unit">{item.unit}</span>
                </div>
                <div className="qi-check-toggle">
                  <button
                    type="button"
                    className={`qi-toggle-btn pass ${checks[item.key].result === "pass" ? "active" : ""}`}
                    onClick={() => setCheckResult(item.key, "pass")}
                  >
                    합격
                  </button>
                  <button
                    type="button"
                    className={`qi-toggle-btn fail ${checks[item.key].result === "fail" ? "active" : ""}`}
                    onClick={() => setCheckResult(item.key, "fail")}
                  >
                    불합격
                  </button>
                </div>
              </div>
            ))}

            <div className={`qi-overall ${overallResult === null ? "pending" : overallResult}`}>
              <span>종합 판정</span>
              <span>
                {overallResult === null && "항목을 모두 판정하세요"}
                {overallResult === "pass" && "✓ 합격"}
                {overallResult === "fail" && "✕ 불합격"}
              </span>
            </div>

            {overallResult === "fail" && (
              <div className="qi-defect-box">
                <div className="qi-card-title">⚠️ 불량 상세 정보</div>
                <div className="qi-field">
                  <label>불량 유형</label>
                  <select
                    className="qi-select"
                    value={defectType}
                    onChange={(e) => setDefectType(e.target.value)}
                  >
                    {DEFECT_TYPES.map((d) => (
                      <option key={d} value={d}>{d}</option>
                    ))}
                  </select>
                </div>
                <div className="qi-field">
                  <label>불량 수량</label>
                  <input
                    type="text"
                    className="qi-input"
                    placeholder="예: 1"
                    value={defectQty}
                    onChange={(e) => setDefectQty(e.target.value)}
                  />
                </div>
                <div className="qi-field">
                  <label>사진 첨부 (선택)</label>
                  <input
                    type="file"
                    accept="image/*"
                    className="qi-file-input"
                    onChange={(e) => setDefectPhoto(e.target.files[0] || null)}
                  />
                </div>
                <div className="qi-field">
                  <label>코멘트</label>
                  <textarea
                    className="qi-textarea"
                    placeholder="불량 원인 추정, 특이사항 등을 입력하세요."
                    value={defectComment}
                    onChange={(e) => setDefectComment(e.target.value)}
                  />
                </div>
              </div>
            )}
          </div>

          <div className="qi-submit-bar">
            <button type="button" className="qi-btn qi-btn-ghost" onClick={resetForm}>
              초기화
            </button>
            <button type="button" className="qi-btn qi-btn-primary" onClick={handleSubmit}>
              {flash ? "저장되었습니다 ✓" : "검사 결과 등록"}
            </button>
          </div>
        </div>

        {/* 우측: 오늘 등록한 검사 이력 */}
        <div className="qi-card" style={{ marginBottom: 0 }}>
          <div className="qi-card-title">📋 오늘 등록한 검사 ({submittedList.length}건)</div>
          {submittedList.length === 0 ? (
            <div className="qi-history-empty">아직 등록한 검사 결과가 없습니다.</div>
          ) : (
            submittedList.map((r) => (
              <div key={r.id} className="qi-history-item">
                <div className={`qi-history-dot ${r.result}`} />
                <div className="qi-history-content">
                  <div className="qi-history-target">{r.target}</div>
                  <div className="qi-history-meta">
                    {r.lot !== "-" && `LOT ${r.lot} · `}
                    {r.result === "pass" ? "합격" : "불합격"} · {r.time}
                  </div>
                  {r.result === "fail" && (
                    <>
                      <div className="qi-history-defect">
                        {r.defectType} · 수량 {r.defectQty}
                        {r.photoName && ` · 📎 ${r.photoName}`}
                      </div>
                      {r.comment && <div className="qi-history-comment">"{r.comment}"</div>}
                    </>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export default QualityInspectionPage;