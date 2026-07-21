import React, { useMemo, useState } from "react";
import { pushNotification } from "../utils/notificationBus";

// -------------------------------------------------------------
// [데이터 정의] 더미 작업지시 데이터
// 실제 연동 시 백엔드 작업지시 API의 진행 수량을 그대로 반영하면 됩니다.
// -------------------------------------------------------------
const initialWorkOrders = [
  { id: "WO-2023-0041", product: "EV-Relay Type-B (High Voltage)", target: 600, current: 450, due: "16:30", status: "진행중" },
  { id: "WO-2023-0042", product: "EV-Relay Type-A (Standard)", target: 400, current: 0, due: "18:00", status: "대기" },
  { id: "WO-2023-0039", product: "Auxiliary Connector Unit", target: 200, current: 200, due: "완료", status: "완료" },
];

const REASON_OPTIONS = ["설비 점검", "자재 부족", "품질 이슈", "인력 부족", "휴식/교대", "기타"];

function nowTime() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function ProductionEntryPage() {
  const [workOrders, setWorkOrders] = useState(initialWorkOrders);
  const [selectedId, setSelectedId] = useState(initialWorkOrders[0].id);
  const [qtyInput, setQtyInput] = useState("");
  const [defectQtyInput, setDefectQtyInput] = useState("");

  const [showStopForm, setShowStopForm] = useState(false);
  const [stopReason, setStopReason] = useState(REASON_OPTIONS[0]);
  const [stopNote, setStopNote] = useState("");

  const [log, setLog] = useState([]);
  const [flash, setFlash] = useState(false);

  const selectedWo = workOrders.find((w) => w.id === selectedId);
  const achievedPct = selectedWo
    ? Math.min(100, Math.round((selectedWo.current / selectedWo.target) * 100))
    : 0;

  const remaining = selectedWo ? Math.max(0, selectedWo.target - selectedWo.current) : 0;

  const quickAmounts = [1, 5, 10, 25];

  function addQty(amount) {
    if (!selectedWo || selectedWo.status === "완료") return;
    setWorkOrders((prev) =>
      prev.map((w) => {
        if (w.id !== selectedId) return w;
        const nextCurrent = Math.min(w.target, w.current + amount);
        return {
          ...w,
          current: nextCurrent,
          status: nextCurrent >= w.target ? "완료" : w.status === "대기" ? "진행중" : w.status,
        };
      })
    );
    setLog((prev) => [
      { id: Date.now(), type: "produce", qty: amount, defect: 0, woId: selectedId, time: nowTime() },
      ...prev,
    ]);
    setFlash(true);
    setTimeout(() => setFlash(false), 1000);
  }

  function handleManualSubmit() {
    const qty = parseInt(qtyInput, 10);
    const defectQty = parseInt(defectQtyInput, 10) || 0;
    if (!qty || qty <= 0) {
      alert("등록할 생산 수량을 입력해주세요.");
      return;
    }
    if (!selectedWo || selectedWo.status === "완료") {
      alert("이미 완료된 작업지시입니다.");
      return;
    }

    setWorkOrders((prev) =>
      prev.map((w) => {
        if (w.id !== selectedId) return w;
        const nextCurrent = Math.min(w.target, w.current + qty);
        return {
          ...w,
          current: nextCurrent,
          status: nextCurrent >= w.target ? "완료" : w.status === "대기" ? "진행중" : w.status,
        };
      })
    );
    setLog((prev) => [
      { id: Date.now(), type: "produce", qty, defect: defectQty, woId: selectedId, time: nowTime() },
      ...prev,
    ]);
    setQtyInput("");
    setDefectQtyInput("");
    setFlash(true);
    setTimeout(() => setFlash(false), 1000);
  }

  function handleStopSubmit() {
    if (!stopNote.trim() && stopReason === "기타") {
      alert("기타 사유는 상세 내용을 입력해주세요.");
      return;
    }
    setLog((prev) => [
      { id: Date.now(), type: "stop", reason: stopReason, note: stopNote, woId: selectedId, time: nowTime() },
      ...prev,
    ]);

    // 관리자에게 실시간 알림 전달 (Header 알림 종에 표시됨)
    pushNotification({
      targetRole: "admin",
      type: "warn",
      title: `라인 중단 신고 · ${selectedId}`,
      desc: `${stopReason}${stopNote ? ` · ${stopNote}` : ""}`,
    });

    setStopNote("");
    setShowStopForm(false);
    alert("중단 사유가 기록되었습니다. 관리자에게 알림이 전송됩니다.");
  }

  const todayTotal = useMemo(
    () => log.filter((l) => l.type === "produce").reduce((sum, l) => sum + l.qty, 0),
    [log]
  );
  const todayDefect = useMemo(
    () => log.filter((l) => l.type === "produce").reduce((sum, l) => sum + (l.defect || 0), 0),
    [log]
  );

  const STATUS_CLASS = { "진행중": "pe-badge-active", "대기": "pe-badge-idle", "완료": "pe-badge-done" };

  const styles = `
    .pe { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; }

    .pe-header { margin-bottom: 20px; }
    .pe-header h2 { margin: 0; font-size: 22px; font-weight: 800; color: #0f172a; }
    .pe-header p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; }

    .pe-summary-strip { display: flex; gap: 12px; margin-bottom: 20px; }
    .pe-summary-chip { flex: 1; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px 16px; }
    .pe-summary-chip .num { font-size: 22px; font-weight: 800; color: #0f172a; }
    .pe-summary-chip .lbl { font-size: 11.5px; color: #64748b; font-weight: 600; margin-top: 2px; }
    .pe-summary-chip.defect .num { color: #dc2626; }

    .pe-grid { display: grid; grid-template-columns: 1.3fr 1fr; gap: 20px; align-items: start; }
    @media (max-width: 1000px) { .pe-grid { grid-template-columns: 1fr; } }

    .pe-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 22px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.03); }
    .pe-card-title { font-size: 15px; font-weight: 800; color: #0f172a; margin-bottom: 16px; }

    .pe-wo-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 4px; }
    .pe-wo-card { border: 1.5px solid #e2e8f0; border-radius: 10px; padding: 12px 14px; cursor: pointer; transition: 0.15s; }
    .pe-wo-card:hover { border-color: #93c5fd; }
    .pe-wo-card.active { border-color: #0566d9; background: #f0f9ff; }
    .pe-wo-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .pe-wo-id { font-family: monospace; font-weight: 700; font-size: 12.5px; color: #0f172a; }
    .pe-wo-product { font-size: 13px; color: #334155; margin-bottom: 6px; }
    .pe-wo-progress-track { height: 5px; background: #f1f5f9; border-radius: 99px; overflow: hidden; }
    .pe-wo-progress-fill { height: 100%; background: #0566d9; }
    .pe-wo-meta { display: flex; justify-content: space-between; font-size: 11px; color: #94a3b8; margin-top: 4px; }

    .pe-badge { font-size: 10.5px; font-weight: 700; padding: 3px 8px; border-radius: 10px; white-space: nowrap; }
    .pe-badge-active { background: #dbeafe; color: #1d4ed8; }
    .pe-badge-idle { background: #fef08a; color: #854d0e; }
    .pe-badge-done { background: #dcfce7; color: #16803d; }

    .pe-target-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 18px; margin-bottom: 18px; }
    .pe-target-top { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8px; }
    .pe-target-top .cur { font-size: 26px; font-weight: 800; color: #0566d9; }
    .pe-target-top .goal { font-size: 13px; color: #64748b; font-weight: 600; }
    .pe-target-track { height: 8px; background: #e2e8f0; border-radius: 99px; overflow: hidden; margin-bottom: 8px; }
    .pe-target-fill { height: 100%; background: #0566d9; transition: width 0.3s; }
    .pe-target-remaining { font-size: 12px; color: #64748b; }

    .pe-quick-row { display: flex; gap: 8px; margin-bottom: 14px; }
    .pe-quick-btn { flex: 1; padding: 12px; border-radius: 10px; border: 1.5px solid #bfdbfe; background: #eff6ff; color: #0566d9; font-size: 14px; font-weight: 800; cursor: pointer; }
    .pe-quick-btn:hover { background: #dbeafe; }
    .pe-quick-btn:disabled { opacity: 0.4; cursor: not-allowed; }

    .pe-manual-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; }
    .pe-field label { display: block; font-size: 12px; font-weight: 700; color: #64748b; margin-bottom: 6px; }
    .pe-input { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; }
    .pe-input:focus { border-color: #0566d9; }

    .pe-btn { padding: 12px; border-radius: 10px; border: none; font-size: 14px; font-weight: 700; cursor: pointer; width: 100%; }
    .pe-btn-primary { background: #0566d9; color: #fff; }
    .pe-btn-primary:hover { opacity: 0.9; }

    .pe-stop-link { text-align: center; margin-top: 14px; font-size: 12.5px; color: #dc2626; font-weight: 700; cursor: pointer; background: none; border: none; width: 100%; padding: 6px; }
    .pe-stop-link:hover { text-decoration: underline; }

    .pe-stop-box { margin-top: 14px; padding-top: 14px; border-top: 1px dashed #fecaca; }
    .pe-select, .pe-textarea { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; font-family: inherit; margin-bottom: 10px; }
    .pe-textarea { min-height: 70px; resize: vertical; }
    .pe-stop-submit { background: #fee2e2; color: #b91c1c; }
    .pe-stop-submit:hover { background: #fecaca; }

    .pe-history-empty { text-align: center; color: #94a3b8; font-size: 12.5px; padding: 30px 10px; }
    .pe-history-item { display: flex; align-items: flex-start; gap: 10px; padding: 12px 2px; border-top: 1px solid #f1f5f9; }
    .pe-history-item:first-child { border-top: none; }
    .pe-history-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 5px; flex-shrink: 0; }
    .pe-history-dot.produce { background: #0566d9; }
    .pe-history-dot.stop { background: #dc2626; }
    .pe-history-content { flex: 1; min-width: 0; }
    .pe-history-main { font-size: 13px; font-weight: 700; color: #0f172a; }
    .pe-history-meta { font-size: 11.5px; color: #94a3b8; margin-top: 2px; }
    .pe-history-note { font-size: 12px; color: #64748b; margin-top: 3px; font-style: italic; }
  `;

  return (
    <div className="pe">
      <style>{styles}</style>

      <div className="pe-header">
        <h2>📈 생산 실적 입력</h2>
        <p>담당 작업지시를 선택하고 생산 수량을 실시간으로 등록하세요.</p>
      </div>

      <div className="pe-summary-strip">
        <div className="pe-summary-chip">
          <div className="num">{todayTotal.toLocaleString()}</div>
          <div className="lbl">오늘 등록한 생산량</div>
        </div>
        <div className="pe-summary-chip defect">
          <div className="num">{todayDefect.toLocaleString()}</div>
          <div className="lbl">그중 불량 수량</div>
        </div>
        <div className="pe-summary-chip">
          <div className="num">{log.filter((l) => l.type === "stop").length}</div>
          <div className="lbl">중단 신고 건수</div>
        </div>
      </div>

      <div className="pe-grid">
        {/* 좌측: 작업지시 목록 + 입력 */}
        <div>
          <div className="pe-card">
            <div className="pe-card-title">배정된 작업지시</div>
            <div className="pe-wo-list">
              {workOrders.map((wo) => {
                const pct = Math.min(100, Math.round((wo.current / wo.target) * 100));
                return (
                  <div
                    key={wo.id}
                    className={`pe-wo-card ${wo.id === selectedId ? "active" : ""}`}
                    onClick={() => setSelectedId(wo.id)}
                  >
                    <div className="pe-wo-top">
                      <span className="pe-wo-id">{wo.id}</span>
                      <span className={`pe-badge ${STATUS_CLASS[wo.status]}`}>{wo.status}</span>
                    </div>
                    <div className="pe-wo-product">{wo.product}</div>
                    <div className="pe-wo-progress-track">
                      <div className="pe-wo-progress-fill" style={{ width: `${pct}%` }} />
                    </div>
                    <div className="pe-wo-meta">
                      <span>{wo.current} / {wo.target}</span>
                      <span>기한 {wo.due}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="pe-card">
            <div className="pe-card-title">생산 수량 등록</div>

            {selectedWo && (
              <div className="pe-target-box">
                <div className="pe-target-top">
                  <span className="cur">{selectedWo.current.toLocaleString()}</span>
                  <span className="goal">/ {selectedWo.target.toLocaleString()} 개</span>
                </div>
                <div className="pe-target-track">
                  <div className="pe-target-fill" style={{ width: `${achievedPct}%` }} />
                </div>
                <div className="pe-target-remaining">
                  {selectedWo.status === "완료" ? "목표 수량을 달성했습니다 🎉" : `남은 수량 ${remaining.toLocaleString()}개 · 달성률 ${achievedPct}%`}
                </div>
              </div>
            )}

            <div className="pe-quick-row">
              {quickAmounts.map((amt) => (
                <button
                  key={amt}
                  type="button"
                  className="pe-quick-btn"
                  disabled={!selectedWo || selectedWo.status === "완료"}
                  onClick={() => addQty(amt)}
                >
                  +{amt}
                </button>
              ))}
            </div>

            <div className="pe-manual-row">
              <div className="pe-field">
                <label>직접 입력 (생산 수량)</label>
                <input
                  type="number"
                  min="1"
                  className="pe-input"
                  placeholder="예: 12"
                  value={qtyInput}
                  onChange={(e) => setQtyInput(e.target.value)}
                />
              </div>
              <div className="pe-field">
                <label>그중 불량 수량 (선택)</label>
                <input
                  type="number"
                  min="0"
                  className="pe-input"
                  placeholder="예: 1"
                  value={defectQtyInput}
                  onChange={(e) => setDefectQtyInput(e.target.value)}
                />
              </div>
            </div>

            <button type="button" className="pe-btn pe-btn-primary" onClick={handleManualSubmit}>
              {flash ? "등록되었습니다 ✓" : "생산 수량 등록"}
            </button>

            <button type="button" className="pe-stop-link" onClick={() => setShowStopForm((v) => !v)}>
              ⚠ 라인 중단 / 지연 사유 신고
            </button>

            {showStopForm && (
              <div className="pe-stop-box">
                <select className="pe-select" value={stopReason} onChange={(e) => setStopReason(e.target.value)}>
                  {REASON_OPTIONS.map((r) => (
                    <option key={r} value={r}>{r}</option>
                  ))}
                </select>
                <textarea
                  className="pe-textarea"
                  placeholder="상세 내용을 입력하세요 (관리자에게 즉시 전달됩니다)."
                  value={stopNote}
                  onChange={(e) => setStopNote(e.target.value)}
                />
                <button type="button" className="pe-btn pe-stop-submit" onClick={handleStopSubmit}>
                  중단 사유 제출
                </button>
              </div>
            )}
          </div>
        </div>

        {/* 우측: 오늘 등록 이력 */}
        <div className="pe-card" style={{ marginBottom: 0 }}>
          <div className="pe-card-title">오늘 등록 이력 ({log.length}건)</div>
          {log.length === 0 ? (
            <div className="pe-history-empty">아직 등록한 실적이 없습니다.</div>
          ) : (
            log.map((l) => (
              <div key={l.id} className="pe-history-item">
                <div className={`pe-history-dot ${l.type}`} />
                <div className="pe-history-content">
                  {l.type === "produce" ? (
                    <>
                      <div className="pe-history-main">
                        {l.woId} · +{l.qty}개 등록{l.defect > 0 && ` (불량 ${l.defect})`}
                      </div>
                      <div className="pe-history-meta">{l.time}</div>
                    </>
                  ) : (
                    <>
                      <div className="pe-history-main">{l.woId} · 중단 신고 — {l.reason}</div>
                      <div className="pe-history-meta">{l.time}</div>
                      {l.note && <div className="pe-history-note">"{l.note}"</div>}
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

export default ProductionEntryPage;