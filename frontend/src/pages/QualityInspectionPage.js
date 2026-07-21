import React, { useState, useEffect, useCallback, useMemo } from "react";
import MesApi from "../api/MesApi";

const INSPECTION_PROCESS = "OP70";

function judge(value, lower, upper) {
  if (value === "" || value === null || Number.isNaN(Number(value))) return null;
  const num = Number(value);
  if (lower !== null && num < lower) return "fail";
  if (upper !== null && num > upper) return "fail";
  return "pass";
}

function QualityInspectionPage() {
  const [lots, setLots] = useState([]);
  const [selectedLotId, setSelectedLotId] = useState(null);
  const [standards, setStandards] = useState([]);
  const [machines, setMachines] = useState([]);
  const [defectCodes, setDefectCodes] = useState([]);
  const [inspections, setInspections] = useState([]);
  const [expectedQty, setExpectedQty] = useState(null);

  const [checks, setChecks] = useState({});
  const [defectType, setDefectType] = useState("");
  const [defectQty, setDefectQty] = useState("1");
  const [defectComment, setDefectComment] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [flash, setFlash] = useState(false);

  const fetchLots = useCallback(async () => {
    try {
      const res = await MesApi.getLots("RUNNING");
      setLots((res.data || []).filter((l) => l.currentProcessCode === INSPECTION_PROCESS));
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "검사 대상 LOT 목록을 불러오지 못했습니다.");
    }
  }, []);

  const fetchReference = useCallback(async () => {
    try {
      const [standardsRes, machinesRes, defectCodesRes] = await Promise.all([
        MesApi.getInspectionStandards(),
        MesApi.getMachines(),
        MesApi.getDefectCodes(),
      ]);
      setStandards((standardsRes.data || []).filter((s) => s.processCode === INSPECTION_PROCESS && s.useYn === "Y"));
      setMachines((machinesRes.data || []).filter((m) => m.processCode === INSPECTION_PROCESS && m.useYn === "Y"));
      setDefectCodes((defectCodesRes.data || []).filter((d) => d.processCode === INSPECTION_PROCESS && d.useYn === "Y"));
    } catch (err) {
      console.error(err);
    }
  }, []);

  useEffect(() => {
    fetchLots();
    fetchReference();
  }, [fetchLots, fetchReference]);

  useEffect(() => {
    if (!selectedLotId && lots.length > 0) {
      setSelectedLotId(lots[0].lotId);
    }
  }, [lots, selectedLotId]);

  const selectedLot = lots.find((l) => l.lotId === selectedLotId) || null;

  useEffect(() => {
    if (standards.length === 0) return;
    setChecks(
      Object.fromEntries(standards.map((s) => [s.inspectionItem, ""]))
    );
  }, [standards, selectedLotId]);

  const fetchInspections = useCallback(async (lotNo) => {
    if (!lotNo) {
      setInspections([]);
      return;
    }
    try {
      const res = await MesApi.getInspections({ lotNo, processCode: INSPECTION_PROCESS });
      setInspections(res.data || []);
    } catch (err) {
      console.error(err);
    }
  }, []);

  const fetchExpectedQty = useCallback(async (lotNo) => {
    if (!lotNo) {
      setExpectedQty(null);
      return;
    }
    try {
      // OP70 투입수량은 직전 공정(OP60)의 양품수량과 같다 — 참고용으로만 계산한다 (최종 검증은 백엔드가 함).
      const res = await MesApi.getProductionLogs({ lotNo, processCode: "OP60" });
      const sum = (res.data || []).reduce((total, log) => total + log.okQty, 0);
      setExpectedQty(sum || null);
    } catch (err) {
      console.error(err);
    }
  }, []);

  useEffect(() => {
    fetchInspections(selectedLot?.lotNo);
    fetchExpectedQty(selectedLot?.lotNo);
  }, [selectedLot, fetchInspections, fetchExpectedQty]);

  // 유닛 번호별로 그룹핑해서 이미 몇 개의 유닛을 검사했는지, 다음 순번이 몇 번인지 계산한다.
  const unitGroups = useMemo(() => {
    const map = new Map();
    inspections.forEach((i) => {
      if (!map.has(i.unitSeq)) map.set(i.unitSeq, []);
      map.get(i.unitSeq).push(i);
    });
    return [...map.entries()]
      .map(([unitSeq, items]) => ({
        unitSeq,
        items,
        result: items.some((i) => i.result === "NG") ? "NG" : "OK",
      }))
      .sort((a, b) => b.unitSeq - a.unitSeq);
  }, [inspections]);

  const nextUnitSeq = unitGroups.length > 0 ? unitGroups[0].unitSeq + 1 : 1;

  const allChecked = standards.length > 0 && standards.every((s) => checks[s.inspectionItem] !== "" && checks[s.inspectionItem] !== undefined);
  const results = standards.map((s) => judge(checks[s.inspectionItem], s.lowerLimit, s.upperLimit));
  const overallResult = !allChecked ? null : results.some((r) => r === "fail") ? "fail" : "pass";

  function resetForm() {
    setChecks(Object.fromEntries(standards.map((s) => [s.inspectionItem, ""])));
    setDefectType(defectCodes[0]?.defectCode || "");
    setDefectQty("1");
    setDefectComment("");
  }

  async function handleSubmit() {
    if (!selectedLot) return;
    if (machines.length === 0) {
      alert("이 공정(OP70)에 사용 가능한 검사 설비가 없습니다.");
      return;
    }
    if (!allChecked) {
      alert("모든 검사 항목의 측정값을 입력해주세요.");
      return;
    }
    if (overallResult === "fail" && !defectType) {
      alert("불량 유형을 선택해주세요.");
      return;
    }

    setIsSubmitting(true);
    try {
      for (const standard of standards) {
        await MesApi.createInspection({
          lotNo: selectedLot.lotNo,
          machineId: machines[0].machineId,
          processCode: INSPECTION_PROCESS,
          unitSeq: nextUnitSeq,
          inspectionItem: standard.inspectionItem,
          measuredValue: Number(checks[standard.inspectionItem]),
          unit: standard.unit,
        });
      }
      if (overallResult === "fail") {
        await MesApi.createDefect({
          lotNo: selectedLot.lotNo,
          machineId: machines[0].machineId,
          processCode: INSPECTION_PROCESS,
          defectCode: defectType,
          defectQty: Number(defectQty) || 1,
          message: defectComment,
        });
      }
      setFlash(true);
      setTimeout(() => setFlash(false), 1400);
      resetForm();
      await Promise.all([fetchInspections(selectedLot.lotNo), fetchLots()]);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "검사 결과 등록에 실패했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const okUnits = unitGroups.filter((u) => u.result === "OK").length;
  const ngUnits = unitGroups.filter((u) => u.result === "NG").length;

  const styles = `
    .qi { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; }

    .qi-header { margin-bottom: 20px; }
    .qi-header h2 { margin: 0; font-size: 22px; font-weight: 800; color: #0f172a; }
    .qi-header p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; }

    .qi-grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: 20px; align-items: start; }
    @media (max-width: 1000px) { .qi-grid { grid-template-columns: 1fr; } }

    .qi-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 22px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.03); }
    .qi-card-title { font-size: 15px; font-weight: 800; color: #0f172a; margin-bottom: 16px; display: flex; align-items: center; gap: 6px; }

    .qi-field { margin-bottom: 14px; }
    .qi-field:last-child { margin-bottom: 0; }
    .qi-field label { display: block; font-size: 12px; font-weight: 700; color: #64748b; margin-bottom: 6px; }
    .qi-select, .qi-input, .qi-textarea { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; font-family: inherit; background: #ffffff; }
    .qi-select:focus, .qi-input:focus, .qi-textarea:focus { border-color: #0566d9; }
    .qi-textarea { min-height: 70px; resize: vertical; }

    .qi-target-summary { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 14px; font-size: 13px; color: #334155; margin-top: 4px; }
    .qi-target-summary b { color: #0f172a; }

    .qi-check-row { display: grid; grid-template-columns: 110px 1fr auto; gap: 10px; align-items: center; padding: 12px 0; border-top: 1px solid #f1f5f9; }
    .qi-check-row:first-child { border-top: none; }
    .qi-check-label { font-size: 13px; font-weight: 700; color: #0f172a; }
    .qi-check-input-wrap { display: flex; align-items: center; gap: 6px; }
    .qi-check-input-wrap input { width: 100%; box-sizing: border-box; padding: 8px 10px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; }
    .qi-check-input-wrap input:focus { border-color: #0566d9; }
    .qi-check-unit { font-size: 11px; color: #94a3b8; white-space: nowrap; }
    .qi-result-badge { padding: 7px 12px; border-radius: 8px; font-size: 12px; font-weight: 700; white-space: nowrap; background: #f1f5f9; color: #94a3b8; }
    .qi-result-badge.pass { background: #dcfce7; color: #16803d; }
    .qi-result-badge.fail { background: #fee2e2; color: #dc2626; }

    .qi-overall { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-radius: 10px; margin-top: 16px; font-size: 13px; font-weight: 700; }
    .qi-overall.pending { background: #f1f5f9; color: #94a3b8; }
    .qi-overall.pass { background: #dcfce7; color: #16803d; }
    .qi-overall.fail { background: #fee2e2; color: #b91c1c; }

    .qi-defect-box { margin-top: 16px; padding-top: 16px; border-top: 1px dashed #fecaca; }
    .qi-defect-box .qi-card-title { color: #b91c1c; }

    .qi-submit-bar { display: flex; gap: 10px; }
    .qi-btn { flex: 1; padding: 13px; border-radius: 10px; border: none; font-size: 14px; font-weight: 700; cursor: pointer; }
    .qi-btn-ghost { flex: 0 0 auto; padding: 13px 18px; background: #f1f5f9; color: #334155; }
    .qi-btn-ghost:hover { background: #e2e8f0; }
    .qi-btn-primary { background: #0566d9; color: #fff; }
    .qi-btn-primary:hover { opacity: 0.9; }
    .qi-btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

    .qi-history-empty { text-align: center; color: #94a3b8; font-size: 12.5px; padding: 30px 10px; }

    .qi-history-item { display: flex; align-items: flex-start; gap: 10px; padding: 12px 2px; border-top: 1px solid #f1f5f9; }
    .qi-history-item:first-child { border-top: none; }
    .qi-history-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 5px; flex-shrink: 0; }
    .qi-history-dot.OK { background: #16803d; }
    .qi-history-dot.NG { background: #dc2626; }
    .qi-history-content { flex: 1; min-width: 0; }
    .qi-history-target { font-size: 13px; font-weight: 700; color: #0f172a; }
    .qi-history-meta { font-size: 11.5px; color: #94a3b8; margin-top: 2px; }

    .qi-summary-strip { display: flex; gap: 12px; margin-bottom: 20px; }
    .qi-summary-chip { flex: 1; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px 16px; }
    .qi-summary-chip .num { font-size: 22px; font-weight: 800; color: #0f172a; }
    .qi-summary-chip .lbl { font-size: 11.5px; color: #64748b; font-weight: 600; margin-top: 2px; }
    .qi-summary-chip.pass .num { color: #16803d; }
    .qi-summary-chip.fail .num { color: #dc2626; }
    .qi-empty-panel { text-align: center; color: #94a3b8; font-size: 13px; padding: 40px 10px; }
  `;

  return (
    <div className="qi">
      <style>{styles}</style>

      <div className="qi-header">
        <h2>🔍 품질 검사 (OP70 최종검사)</h2>
        <p>최종검사 공정에 도달한 LOT을 선택해 제품 1개씩 검사 결과를 기록하세요.</p>
      </div>

      <div className="qi-summary-strip">
        <div className="qi-summary-chip">
          <div className="num">{unitGroups.length}</div>
          <div className="lbl">이 LOT 검사 완료 수량</div>
        </div>
        <div className="qi-summary-chip pass">
          <div className="num">{okUnits}</div>
          <div className="lbl">합격</div>
        </div>
        <div className="qi-summary-chip fail">
          <div className="num">{ngUnits}</div>
          <div className="lbl">불합격</div>
        </div>
      </div>

      {lots.length === 0 ? (
        <div className="qi-card">
          <div className="qi-empty-panel">
            현재 최종검사(OP70) 단계에 있는 LOT이 없습니다.<br />
            생산 실적 입력에서 OP60까지 완료된 LOT이 있어야 이 화면에 표시됩니다.
          </div>
        </div>
      ) : (
        <div className="qi-grid">
          {/* 좌측: 검사 입력 폼 */}
          <div>
            <div className="qi-card">
              <div className="qi-card-title">🎯 검사 대상 LOT</div>
              <div className="qi-field">
                <select className="qi-select" value={selectedLotId || ""} onChange={(e) => setSelectedLotId(Number(e.target.value))}>
                  {lots.map((lot) => (
                    <option key={lot.lotId} value={lot.lotId}>{lot.lotNo} · {lot.itemName}</option>
                  ))}
                </select>
              </div>
              <div className="qi-target-summary">
                검사 순번: <b>{nextUnitSeq}번째</b>
                {expectedQty !== null && <> / 약 <b>{expectedQty}개</b> (직전 공정 양품 수량 기준 참고치)</>}
              </div>
            </div>

            <div className="qi-card">
              <div className="qi-card-title">✅ 검사 항목 ({standards.length}개)</div>

              {standards.length === 0 && <div className="qi-empty-panel">등록된 검사 기준이 없습니다.</div>}

              {standards.map((s) => {
                const result = judge(checks[s.inspectionItem], s.lowerLimit, s.upperLimit);
                return (
                  <div key={s.inspectionItem} className="qi-check-row">
                    <div className="qi-check-label">{s.itemName}</div>
                    <div className="qi-check-input-wrap">
                      <input
                        type="number"
                        step="0.001"
                        placeholder={`${s.lowerLimit ?? "-"} ~ ${s.upperLimit ?? "-"}`}
                        value={checks[s.inspectionItem] || ""}
                        onChange={(e) => setChecks({ ...checks, [s.inspectionItem]: e.target.value })}
                      />
                      <span className="qi-check-unit">{s.unit}</span>
                    </div>
                    <div className={`qi-result-badge ${result || ""}`}>
                      {result === "pass" ? "합격" : result === "fail" ? "불합격" : "대기"}
                    </div>
                  </div>
                );
              })}

              <div className={`qi-overall ${overallResult === null ? "pending" : overallResult}`}>
                <span>종합 판정</span>
                <span>
                  {overallResult === null && "항목을 모두 입력하세요"}
                  {overallResult === "pass" && "✓ 합격"}
                  {overallResult === "fail" && "✕ 불합격"}
                </span>
              </div>

              {overallResult === "fail" && (
                <div className="qi-defect-box">
                  <div className="qi-card-title">⚠️ 불량 상세 정보</div>
                  <div className="qi-field">
                    <label>불량 유형</label>
                    <select className="qi-select" value={defectType} onChange={(e) => setDefectType(e.target.value)}>
                      {defectCodes.map((d) => (
                        <option key={d.defectCode} value={d.defectCode}>{d.defectName}</option>
                      ))}
                    </select>
                  </div>
                  <div className="qi-field">
                    <label>불량 수량</label>
                    <input type="number" min="1" className="qi-input" value={defectQty} onChange={(e) => setDefectQty(e.target.value)} />
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
              <button type="button" className="qi-btn qi-btn-ghost" onClick={resetForm}>초기화</button>
              <button type="button" className="qi-btn qi-btn-primary" onClick={handleSubmit} disabled={isSubmitting}>
                {isSubmitting ? "등록 중..." : flash ? "저장되었습니다 ✓" : "검사 결과 등록"}
              </button>
            </div>
          </div>

          {/* 우측: 이 LOT의 검사 이력 */}
          <div className="qi-card" style={{ marginBottom: 0 }}>
            <div className="qi-card-title">📋 이 LOT 검사 이력 ({unitGroups.length}건)</div>
            {unitGroups.length === 0 ? (
              <div className="qi-history-empty">아직 등록한 검사 결과가 없습니다.</div>
            ) : (
              unitGroups.map((u) => (
                <div key={u.unitSeq} className="qi-history-item">
                  <div className={`qi-history-dot ${u.result}`} />
                  <div className="qi-history-content">
                    <div className="qi-history-target">{u.unitSeq}번째 유닛 · {u.result === "OK" ? "합격" : "불합격"}</div>
                    <div className="qi-history-meta">
                      {u.items.map((i) => `${i.inspectionItem} ${i.measuredValue}${i.unit}`).join(" · ")}
                    </div>
                    <div className="qi-history-meta">{new Date(u.items[0].inspectedAt).toLocaleString()}</div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default QualityInspectionPage;
