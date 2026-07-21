import React, { useState, useEffect, useCallback, useMemo } from "react";
import MesApi from "../api/MesApi";
import { pushNotification } from "../utils/notificationBus";

// -------------------------------------------------------------
// 이 공정에 도달하면 검사 집계 실적을 백엔드가 자동으로 생성하므로
// 사람이 직접 실적을 등록할 수 없다 (OP70).
// -------------------------------------------------------------
const BLOCKED_PROCESS = "OP70";
const PARALLEL_FIRST = "OP20";
const PARALLEL_SECOND = "OP30";

const REASON_OPTIONS = ["설비 점검", "자재 부족", "품질 이슈", "인력 부족", "휴식/교대", "기타"];

const STATUS_LABEL = { WAITING: "대기", RUNNING: "생산중", HOLD: "보류", COMPLETED: "완료", SCRAPPED: "폐기" };
const STATUS_CLASS = { WAITING: "pe-badge-idle", RUNNING: "pe-badge-active", HOLD: "pe-badge-idle", COMPLETED: "pe-badge-done", SCRAPPED: "pe-badge-idle" };

function ProductionEntryPage() {
  const [lots, setLots] = useState([]);
  const [machines, setMachines] = useState([]);
  const [selectedLotId, setSelectedLotId] = useState(null);
  const [logs, setLogs] = useState([]);
  const [isLoading, setIsLoading] = useState(false);

  const [processCode, setProcessCode] = useState("");
  const [machineId, setMachineId] = useState("");
  const [inputQty, setInputQty] = useState("");
  const [okQty, setOkQty] = useState("");
  const [ngQty, setNgQty] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [showStopForm, setShowStopForm] = useState(false);
  const [stopReason, setStopReason] = useState(REASON_OPTIONS[0]);
  const [stopNote, setStopNote] = useState("");

  const fetchLots = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await MesApi.getLots("RUNNING");
      setLots(res.data || []);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "생산중인 LOT 목록을 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  const fetchMachines = useCallback(async () => {
    try {
      const res = await MesApi.getMachines();
      setMachines((res.data || []).filter((m) => m.useYn === "Y"));
    } catch (err) {
      console.error(err);
    }
  }, []);

  useEffect(() => {
    fetchLots();
    fetchMachines();
  }, [fetchLots, fetchMachines]);

  useEffect(() => {
    if (!selectedLotId && lots.length > 0) {
      setSelectedLotId(lots[0].lotId);
    }
  }, [lots, selectedLotId]);

  const selectedLot = lots.find((l) => l.lotId === selectedLotId) || null;

  // OP20 공정 중에는 OP30(병렬 공정)에도 실적을 등록할 수 있다 (백엔드 병렬 공정 규칙과 동일).
  const availableProcesses = useMemo(() => {
    if (!selectedLot || !selectedLot.currentProcessCode) return [];
    if (selectedLot.currentProcessCode === BLOCKED_PROCESS) return [];
    if (selectedLot.currentProcessCode === PARALLEL_FIRST) {
      return [
        { code: PARALLEL_FIRST, name: selectedLot.currentProcessName },
        { code: PARALLEL_SECOND, name: "접점 가공/용접" },
      ];
    }
    return [{ code: selectedLot.currentProcessCode, name: selectedLot.currentProcessName }];
  }, [selectedLot]);

  useEffect(() => {
    if (availableProcesses.length > 0) {
      setProcessCode(availableProcesses[0].code);
    } else {
      setProcessCode("");
    }
  }, [availableProcesses]);

  const machinesForProcess = machines.filter((m) => m.processCode === processCode);

  useEffect(() => {
    setMachineId(machinesForProcess[0]?.machineId || "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [processCode, machines]);

  const fetchLogs = useCallback(async (lotNo) => {
    if (!lotNo) {
      setLogs([]);
      return;
    }
    try {
      const res = await MesApi.getProductionLogs({ lotNo });
      setLogs(res.data || []);
    } catch (err) {
      console.error(err);
    }
  }, []);

  useEffect(() => {
    fetchLogs(selectedLot?.lotNo);
  }, [selectedLot, fetchLogs]);

  const todayTotal = useMemo(() => logs.reduce((sum, l) => sum + l.inputQty, 0), [logs]);
  const todayDefect = useMemo(() => logs.reduce((sum, l) => sum + l.ngQty, 0), [logs]);

  const qtyMismatch =
    inputQty !== "" && (Number(okQty) || 0) + (Number(ngQty) || 0) !== Number(inputQty);

  async function handleSubmit() {
    if (!selectedLot) return;
    const input = Number(inputQty);
    const ok = Number(okQty) || 0;
    const ng = Number(ngQty) || 0;
    if (!input || input <= 0) {
      alert("등록할 생산 수량을 입력해주세요.");
      return;
    }
    if (ok + ng !== input) {
      alert("양품 수량과 불량 수량의 합이 투입 수량과 같아야 합니다.");
      return;
    }
    if (!machineId) {
      alert("설비를 선택해주세요.");
      return;
    }

    setIsSubmitting(true);
    try {
      await MesApi.createProductionLog({
        lotNo: selectedLot.lotNo,
        machineId,
        processCode,
        inputQty: input,
        okQty: ok,
        ngQty: ng,
        status: "RUNNING",
      });
      setInputQty("");
      setOkQty("");
      setNgQty("");
      await Promise.all([fetchLots(), fetchLogs(selectedLot.lotNo)]);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "생산 실적 등록에 실패했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleStopSubmit() {
    if (!stopNote.trim() && stopReason === "기타") {
      alert("기타 사유는 상세 내용을 입력해주세요.");
      return;
    }

    // 라인 중단 신고는 별도 이력 테이블 없이 관리자에게 즉시 알림만 전달하는 용도입니다.
    pushNotification({
      targetRole: "admin",
      type: "warn",
      title: `라인 중단 신고 · ${selectedLot?.lotNo || "-"}`,
      desc: `${stopReason}${stopNote ? ` · ${stopNote}` : ""}`,
    });

    setStopNote("");
    setShowStopForm(false);
    alert("중단 사유가 기록되었습니다. 관리자에게 알림이 전송됩니다.");
  }

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
    .pe-wo-meta { display: flex; justify-content: space-between; font-size: 11px; color: #94a3b8; margin-top: 4px; }

    .pe-badge { font-size: 10.5px; font-weight: 700; padding: 3px 8px; border-radius: 10px; white-space: nowrap; }
    .pe-badge-active { background: #dbeafe; color: #1d4ed8; }
    .pe-badge-idle { background: #fef08a; color: #854d0e; }
    .pe-badge-done { background: #dcfce7; color: #16803d; }

    .pe-target-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 18px; margin-bottom: 18px; }
    .pe-target-top { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8px; }
    .pe-target-top .cur { font-size: 22px; font-weight: 800; color: #0566d9; }
    .pe-target-top .goal { font-size: 13px; color: #64748b; font-weight: 600; }
    .pe-target-remaining { font-size: 12px; color: #64748b; }

    .pe-manual-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; }
    .pe-field label { display: block; font-size: 12px; font-weight: 700; color: #64748b; margin-bottom: 6px; }
    .pe-input, .pe-select { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; }
    .pe-input:focus, .pe-select:focus { border-color: #0566d9; }
    .pe-hint { font-size: 11.5px; color: #dc2626; margin: -4px 0 10px 0; }

    .pe-btn { padding: 12px; border-radius: 10px; border: none; font-size: 14px; font-weight: 700; cursor: pointer; width: 100%; }
    .pe-btn-primary { background: #0566d9; color: #fff; }
    .pe-btn-primary:hover { opacity: 0.9; }
    .pe-btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

    .pe-stop-link { text-align: center; margin-top: 14px; font-size: 12.5px; color: #dc2626; font-weight: 700; cursor: pointer; background: none; border: none; width: 100%; padding: 6px; }
    .pe-stop-link:hover { text-decoration: underline; }

    .pe-stop-box { margin-top: 14px; padding-top: 14px; border-top: 1px dashed #fecaca; }
    .pe-select-full, .pe-textarea { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; font-family: inherit; margin-bottom: 10px; }
    .pe-textarea { min-height: 70px; resize: vertical; }
    .pe-stop-submit { background: #fee2e2; color: #b91c1c; }
    .pe-stop-submit:hover { background: #fecaca; }

    .pe-history-empty { text-align: center; color: #94a3b8; font-size: 12.5px; padding: 30px 10px; }
    .pe-history-item { display: flex; align-items: flex-start; gap: 10px; padding: 12px 2px; border-top: 1px solid #f1f5f9; }
    .pe-history-item:first-child { border-top: none; }
    .pe-history-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 5px; flex-shrink: 0; background: #0566d9; }
    .pe-history-content { flex: 1; min-width: 0; }
    .pe-history-main { font-size: 13px; font-weight: 700; color: #0f172a; }
    .pe-history-meta { font-size: 11.5px; color: #94a3b8; margin-top: 2px; }
    .pe-empty-panel { text-align: center; color: #94a3b8; font-size: 13px; padding: 40px 10px; }
  `;

  return (
    <div className="pe">
      <style>{styles}</style>

      <div className="pe-header">
        <h2>📈 생산 실적 입력</h2>
        <p>생산중인 LOT을 선택하고 공정별 실적을 등록하세요.</p>
      </div>

      <div className="pe-summary-strip">
        <div className="pe-summary-chip">
          <div className="num">{todayTotal.toLocaleString()}</div>
          <div className="lbl">이 LOT 누적 등록 수량</div>
        </div>
        <div className="pe-summary-chip defect">
          <div className="num">{todayDefect.toLocaleString()}</div>
          <div className="lbl">그중 불량 수량</div>
        </div>
        <div className="pe-summary-chip">
          <div className="num">{logs.length}</div>
          <div className="lbl">등록된 실적 건수</div>
        </div>
      </div>

      <div className="pe-grid">
        {/* 좌측: LOT 목록 + 입력 */}
        <div>
          <div className="pe-card">
            <div className="pe-card-title">생산중인 LOT ({lots.length}건)</div>
            {isLoading && <div className="pe-empty-panel">불러오는 중...</div>}
            {!isLoading && lots.length === 0 && (
              <div className="pe-empty-panel">생산중(RUNNING) 상태의 LOT이 없습니다.<br />작업지시에서 LOT을 생성하고 가동 상태로 전환해주세요.</div>
            )}
            <div className="pe-wo-list">
              {lots.map((lot) => (
                <div
                  key={lot.lotId}
                  className={`pe-wo-card ${lot.lotId === selectedLotId ? "active" : ""}`}
                  onClick={() => setSelectedLotId(lot.lotId)}
                >
                  <div className="pe-wo-top">
                    <span className="pe-wo-id">{lot.lotNo}</span>
                    <span className={`pe-badge ${STATUS_CLASS[lot.status]}`}>{STATUS_LABEL[lot.status]}</span>
                  </div>
                  <div className="pe-wo-product">{lot.itemName}</div>
                  <div className="pe-wo-meta">
                    <span>투입 {lot.inputQty.toLocaleString()}개</span>
                    <span>현재 공정: {lot.currentProcessName || "-"}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {selectedLot && (
            <div className="pe-card">
              <div className="pe-card-title">생산 실적 등록</div>

              <div className="pe-target-box">
                <div className="pe-target-top">
                  <span className="cur">{selectedLot.lotNo}</span>
                  <span className="goal">투입 {selectedLot.inputQty.toLocaleString()}개</span>
                </div>
                <div className="pe-target-remaining">
                  현재 공정: <strong>{selectedLot.currentProcessName || "-"}</strong>
                  {selectedLot.currentProcessCode === BLOCKED_PROCESS &&
                    " · 최종검사(OP70)는 품질검사 결과로 자동 집계되어 이 화면에서 등록할 수 없습니다."}
                </div>
              </div>

              {availableProcesses.length === 0 ? (
                <div className="pe-empty-panel">이 공정 단계는 실적을 직접 등록할 수 없습니다.</div>
              ) : (
                <>
                  <div className="pe-manual-row">
                    <div className="pe-field">
                      <label>공정</label>
                      <select className="pe-select" value={processCode} onChange={(e) => setProcessCode(e.target.value)}>
                        {availableProcesses.map((p) => (
                          <option key={p.code} value={p.code}>{p.name} ({p.code})</option>
                        ))}
                      </select>
                    </div>
                    <div className="pe-field">
                      <label>설비</label>
                      <select className="pe-select" value={machineId} onChange={(e) => setMachineId(e.target.value)}>
                        {machinesForProcess.length === 0 && <option value="">사용 가능한 설비 없음</option>}
                        {machinesForProcess.map((m) => (
                          <option key={m.machineId} value={m.machineId}>{m.machineName} ({m.machineId})</option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="pe-manual-row" style={{ gridTemplateColumns: "1fr 1fr 1fr" }}>
                    <div className="pe-field">
                      <label>투입 수량</label>
                      <input type="number" min="1" className="pe-input" placeholder="예: 20" value={inputQty} onChange={(e) => setInputQty(e.target.value)} />
                    </div>
                    <div className="pe-field">
                      <label>양품 수량</label>
                      <input type="number" min="0" className="pe-input" placeholder="예: 19" value={okQty} onChange={(e) => setOkQty(e.target.value)} />
                    </div>
                    <div className="pe-field">
                      <label>불량 수량</label>
                      <input type="number" min="0" className="pe-input" placeholder="예: 1" value={ngQty} onChange={(e) => setNgQty(e.target.value)} />
                    </div>
                  </div>
                  {qtyMismatch && <div className="pe-hint">양품 + 불량 수량의 합이 투입 수량과 같아야 합니다.</div>}

                  <button type="button" className="pe-btn pe-btn-primary" onClick={handleSubmit} disabled={isSubmitting || !machineId}>
                    {isSubmitting ? "등록 중..." : "생산 실적 등록"}
                  </button>
                </>
              )}

              <button type="button" className="pe-stop-link" onClick={() => setShowStopForm((v) => !v)}>
                ⚠ 라인 중단 / 지연 사유 신고
              </button>

              {showStopForm && (
                <div className="pe-stop-box">
                  <select className="pe-select-full" value={stopReason} onChange={(e) => setStopReason(e.target.value)}>
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
          )}
        </div>

        {/* 우측: 이 LOT의 등록 이력 */}
        <div className="pe-card" style={{ marginBottom: 0 }}>
          <div className="pe-card-title">이 LOT 실적 이력 ({logs.length}건)</div>
          {!selectedLot ? (
            <div className="pe-history-empty">좌측에서 LOT을 선택하세요.</div>
          ) : logs.length === 0 ? (
            <div className="pe-history-empty">아직 등록된 실적이 없습니다.</div>
          ) : (
            logs.map((l) => (
              <div key={l.productionLogId} className="pe-history-item">
                <div className="pe-history-dot" />
                <div className="pe-history-content">
                  <div className="pe-history-main">
                    {l.processName} · 투입 {l.inputQty} (양품 {l.okQty} / 불량 {l.ngQty}) · {l.machineName}
                  </div>
                  <div className="pe-history-meta">{new Date(l.createdAt).toLocaleString()}</div>
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
