import React, { useEffect, useMemo } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { DonutChart } from "../components/MesCharts";
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from "../components/MesComponents";

const processOrder = ["OP20", "OP30", "OP40_OP50", "OP60", "OP70", "OP80"];
const processNames = {
  OP20: "코일 권선",
  OP30: "접점 용접",
  OP40_OP50: "자동 조립",
  OP60: "실링/가스충전",
  OP70: "최종 검사",
  OP80: "마킹/포장",
};
const chartColors = ["#2563eb", "#0ea5e9", "#14b8a6", "#22c55e", "#f59e0b", "#ef4444"];

export default function DashboardPage() {
  const today = useMemo(() => todayRange(), []);
  const summary = useApiData(MesApi.getDashboardSummary, []);
  const machines = useApiData(MesApi.getMachines, []);
  const productionLogs = useApiData(
    () => MesApi.getProductionLogs({ startAt: today.startAt, endAt: today.endAt, status: "COMPLETED" }),
    [today.startAt, today.endAt]
  );
  const defects = useApiData(
    () => MesApi.getDefects({ startAt: today.startAt, endAt: today.endAt }),
    [today.startAt, today.endAt]
  );

  useEffect(() => {
    const machineTimer = setInterval(machines.reload, 1000);
    const aggregateTimer = setInterval(() => {
      summary.reload();
      productionLogs.reload();
      defects.reload();
    }, 5000);
    return () => {
      clearInterval(machineTimer);
      clearInterval(aggregateTimer);
    };
  }, [machines.reload, summary.reload, productionLogs.reload, defects.reload]);

  const reload = () => {
    summary.reload();
    machines.reload();
    productionLogs.reload();
    defects.reload();
  };
  const resources = [summary, machines, productionLogs, defects];
  const loading = resources.some((resource) => resource.loading && resource.data === null);
  const error = resources.find((resource) => resource.error && resource.data === null)?.error;

  const data = summary.data || {};
  const production = data.production || {};
  const totalProduction = Number(production.okQty || 0) + Number(production.ngQty || 0);
  const defectRate = totalProduction > 0 ? Number(production.ngQty || 0) / totalProduction * 100 : 0;
  const hourly = useMemo(() => summarizeHourly(productionLogs.data || []), [productionLogs.data]);
  const defectTypes = useMemo(() => summarizeDefects(defects.data || []), [defects.data]);
  const efficiency = useMemo(() => summarizeEfficiency(productionLogs.data || []), [productionLogs.data]);
  const sortedMachines = useMemo(() => [...(machines.data || [])].sort(
    (left, right) => processOrder.indexOf(left.processCode) - processOrder.indexOf(right.processCode)
  ), [machines.data]);

  if (loading) return <LoadingState/>;
  if (error) return <ErrorState error={error} onRetry={reload}/>;

  return <div className="mes-page operations-dashboard dashboard-v2">
    <PageHeader
      title="대시보드"
      description="당일 생산·품질 지표와 실시간 설비 가동 현황을 확인합니다."
      actions={<><span className="live-indicator">● 설비 1초 · 집계 5초 갱신</span><button className="btn secondary" onClick={reload}>지금 갱신</button></>}
    />

    <div className="dashboard-primary-kpis">
      <MetricCard label="당일 총 생산량" value={totalProduction.toLocaleString()} unit="EA" description={`OK ${Number(production.okQty || 0).toLocaleString()} · NG ${Number(production.ngQty || 0).toLocaleString()}`} icon="factory"/>
      <MetricCard label="당일 불량률" value={`${defectRate.toFixed(1)}%`} unit="NG RATE" description={`${Number(production.ngQty || 0).toLocaleString()}개 불량`} icon="percent" tone={defectRate > 5 ? "danger" : "normal"}/>
    </div>

    <div className="dashboard-analysis-grid">
      <section className="mes-card dashboard-chart-card">
        <div className="chart-heading"><div><h2>불량 유형 분석</h2><p>오늘 발생한 불량 코드별 수량</p></div></div>
        {defectTypes.length ? <DonutChart
          ariaLabel="당일 불량 유형별 수량"
          centerValue={`${defectTypes.reduce((sum, item) => sum + item.value, 0)} EA`}
          centerLabel="불량 수량"
          segments={defectTypes}
        /> : <EmptyState message="오늘 발생한 불량 이력이 없습니다."/>}
      </section>

      <section className="mes-card dashboard-chart-card hourly-production-card">
        <div className="chart-heading"><div><h2>실시간 생산 현황</h2><p>시간 단위 OP80 완제품 생산량</p></div><div className="chart-key"><span className="ok">OK</span><span className="ng">NG</span></div></div>
        <HourlyBarChart rows={hourly}/>
      </section>
    </div>

    <section className="mes-card">
      <div className="pipeline-heading"><div><h2>설비 가동 현황</h2><p>실시간 설비 상태와 현재 작업 LOT</p></div><MachineStatusSummary rows={sortedMachines}/></div>
      <div className="dashboard-machine-grid">
        {sortedMachines.map((machine) => <article className={`dashboard-machine-card status-${String(machine.status || "IDLE").toLowerCase()}`} key={machine.machineId}>
          <div><strong>{machine.processCode} · {machine.processName}</strong><span className="mono">{machine.machineId}</span></div>
          <StatusBadge value={machine.status}/>
          <dl><dt>현재 LOT</dt><dd>{machine.currentLotNo || "-"}</dd><dt>진행률</dt><dd>{machine.targetQty > 0 ? `${machine.processedQty || 0}/${machine.targetQty} (${machine.progressPercent || 0}%)` : "대기"}</dd></dl>
        </article>)}
      </div>
    </section>

    <section className="mes-card">
      <div className="chart-heading"><div><h2>공정효율</h2><p>당일 공정별 OK 수량 ÷ 투입 수량</p></div></div>
      <EfficiencyChart rows={efficiency}/>
    </section>
  </div>;
}

function MetricCard({ label, value, unit, description, icon, tone = "normal" }) {
  return <article className={`dashboard-metric-card ${tone}`}>
    <div><span>{label}</span><strong>{value}</strong><small>{unit}</small><p>{description}</p></div>
    <span className="material-symbols-outlined">{icon}</span>
  </article>;
}

function HourlyBarChart({ rows }) {
  const max = Math.max(1, ...rows.map((row) => row.ok + row.ng));
  if (!rows.some((row) => row.ok + row.ng > 0)) return <EmptyState message="오늘 완료된 OP80 생산 실적이 없습니다."/>;
  return <div className="hourly-bar-chart" role="img" aria-label="시간대별 OP80 생산량 막대 그래프">
    {rows.map((row) => <div className="hourly-bar-column" key={row.hour}>
      <div className="hourly-bars" title={`${row.hour}시 OK ${row.ok}, NG ${row.ng}`}>
        <span className="hourly-ok" style={{ height: `${row.ok / max * 100}%` }}/>
        <span className="hourly-ng" style={{ height: `${row.ng / max * 100}%` }}/>
      </div>
      <strong>{row.ok + row.ng}</strong><span>{String(row.hour).padStart(2, "0")}시</span>
    </div>)}
  </div>;
}

function EfficiencyChart({ rows }) {
  if (!rows.some((row) => row.input > 0)) return <EmptyState message="오늘 집계할 생산 실적이 없습니다."/>;
  return <div className="efficiency-chart">
    {rows.map((row) => <div className="efficiency-row" key={row.code}>
      <div><strong>{row.code}</strong><span>{row.name}</span></div>
      <div className="efficiency-track"><span style={{ width: `${row.rate}%` }}/></div>
      <strong>{row.input > 0 ? `${row.rate.toFixed(1)}%` : "-"}</strong>
      <small>OK {row.ok} / 투입 {row.input}</small>
    </div>)}
  </div>;
}

function MachineStatusSummary({ rows }) {
  const counts = rows.reduce((result, row) => {
    result[row.status] = (result[row.status] || 0) + 1;
    return result;
  }, {});
  return <div className="machine-status-summary">
    {["RUNNING", "IDLE", "ERROR", "STOPPED"].map((status) => <span key={status}><i className={`status-dot status-${status.toLowerCase()}`}/>{status} {counts[status] || 0}</span>)}
  </div>;
}

function summarizeHourly(logs) {
  const rows = Array.from({ length: 24 }, (_, hour) => ({ hour, ok: 0, ng: 0 }));
  logs.filter((log) => log.processCode === "OP80").forEach((log) => {
    const date = new Date(log.endedAt || log.createdAt);
    if (!Number.isNaN(date.getTime())) {
      rows[date.getHours()].ok += Number(log.okQty) || 0;
      rows[date.getHours()].ng += Number(log.ngQty) || 0;
    }
  });
  return rows;
}

function summarizeDefects(rows) {
  const grouped = new Map();
  rows.forEach((row) => {
    const label = row.defectName || row.defectCode || "기타";
    grouped.set(label, (grouped.get(label) || 0) + (Number(row.defectQty) || 0));
  });
  return [...grouped.entries()]
    .sort((left, right) => right[1] - left[1])
    .slice(0, 6)
    .map(([label, value], index) => ({ label, value, color: chartColors[index % chartColors.length] }));
}

function summarizeEfficiency(logs) {
  const grouped = Object.fromEntries(processOrder.map((code) => [code, { code, name: processNames[code], input: 0, ok: 0 }]));
  logs.forEach((log) => {
    const row = grouped[log.processCode];
    if (!row) return;
    row.input += Number(log.inputQty) || 0;
    row.ok += Number(log.okQty) || 0;
  });
  return processOrder.map((code) => ({ ...grouped[code], rate: grouped[code].input > 0 ? grouped[code].ok / grouped[code].input * 100 : 0 }));
}

function todayRange() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { startAt: toLocalDateTime(start), endAt: toLocalDateTime(end) };
}

function toLocalDateTime(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
