import React, { useEffect, useMemo } from "react";
import { Link } from "react-router-dom";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { DonutChart } from "../components/MesCharts";
import { EmptyState, ErrorState, LoadingState, StatusBadge, formatDate } from "../components/MesComponents";

const machineOrder = ["OP20", "OP30", "OP40_OP50", "OP60", "OP70", "OP80"];
const canClearAlarm = (role) => ["ADMIN", "MANAGER", "OPERATOR"].includes(role);

function todayRange() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { startAt: localDateTime(start), endAt: localDateTime(end) };
}

function localDateTime(value) {
  const part = (number) => String(number).padStart(2, "0");
  return `${value.getFullYear()}-${part(value.getMonth() + 1)}-${part(value.getDate())}T${part(value.getHours())}:${part(value.getMinutes())}:${part(value.getSeconds())}`;
}

function statusRank(status) {
  return { ERROR: 0, RUNNING: 1, IDLE: 2, STOPPED: 3 }[status] ?? 4;
}

export default function DashboardPage({ currentUser }) {
  const summary = useApiData(MesApi.getDashboardSummary, []);
  const machines = useApiData(MesApi.getMachines, []);
  const lots = useApiData(() => MesApi.getLots({ status: "RUNNING" }), []);
  const logs = useApiData(() => MesApi.getProductionLogs({ ...todayRange(), status: "COMPLETED" }), []);
  const alarms = useApiData(() => MesApi.getMachineAlarms({ cleared: false }), []);

  useEffect(() => {
    const machineTimer = setInterval(machines.reload, 1000);
    const dashboardTimer = setInterval(() => {
      summary.reload();
      lots.reload();
      logs.reload();
      alarms.reload();
    }, 5000);
    const refresh = () => {
      summary.reload();
      machines.reload();
      lots.reload();
      logs.reload();
      alarms.reload();
    };
    window.addEventListener("mes:refresh", refresh);
    return () => {
      clearInterval(machineTimer);
      clearInterval(dashboardTimer);
      window.removeEventListener("mes:refresh", refresh);
    };
  // reload functions are stable for the lifetime of each useApiData hook.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [summary.reload, machines.reload, lots.reload, logs.reload, alarms.reload]);

  const initialLoading = [summary, machines, lots, logs, alarms].some((result) => result.loading && result.data === null);
  const initialError = [summary, machines, lots, logs, alarms].find((result) => result.error && result.data === null)?.error;
  const reloadAll = () => window.dispatchEvent(new Event("mes:refresh"));

  const s = summary.data || {};
  const sortedMachines = useMemo(() => [...(machines.data || [])].sort((left, right) => {
    const processDifference = machineOrder.indexOf(left.processCode) - machineOrder.indexOf(right.processCode);
    return processDifference || statusRank(left.status) - statusRank(right.status);
  }), [machines.data]);
  const completedLogs = useMemo(() => logs.data || [], [logs.data]);
  const activeAlarms = alarms.data || [];
  const hourly = useMemo(() => hourlyProduction(completedLogs), [completedLogs]);
  const recentLogs = completedLogs.slice(0, 4);

  if (initialLoading) return <LoadingState />;
  if (initialError) return <ErrorState error={initialError} onRetry={reloadAll} />;

  const kpis = [
    { label: "오늘 생산 OK", value: s.production?.okQty || 0, unit: "EA", tone: "success" },
    { label: "오늘 생산 NG", value: s.production?.ngQty || 0, unit: "EA", tone: "danger" },
    { label: "진행 중 LOT", value: (lots.data || []).length, unit: "LOTS", tone: "primary" },
    { label: "가동 설비", value: `${s.machines?.running || 0}/${s.machines?.total || 0}`, unit: "RUN", tone: "info" },
    { label: "활성 알람", value: s.alarms?.active || 0, unit: "ACTIVE", tone: "warning" },
    { label: "자재 부족 품목", value: s.materials?.lowStockItemCount || 0, unit: "SKUS", tone: "danger" },
  ];

  return <div className="mes-page stitch-dashboard">
    <div className="dashboard-title-row">
      <div>
        <h1>생산 대시보드</h1>
        <p>공정, 설비, 생산 및 품질 현황을 실시간으로 확인합니다.</p>
      </div>
      <div className="live-indicator"><span /> 실시간 데이터 · 설비 1초 / 집계 5초</div>
    </div>

    <div className="dashboard-kpis">
      {kpis.map((kpi) => <KpiCard key={kpi.label} {...kpi} />)}
    </div>

    <section className="dashboard-panel process-overview">
      <div className="dashboard-section-heading">
        <div><h2><span className="material-symbols-outlined">precision_manufacturing</span>실시간 생산 공정 현황</h2><p>{formatDate(s.generatedAt)} 기준</p></div>
        <div className="machine-legend"><Legend tone="running" label="RUNNING"/><Legend tone="idle" label="IDLE"/><Legend tone="error" label="ERROR"/></div>
      </div>
      <div className="dashboard-process-flow">
        <div className="dashboard-parallel-machines">
          {sortedMachines.filter((machine) => ["OP20", "OP30"].includes(machine.processCode)).map((machine) => <DashboardMachine key={machine.machineId} machine={machine}/>) }
        </div>
        <ProcessArrow label="병렬 공정 합류" />
        {sortedMachines.filter((machine) => !["OP20", "OP30"].includes(machine.processCode)).map((machine, index) => <React.Fragment key={machine.machineId}>
          {index > 0 && <ProcessArrow />}
          <DashboardMachine machine={machine}/>
        </React.Fragment>)}
      </div>
    </section>

    <div className="dashboard-analysis-grid">
      <section className="dashboard-panel hourly-panel">
        <h3>시간대별 완제품 생산 추이 <small>(EA)</small></h3>
        <HourlyChart rows={hourly}/>
      </section>
      <section className="dashboard-panel compact-donut">
        <h3>설비 상태 분포</h3>
        <DonutChart ariaLabel="설비 상태 분포" centerValue={s.machines?.total || 0} centerLabel="EQUIP" segments={[
          { label: "RUN", value: s.machines?.running || 0, color: "#009b55" },
          { label: "IDLE", value: s.machines?.idle || 0, color: "#78909c" },
          { label: "STOP", value: s.machines?.stopped || 0, color: "#e59a00" },
          { label: "ERR", value: s.machines?.error || 0, color: "#d92d20" },
        ]}/>
      </section>
      <section className="dashboard-panel compact-donut">
        <h3>작업지시 상태 분포</h3>
        <DonutChart ariaLabel="작업지시 상태 분포" centerValue={s.workOrders?.total || 0} centerLabel="ORDERS" segments={[
          { label: "COMP", value: s.workOrders?.completed || 0, color: "#0566d9" },
          { label: "RUN", value: s.workOrders?.running || 0, color: "#009b55" },
          { label: "NEW", value: (s.workOrders?.created || 0) + (s.workOrders?.released || 0), color: "#78909c" },
        ]}/>
      </section>
    </div>

    <div className="dashboard-bottom-grid">
      <section className="dashboard-panel dashboard-table-panel">
        <div className="dashboard-section-heading"><h3><span className="material-symbols-outlined">receipt_long</span>최근 생산 실적</h3><Link to="/production">전체보기</Link></div>
        {!recentLogs.length ? <EmptyState message="오늘 완료된 생산 실적이 없습니다."/> : <div className="dashboard-table-scroll"><table className="dashboard-table"><thead><tr><th>LOT 번호</th><th>공정</th><th>설비</th><th>OK / NG</th><th>종료 시각</th></tr></thead><tbody>
          {recentLogs.map((log) => <tr key={log.productionLogId}><td className="mono dashboard-lot">{log.lotNo}</td><td>{log.processCode}<br/><small>{log.processName}</small></td><td className="mono">{log.machineId}</td><td><strong className="ok-count">{log.okQty}</strong><span className="quantity-divider">/</span><strong className={Number(log.ngQty) > 0 ? "ng-count" : "muted-count"}>{log.ngQty}</strong></td><td className="mono">{formatDate(log.endedAt)}</td></tr>)}
        </tbody></table></div>}
      </section>
      <section className="dashboard-panel dashboard-table-panel alarm-panel">
        <div className="dashboard-section-heading"><h3><span className="material-symbols-outlined">warning</span>미해제 알람 목록</h3><span className="active-alarm-count">{activeAlarms.length} ACTIVE</span></div>
        {!activeAlarms.length ? <EmptyState message="활성 알람이 없습니다."/> : <div className="dashboard-table-scroll"><table className="dashboard-table"><thead><tr><th>설비</th><th>메시지</th><th>발생 시각</th><th></th></tr></thead><tbody>
          {activeAlarms.slice(0, 4).map((alarm) => <tr key={alarm.machineAlarmHistoryId}><td><strong className="ng-count mono">{alarm.machineId}</strong><br/><small>{alarm.alarmCode}</small></td><td>{alarm.message || alarm.alarmName}</td><td className="mono">{formatDate(alarm.occurredAt)}</td><td>{canClearAlarm(currentUser?.role) && <button className="alarm-clear-button" onClick={async () => { await MesApi.clearMachineAlarm(alarm.machineAlarmHistoryId); alarms.reload(); machines.reload(); summary.reload(); }}>해제</button>}</td></tr>)}
        </tbody></table></div>}
      </section>
    </div>
  </div>;
}

function KpiCard({ label, value, unit, tone }) {
  return <div className={`dashboard-kpi tone-${tone}`}><span>{label}</span><div><strong>{Number.isFinite(value) ? value.toLocaleString() : value}</strong><small>{unit}</small></div></div>;
}

function Legend({ tone, label }) {
  return <span><i className={`legend-${tone}`}/>{label}</span>;
}

function ProcessArrow({ label }) {
  return <div className="dashboard-process-arrow" aria-hidden="true">{label && <span>{label}</span>}<i/><b>›</b></div>;
}

function DashboardMachine({ machine }) {
  const hasProgress = Number(machine.targetQty) > 0;
  const progress = Math.min(100, Math.max(0, Number(machine.progressPercent) || 0));
  return <article className={`dashboard-machine machine-${String(machine.status || "IDLE").toLowerCase()}`}>
    <div className="dashboard-machine-head"><span>{machine.processCode}</span><i/></div>
    <strong>{machine.machineId}</strong>
    <small>{machine.processName || machine.machineName}</small>
    {hasProgress ? <div className="dashboard-machine-progress"><div><span>{machine.currentLotNo || "-"}</span><b>{machine.processedQty || 0}/{machine.targetQty}</b></div><div className="progress-track"><span style={{ width: `${progress}%` }}/></div></div> : <div className="dashboard-machine-empty">대기 중</div>}
    <StatusBadge value={machine.status}/>
  </article>;
}

function hourlyProduction(logs) {
  const now = new Date();
  const currentHour = now.getHours();
  const firstHour = Math.max(0, currentHour - 7);
  const buckets = Array.from({ length: currentHour - firstHour + 1 }, (_, index) => ({ hour: firstHour + index, quantity: 0 }));
  logs.filter((log) => log.processCode === "OP80").forEach((log) => {
    const endedAt = new Date(log.endedAt || log.createdAt);
    const bucket = buckets.find((row) => row.hour === endedAt.getHours());
    if (bucket) bucket.quantity += (Number(log.okQty) || 0) + (Number(log.ngQty) || 0);
  });
  return buckets;
}

function HourlyChart({ rows }) {
  const max = Math.max(1, ...rows.map((row) => row.quantity));
  return <div className="hourly-chart" role="img" aria-label="시간대별 완제품 생산량">
    {rows.map((row, index) => <div className="hourly-column" key={row.hour}><span className="hourly-value">{row.quantity || ""}</span><i style={{ height: `${Math.max(row.quantity ? 8 : 2, row.quantity / max * 100)}%` }} className={index === rows.length - 1 ? "current" : ""}/><b>{String(row.hour).padStart(2, "0")}</b></div>)}
  </div>;
}
