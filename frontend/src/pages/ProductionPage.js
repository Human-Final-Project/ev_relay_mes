import React, { useEffect, useMemo, useState } from "react";
import { Link, useInRouterContext } from "react-router-dom";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { DonutChart, StackedBarChart, summarizeByProcess } from "../components/MesCharts";
import { EmptyState, ErrorState, Field, LoadingState, StatusBadge, formatDate } from "../components/MesComponents";

const processOrder = ["OP20", "OP30", "OP40_OP50", "OP60", "OP70", "OP80"];

export default function ProductionPage() {
  const [filters, setFilters] = useState({ lotNo: "", processCode: "", machineId: "" });
  const [applied, setApplied] = useState({});
  const machines = useApiData(MesApi.getMachines, []);
  const alarms = useApiData(() => MesApi.getMachineAlarms({ cleared: false }), []);
  const logs = useApiData(() => MesApi.getProductionLogs({ ...applied, status: "COMPLETED" }), [JSON.stringify(applied)]);

  useEffect(() => {
    const machineTimer = setInterval(machines.reload, 1000);
    const dataTimer = setInterval(() => { alarms.reload(); logs.reload(); }, 5000);
    return () => { clearInterval(machineTimer); clearInterval(dataTimer); };
  // reload functions are stable for the lifetime of useApiData.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [machines.reload, alarms.reload, logs.reload]);

  const sortedMachines = useMemo(() => [...(machines.data || [])].sort((a, b) => processOrder.indexOf(a.processCode) - processOrder.indexOf(b.processCode)), [machines.data]);
  const summary = useMemo(() => {
    const rows = logs.data || [];
    const ok = rows.reduce((sum, row) => sum + (Number(row.okQty) || 0), 0);
    const ng = rows.reduce((sum, row) => sum + (Number(row.ngQty) || 0), 0);
    return { ok, ng, processes: summarizeByProcess(rows, processOrder) };
  }, [logs.data]);
  const running = sortedMachines.filter((machine) => machine.status === "RUNNING").length;
  const errors = sortedMachines.filter((machine) => machine.status === "ERROR").length;
  const yieldRate = summary.ok + summary.ng ? summary.ok / (summary.ok + summary.ng) * 100 : 0;
  const initialError = [machines, alarms].find((result) => result.error && result.data === null)?.error;
  const reloadAll = () => { machines.reload(); alarms.reload(); logs.reload(); };

  if (initialError) return <ErrorState error={initialError} onRetry={reloadAll} />;

  return <div className="mes-page stitch-dashboard production-monitor-page">
    <div className="dashboard-title-row">
      <div><h1>생산 모니터링</h1><p>공정 진행은 1초, 생산 실적과 알람은 5초마다 자동 갱신됩니다.</p></div>
      <div className="production-title-actions"><div className="live-indicator"><span /> 실시간 데이터</div><button className="btn secondary" onClick={reloadAll}>지금 갱신</button></div>
    </div>

    <div className="dashboard-kpis production-kpis">
      <MonitorKpi label="가동 설비" value={`${running}/${sortedMachines.length}`} unit="RUN" tone="success" />
      <MonitorKpi label="이상 설비" value={errors} unit="ERROR" tone="danger" />
      <MonitorKpi label="활성 알람" value={(alarms.data || []).length} unit="ACTIVE" tone="warning" />
      <MonitorKpi label="완료 생산량" value={summary.ok + summary.ng} unit="EA" tone="primary" />
      <MonitorKpi label="양품률" value={`${yieldRate.toFixed(1)}%`} unit="YIELD" tone="info" />
    </div>

    <section className="dashboard-panel process-overview">
      <div className="dashboard-section-heading">
        <div><h2><span className="material-symbols-outlined">precision_manufacturing</span>실시간 공정 진행</h2><p>카드를 선택하면 설비 관리 상세로 이동합니다.</p></div>
        <AppLink className="dashboard-section-link" to="/machines">설비 관리</AppLink>
      </div>
      <div className="dashboard-process-flow">
        <div className="dashboard-parallel-machines">{sortedMachines.filter((machine) => ["OP20", "OP30"].includes(machine.processCode)).map((machine) => <MachineLink key={machine.machineId} machine={machine} />)}</div>
        <ProcessArrow label="병렬 공정 합류" />
        {sortedMachines.filter((machine) => !["OP20", "OP30"].includes(machine.processCode)).map((machine, index) => <React.Fragment key={machine.machineId}>{index > 0 && <ProcessArrow />}<MachineLink machine={machine} /></React.Fragment>)}
      </div>
    </section>

    <section className="dashboard-panel dashboard-table-panel monitor-alarm-panel">
      <div className="dashboard-section-heading"><h3><span className="material-symbols-outlined">warning</span>활성 알람</h3><AppLink to="/machines?tab=alarms">알람 관리</AppLink></div>
      {!(alarms.data || []).length ? <EmptyState message="활성 알람이 없습니다." /> : <div className="dashboard-table-scroll"><table className="dashboard-table"><thead><tr><th>설비</th><th>알람</th><th>레벨</th><th>메시지</th><th>발생 시각</th></tr></thead><tbody>{alarms.data.slice(0, 6).map((alarm) => <tr key={alarm.machineAlarmHistoryId}><td className="mono">{alarm.machineId}</td><td><strong>{alarm.alarmName || alarm.alarmCode}</strong><br/><small>{alarm.alarmCode}</small></td><td><StatusBadge value={alarm.alarmLevel} /></td><td>{alarm.message || "-"}</td><td className="mono">{formatDate(alarm.occurredAt)}</td></tr>)}</tbody></table></div>}
    </section>

    <section className="dashboard-panel dashboard-table-panel production-results-panel">
      <div className="dashboard-section-heading"><div><h2><span className="material-symbols-outlined" aria-hidden="true">receipt_long</span>생산 실적</h2><p>완료(COMPLETED)된 공정 실적만 표시합니다.</p></div></div>
      <div className="mes-filter production-filter">
        <Field label="LOT"><input value={filters.lotNo} onChange={(event) => setFilters({ ...filters, lotNo: event.target.value })} /></Field>
        <Field label="공정"><select value={filters.processCode} onChange={(event) => setFilters({ ...filters, processCode: event.target.value })}><option value="">전체</option>{processOrder.map((code) => <option key={code}>{code}</option>)}</select></Field>
        <Field label="설비"><select value={filters.machineId} onChange={(event) => setFilters({ ...filters, machineId: event.target.value })}><option value="">전체</option>{sortedMachines.map((machine) => <option key={machine.machineId}>{machine.machineId}</option>)}</select></Field>
        <button className="btn" onClick={() => setApplied({ ...filters })}>조회</button>
      </div>
      {logs.loading && logs.data === null ? <LoadingState /> : logs.error && logs.data === null ? <ErrorState error={logs.error} onRetry={logs.reload} /> : !(logs.data || []).length ? <EmptyState /> : <>
        <div className="production-analytics"><div><div className="chart-heading"><div><h3>공정별 생산량</h3><p>완료된 생산 실적 기준</p></div><div className="chart-key"><span className="ok">OK</span><span className="ng">NG</span></div></div><StackedBarChart rows={summary.processes} /></div><DonutChart ariaLabel="완료된 생산 실적 OK 및 NG 비율" centerValue={`${yieldRate.toFixed(1)}%`} centerLabel="양품률" segments={[{ label: "OK", value: summary.ok, color: "#0ea5a4" }, { label: "NG", value: summary.ng, color: "#f43f5e" }]} /></div>
        <div className="dashboard-table-scroll"><table className="dashboard-table"><thead><tr><th>LOT</th><th>공정/설비</th><th>투입</th><th>OK</th><th>NG</th><th>양품률</th><th>종료</th></tr></thead><tbody>{logs.data.map((log) => <tr key={log.productionLogId}><td className="mono dashboard-lot">{log.lotNo}</td><td>{log.processName}<br/><small className="mono">{log.processCode} · {log.machineId}</small></td><td>{log.inputQty}</td><td className="ok-count">{log.okQty}</td><td className={Number(log.ngQty) ? "ng-count" : "muted-count"}>{log.ngQty}</td><td>{log.inputQty ? `${(log.okQty / log.inputQty * 100).toFixed(1)}%` : "-"}</td><td className="mono">{formatDate(log.endedAt)}</td></tr>)}</tbody></table></div>
      </>}
    </section>
  </div>;
}

function MonitorKpi({ label, value, unit, tone }) {
  return <div className={`dashboard-kpi tone-${tone}`}><span>{label}</span><div><strong>{typeof value === "number" ? value.toLocaleString() : value}</strong><small>{unit}</small></div></div>;
}

function ProcessArrow({ label }) {
  return <div className="dashboard-process-arrow" aria-hidden="true">{label && <span>{label}</span>}<i/><b>→</b></div>;
}

export function MachineCard({ machine, onClick }) {
  const progress = Math.min(100, Math.max(0, Number(machine.progressPercent) || 0));
  const hasProgress = Number(machine.targetQty) > 0;
  return <article className={`dashboard-machine machine-${String(machine.status || "IDLE").toLowerCase()}`} onClick={onClick}>
    <div className="dashboard-machine-head"><span>{machine.processCode}</span><i/></div><strong>{machine.machineId}</strong><small>{machine.processName || machine.machineName}</small>
    {hasProgress ? <div className="dashboard-machine-progress"><div><span>{machine.currentLotNo || "-"}</span><b>{machine.processedQty || 0} / {machine.targetQty} ({progress}%)</b></div><div className="progress-track" role="progressbar" aria-label={`${machine.processName} 진행률`} aria-valuemin="0" aria-valuemax="100" aria-valuenow={progress}><span style={{ width: `${progress}%` }} /></div></div> : <div className="dashboard-machine-empty">대기 중</div>}
    <StatusBadge value={machine.status} />
  </article>;
}

function MachineLink({ machine }) {
  return <AppLink className="machine-card-link" to={`/machines?machineId=${encodeURIComponent(machine.machineId)}`}><MachineCard machine={machine}/></AppLink>;
}

function AppLink({ to, children, ...props }) {
  const inRouter = useInRouterContext();
  return inRouter ? <Link to={to} {...props}>{children}</Link> : <a href={to} {...props}>{children}</a>;
}
