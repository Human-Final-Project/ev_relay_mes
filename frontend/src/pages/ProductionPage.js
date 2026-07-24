import React, { useEffect, useMemo, useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge, formatDate } from "../components/MesComponents";

const processOrder = ["OP20", "OP30", "OP40_OP50", "OP60", "OP70", "OP80"];
const sequentialProcessCodes = ["OP40_OP50", "OP60", "OP70", "OP80"];

export default function ProductionPage() {
  const machines = useApiData(MesApi.getMachines, []);
  const pipelineLots = useApiData(MesApi.getPipelineLots, []);
  const [selectedMachineId, setSelectedMachineId] = useState("");
  const [assignments, setAssignments] = useState(null);
  const [detailError, setDetailError] = useState(null);
  const [updatedAt, setUpdatedAt] = useState(new Date());

  useEffect(() => {
    const timer = setInterval(() => {
      machines.reload();
      pipelineLots.reload();
      setUpdatedAt(new Date());
    }, 1000);
    return () => clearInterval(timer);
  }, [machines.reload, pipelineLots.reload]);

  useEffect(() => {
    if (!selectedMachineId) {
      setAssignments(null);
      return;
    }
    setDetailError(null);
    setAssignments(null);
    MesApi.getMachineAssignments(selectedMachineId)
      .then((response) => setAssignments(response.data || []))
      .catch(setDetailError);
  }, [selectedMachineId]);

  const sorted = useMemo(() => [...(machines.data || [])].sort(
    (left, right) => processOrder.indexOf(left.processCode) - processOrder.indexOf(right.processCode)
  ), [machines.data]);
  const machineByProcess = useMemo(() => Object.fromEntries(
    sorted.map((machine) => [machine.processCode, machine])
  ), [sorted]);
  const selected = sorted.find((machine) => machine.machineId === selectedMachineId) || null;
  const loading = (machines.loading && machines.data === null)
    || (pipelineLots.loading && pipelineLots.data === null);
  const error = (machines.data === null && machines.error)
    || (pipelineLots.data === null && pipelineLots.error);
  const reload = () => {
    machines.reload();
    pipelineLots.reload();
    setUpdatedAt(new Date());
  };

  return <div className="mes-page production-monitor-page">
    <PageHeader
      title="생산 모니터링"
      description="병렬 선행 공정부터 완제품 포장까지 설비와 LOT 진행 상태를 실시간으로 확인합니다."
      actions={<><span className="live-indicator">● 1초 자동 갱신</span><button className="btn secondary" onClick={reload}>지금 갱신</button></>}
    />

    {loading ? <LoadingState/> : error ? <ErrorState error={error} onRetry={reload}/> : <>
      <section className="mes-card live-process-panel">
        <div className="live-process-heading">
          <div>
            <h2><span className="material-symbols-outlined" aria-hidden="true">precision_manufacturing</span>실시간 생산 공정 현황</h2>
            <p>{formatMonitorTime(updatedAt)} 기준</p>
          </div>
          <ProcessLegend/>
        </div>

        <div className="live-process-scroll">
          <div className="live-process-flow" aria-label="EV Relay 생산 공정 흐름">
            <div className="live-parallel-stack">
            {["OP20", "OP30"].map((processCode) => <MachineCard
              compact
              key={processCode}
              machine={machineByProcess[processCode] || placeholderMachine(processCode)}
              selected={selectedMachineId === machineByProcess[processCode]?.machineId}
              onClick={() => machineByProcess[processCode] && setSelectedMachineId(machineByProcess[processCode].machineId)}
            />)}
          </div>

          <div className="parallel-merge" aria-hidden="true">
            <span>병렬 공정 완료</span>
          </div>

          {sequentialProcessCodes.map((processCode, index) => <React.Fragment key={processCode}>
            <MachineCard
              machine={machineByProcess[processCode] || placeholderMachine(processCode)}
              selected={selectedMachineId === machineByProcess[processCode]?.machineId}
              onClick={() => machineByProcess[processCode] && setSelectedMachineId(machineByProcess[processCode].machineId)}
            />
            {index < sequentialProcessCodes.length - 1 && <div className="process-arrow" aria-hidden="true">→</div>}
          </React.Fragment>)}
          </div>
        </div>
      </section>

      <div className="mes-grid two">
        <section className="mes-card">
          <div className="pipeline-heading"><div><h2>파이프라인 LOT 현황</h2><p>현재 생산 중이거나 설비 복구를 기다리는 LOT입니다.</p></div><strong>{(pipelineLots.data || []).length}개 진행</strong></div>
          {!(pipelineLots.data || []).length ? <EmptyState message="현재 파이프라인에 투입된 LOT이 없습니다."/> :
            <div className="mes-table-wrap"><table className="mes-table pipeline-table">
              <thead><tr><th>작업지시</th><th>LOT</th><th>현재 공정</th><th>구분</th><th>투입</th><th>상태</th><th>시작</th></tr></thead>
              <tbody>{pipelineLots.data.map((lot) => <tr key={lot.lotId}>
                <td>{lot.orderNo}<br/><small>WO #{lot.workOrderId}</small></td>
                <td className="mono">{lot.lotNo}</td>
                <td><strong>{lot.currentProcessCode || "-"}</strong><br/><small>{lot.currentProcessName || "공정 대기"}</small></td>
                <td>{lot.lotType} · {lot.productionRound}차</td>
                <td>{lot.inputQty}</td>
                <td><StatusBadge value={lot.status}/></td>
                <td>{formatDate(lot.startedAt)}</td>
              </tr>)}</tbody>
            </table></div>}
        </section>

        <section className="mes-card">
          <h2>선택 설비 배정 정보</h2>
          {!selected ? <EmptyState message="공정 카드를 선택하세요."/> : detailError ? <ErrorState error={detailError}/> : assignments === null ? <LoadingState/> : <>
            <dl className="detail-list">
              <dt>설비</dt><dd>{selected.machineName} ({selected.machineId})</dd>
              <dt>공정</dt><dd>{selected.processName} ({selected.processCode})</dd>
              <dt>현재 상태</dt><dd><StatusBadge value={selected.status}/></dd>
              <dt>현재 LOT</dt><dd>{selected.currentLotNo || "-"}</dd>
              <dt>담당자</dt><dd>{assignments.map((assignment) => `${assignment.workerName}(${assignment.assignmentRole})`).join(", ") || "미배정"}</dd>
            </dl>
          </>}
        </section>
      </div>
    </>}
  </div>;
}

export function MachineCard({ machine, onClick, selected, compact = false }) {
  const hasProgress = Number(machine.targetQty) > 0;
  const status = String(machine.status || "IDLE").toUpperCase();
  const interactive = typeof onClick === "function" && !machine.placeholder;
  return <button
    type="button"
    className={`live-machine-card ${compact ? "compact" : ""} status-${status.toLowerCase()} ${selected ? "selected" : ""}`}
    onClick={onClick}
    disabled={!interactive}
    aria-pressed={selected}
  >
    <span className={`live-machine-status-dot status-${status.toLowerCase()}`} aria-hidden="true"/>
    <span className="live-machine-code">{machine.processCode}</span>
    <strong>{machine.machineId || "설비 미등록"}</strong>
    <small>{machine.processName || machine.machineName || "공정 정보 없음"}</small>
    <span className="live-machine-lot">{machine.currentLotNo || "대기 중"}</span>
    {hasProgress && <span className="live-machine-progress">
      <span className="live-progress-caption"><span>{machine.processedQty || 0} / {machine.targetQty} ({machine.progressPercent || 0}%)</span></span>
      <span className="progress-track" role="progressbar" aria-label={`${machine.processName} 진행률`} aria-valuemin="0" aria-valuemax="100" aria-valuenow={machine.progressPercent || 0}><span style={{ width: `${machine.progressPercent || 0}%` }}/></span>
    </span>}
    <span className="live-machine-badge"><StatusBadge value={status}/></span>
  </button>;
}

function ProcessLegend() {
  return <div className="process-status-legend" aria-label="설비 상태 범례">
    {["RUNNING", "IDLE", "ERROR"].map((status) => <span key={status}><i className={`status-dot status-${status.toLowerCase()}`}/>{status}</span>)}
  </div>;
}

function placeholderMachine(processCode) {
  return {
    placeholder: true,
    processCode,
    machineId: "-",
    processName: "설비 정보 없음",
    status: "IDLE",
  };
}

function formatMonitorTime(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}. ${date.getMonth() + 1}. ${date.getDate()}. ${pad(date.getHours())}시 ${pad(date.getMinutes())}분 ${pad(date.getSeconds())}초`;
}
