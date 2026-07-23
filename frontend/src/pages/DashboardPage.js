import React, { useEffect, useMemo } from "react";
import { Link } from "react-router-dom";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { ErrorState, LoadingState, StatusBadge, formatDate, statusLabel } from "../components/MesComponents";

const machineOrder = ["OP20", "OP30", "OP40_OP50", "OP60", "OP70", "OP80"];
const machineColors = {
  RUNNING: "#08a85a",
  IDLE: "#8b9bad",
  STOPPED: "#ef7d00",
  ERROR: "#e31b23",
};

export default function DashboardPage() {
  const summary = useApiData(MesApi.getDashboardSummary, []);
  const machines = useApiData(MesApi.getMachines, []);
  const recent = useApiData(MesApi.getRecentProductionLogs, []);
  const pipeline = useApiData(MesApi.getPipelineLots, []);
  const alarms = useApiData(() => MesApi.getMachineAlarms({ cleared: false }), []);
  const assignments = useApiData(MesApi.getAllMachineAssignments, []);
  const reloadSummary = summary.reload;
  const reloadMachines = machines.reload;
  const reloadRecent = recent.reload;
  const reloadPipeline = pipeline.reload;
  const reloadAlarms = alarms.reload;
  const reloadAssignments = assignments.reload;

  useEffect(() => {
    const machineTimer = setInterval(() => {
      reloadMachines();
      reloadPipeline();
    }, 1000);
    const aggregateTimer = setInterval(() => {
      reloadSummary();
      reloadRecent();
      reloadAlarms();
      reloadAssignments();
    }, 5000);
    return () => {
      clearInterval(machineTimer);
      clearInterval(aggregateTimer);
    };
  }, [reloadSummary, reloadMachines, reloadRecent, reloadPipeline, reloadAlarms, reloadAssignments]);

  const reload = () => {
    reloadSummary();
    reloadMachines();
    reloadRecent();
    reloadPipeline();
    reloadAlarms();
    reloadAssignments();
  };
  const initialLoading = [summary, machines, recent, pipeline, alarms, assignments]
    .some((resource) => resource.loading && resource.data === null);
  const initialError = [summary, machines, recent, pipeline, alarms, assignments]
    .find((resource) => resource.error && resource.data === null)?.error;

  const sortedMachines = useMemo(() => [...(machines.data || [])].sort((left, right) =>
    machineOrder.indexOf(left.processCode) - machineOrder.indexOf(right.processCode)
  ), [machines.data]);
  const assignmentsByMachine = useMemo(() => (assignments.data || []).reduce((grouped, assignment) => {
    const rows = grouped[assignment.machineId] || [];
    rows.push(assignment);
    grouped[assignment.machineId] = rows;
    return grouped;
  }, {}), [assignments.data]);

  if (initialLoading) return <LoadingState />;
  if (initialError) return <ErrorState error={initialError} onRetry={reload} />;

  const data = summary.data || {};
  const production = data.production || {};
  const machineSummary = data.machines || {};
  const alarmRows = alarms.data || [];
  const activeLots = pipeline.data || [];
  const kpis = [
    { label: "오늘 생산 OK", value: production.okQty || 0, unit: "EA", tone: "green", icon: "check_circle" },
    { label: "오늘 생산 NG", value: production.ngQty || 0, unit: "EA", tone: "red", icon: "cancel" },
    { label: "진행 중 LOT", value: activeLots.length, unit: "LOTS", tone: "blue", icon: "deployed_code" },
    { label: "가동 설비", value: `${machineSummary.running || 0}/${machineSummary.total || 0}`, unit: "RUN", tone: "blue", icon: "monitoring" },
    { label: "활성 알람", value: data.alarms?.active || 0, unit: "ACTIVE", tone: "orange", icon: "notifications" },
    { label: "자재 부족 품목", value: data.materials?.lowStockItemCount || 0, unit: "SKUS", tone: "red", icon: "inventory_2" },
  ];

  return <div className="operations-dashboard">
    <div className="dashboard-kpi-grid">
      {kpis.map((kpi) => <KpiCard key={kpi.label} {...kpi} />)}
    </div>

    <div className="dashboard-overview-grid">
      <section className="ops-panel production-trend-panel">
        <div className="ops-panel-heading">
          <div className="ops-title"><span className="material-symbols-outlined">list_alt</span><h2>시간대별 완제품 생산 추이 (EA)</h2></div>
          <button type="button" className="dashboard-refresh" onClick={reload}><span className="material-symbols-outlined">refresh</span> 갱신</button>
        </div>
        <div className="trend-legend"><span className="ok">OK</span><span className="ng">NG</span></div>
        <HourlyLineChart rows={data.hourlyProduction || []} />
      </section>

      <section className="ops-panel equipment-assignment-panel">
        <div className="ops-panel-heading">
          <div className="ops-title"><span className="material-symbols-outlined">engineering</span><h2>설비별 책임자 배정</h2></div>
          <Link to="/workers">배정 관리</Link>
        </div>
        <div className="assignment-card-grid">
          {sortedMachines.map((machine) => <EquipmentAssignmentCard
            key={machine.machineId}
            machine={machine}
            assignments={assignmentsByMachine[machine.machineId] || []}
          />)}
        </div>
      </section>
    </div>

    <section className="ops-panel realtime-process-panel">
      <div className="ops-panel-heading">
        <div className="ops-title"><h2>실시간 생산 공정 현황</h2></div>
        <span className="panel-timestamp"><span className="material-symbols-outlined">refresh</span>{formatDate(data.generatedAt)} 기준</span>
      </div>
      <ProcessFlow machines={sortedMachines} />
    </section>

    <div className="dashboard-bottom-grid">
      <section className="ops-panel recent-production-panel">
        <div className="ops-panel-heading">
          <div className="ops-title"><span className="material-symbols-outlined">event_note</span><h2>최근 생산 실적</h2></div>
          <a href="/production">전체보기</a>
        </div>
        <div className="compact-table-wrap">
          <table className="compact-dashboard-table">
            <thead><tr><th>LOT 번호</th><th>공정</th><th>설비</th><th>OK / NG</th><th>종료 시각</th></tr></thead>
            <tbody>{(recent.data || []).slice(0, 5).map((log) => <tr key={log.productionLogId}>
              <td><strong className="lot-link">{log.lotNo}</strong></td>
              <td>{log.processCode}<small>{log.processName}</small></td>
              <td>{log.machineId}</td>
              <td><b className="ok-number">{log.okQty || 0}</b><span> / </span><b className="ng-number">{log.ngQty || 0}</b></td>
              <td>{formatDate(log.endedAt || log.createdAt)}</td>
            </tr>)}
            {!(recent.data || []).length && <tr><td colSpan="5" className="table-empty">최근 생산 실적이 없습니다.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      <section className="ops-panel active-alarm-panel">
        <div className="ops-panel-heading alarm-heading">
          <div className="ops-title"><span className="material-symbols-outlined">warning</span><h2>미해제 알람 목록</h2></div>
          <span className="active-count">{alarmRows.length} ACTIVE</span>
        </div>
        {!alarmRows.length
          ? <div className="dashboard-empty"><span className="material-symbols-outlined">notifications</span><strong>활성 알람이 없습니다.</strong></div>
          : <div className="alarm-list">{alarmRows.slice(0, 5).map((alarm) => <div className="alarm-list-row" key={alarm.machineAlarmHistoryId}>
              <span className="material-symbols-outlined">error</span>
              <div><strong>{alarm.alarmName || alarm.alarmCode}</strong><small>{alarm.machineId} · {alarm.processCode || "-"} · {formatDate(alarm.occurredAt)}</small></div>
              <StatusBadge value={alarm.alarmLevel}/>
            </div>)}</div>}
      </section>
    </div>
  </div>;
}

function KpiCard({ label, value, unit, tone, icon }) {
  return <article className={`dashboard-kpi-card ${tone}`}>
    <span className="kpi-label">{label}</span>
    <div className="kpi-value-row"><strong>{value}</strong><span>{unit}</span></div>
    <span className="kpi-icon"><span className="material-symbols-outlined">{icon}</span></span>
  </article>;
}

function HourlyLineChart({ rows }) {
  const width = 760;
  const height = 236;
  const padding = { left: 42, right: 18, top: 12, bottom: 30 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;
  const values = rows.flatMap((row) => [Number(row.okQty) || 0, Number(row.ngQty) || 0]);
  const rawMax = Math.max(10, ...values);
  const max = Math.ceil(rawMax / 20) * 20;
  const point = (value, index) => {
    const x = padding.left + (rows.length <= 1 ? 0 : index / (rows.length - 1) * chartWidth);
    const y = padding.top + chartHeight - (Number(value) || 0) / max * chartHeight;
    return [x, y];
  };
  const pathFor = (key) => rows.map((row, index) => {
    const [x, y] = point(row[key], index);
    return `${index === 0 ? "M" : "L"} ${x} ${y}`;
  }).join(" ");
  const okArea = rows.length
    ? `${pathFor("okQty")} L ${padding.left + chartWidth} ${padding.top + chartHeight} L ${padding.left} ${padding.top + chartHeight} Z`
    : "";
  return <svg className="hourly-line-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="시간대별 누적 완제품 OK 및 NG 생산량">
    <defs><linearGradient id="ok-area" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="#34ba77" stopOpacity=".28"/><stop offset="100%" stopColor="#34ba77" stopOpacity=".02"/></linearGradient></defs>
    {[0, 1, 2, 3, 4].map((step) => {
      const y = padding.top + chartHeight * step / 4;
      const value = Math.round(max * (4 - step) / 4);
      return <g key={step}><line x1={padding.left} x2={padding.left + chartWidth} y1={y} y2={y} stroke="#dce5ef" strokeDasharray="3 3"/><text x={padding.left - 10} y={y + 4} textAnchor="end">{value}</text></g>;
    })}
    {okArea && <path d={okArea} fill="url(#ok-area)"/>}
    {rows.length > 0 && <path d={pathFor("okQty")} fill="none" stroke="#08a85a" strokeWidth="2.5"/>}
    {rows.length > 0 && <path d={pathFor("ngQty")} fill="none" stroke="#ef2b2d" strokeWidth="2"/>}
    {rows.map((row, index) => {
      const [okX, okY] = point(row.okQty, index);
      const [ngX, ngY] = point(row.ngQty, index);
      return <g key={row.hour}>
        <circle cx={okX} cy={okY} r="3.5" fill="#08a85a"/>
        <circle cx={ngX} cy={ngY} r="3" fill="#fff" stroke="#ef2b2d" strokeWidth="2"/>
        <text x={okX} y={height - 7} textAnchor="middle">{row.hour}</text>
      </g>;
    })}
  </svg>;
}

function EquipmentAssignmentCard({ machine, assignments }) {
  const responsible = assignments.find((assignment) => assignment.assignmentRole === "RESPONSIBLE");
  const workerCount = assignments.filter((assignment) => assignment.assignmentRole === "WORKER").length;
  const initial = responsible?.workerName?.trim()?.slice(0, 1) || "?";
  return <article className="equipment-assignment-card">
    <div className="assignment-machine">
      <span className="assignment-process">{machine.processCode}</span>
      <strong>{machine.machineId}</strong>
      <i title={statusLabel(machine.status)} style={{background:machineColors[machine.status] || "#8b9bad"}}/>
    </div>
    <div className={`responsible-person ${responsible ? "" : "unassigned"}`}>
      <span className="responsible-avatar">{responsible ? initial : <span className="material-symbols-outlined">person_off</span>}</span>
      <div>
        <span>{responsible ? "책임자" : "책임자 미배정"}</span>
        <strong>{responsible?.workerName || "배정 필요"}</strong>
        <small>{responsible ? [responsible.department, responsible.position].filter(Boolean).join(" · ") || responsible.workerNo : machine.processName}</small>
      </div>
    </div>
    <span className="assigned-worker-count">작업자 {workerCount}명</span>
  </article>;
}

function ProcessFlow({ machines }) {
  const parallel = machines.filter((machine) => ["OP20", "OP30"].includes(machine.processCode));
  const serial = machines.filter((machine) => !["OP20", "OP30"].includes(machine.processCode));
  return <div className="dashboard-process-flow">
    <div className="parallel-machine-stack">{parallel.map((machine) => <MachineProcessCard key={machine.machineId} machine={machine}/>)}</div>
    {serial.map((machine) => <React.Fragment key={machine.machineId}><span className="process-arrow">→</span><MachineProcessCard machine={machine}/></React.Fragment>)}
    {!machines.length && <div className="dashboard-empty"><strong>등록된 설비가 없습니다.</strong></div>}
  </div>;
}

function MachineProcessCard({ machine }) {
  const hasProgress = Number(machine.targetQty) > 0;
  return <article className={`process-machine-card ${String(machine.status || "").toLowerCase()}`}>
    <i className="machine-state-dot" style={{background:machineColors[machine.status] || "#8b9bad"}}/>
    <span className="process-code">{machine.processCode}</span>
    <strong>{machine.machineId}</strong>
    <small>{machine.processName || machine.machineName}</small>
    <span className={`machine-state state-${String(machine.status || "").toLowerCase()}`}>{statusLabel(machine.status)}</span>
    {hasProgress && <div className="flow-progress">
      <span>{machine.currentLotNo || "-"}</span>
      <div><i style={{width:`${Math.min(100, machine.progressPercent || 0)}%`}}/></div>
      <strong>{machine.processedQty || 0}/{machine.targetQty}</strong>
    </div>}
  </article>;
}
