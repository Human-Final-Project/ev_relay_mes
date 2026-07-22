import React from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { ErrorState, LoadingState, PageHeader, StatusBadge, formatDate } from "../components/MesComponents";
import { DonutChart, StackedBarChart, summarizeByProcess } from "../components/MesCharts";

const machineOrder = ["OP20", "OP30", "OP40_OP50", "OP60", "OP70", "OP80"];

export default function DashboardPage() {
  const summary = useApiData(MesApi.getDashboardSummary, []);
  const machines = useApiData(MesApi.getMachines, []);
  const recent = useApiData(MesApi.getRecentProductionLogs, []);
  const reload = () => { summary.reload(); machines.reload(); recent.reload(); };
  if (summary.loading || machines.loading || recent.loading) return <LoadingState />;
  if (summary.error || machines.error || recent.error) return <ErrorState error={summary.error || machines.error || recent.error} onRetry={reload} />;
  const s = summary.data || {};
  const sortedMachines = [...(machines.data || [])].sort((a,b) => machineOrder.indexOf(a.processCode)-machineOrder.indexOf(b.processCode));
  const processRows=summarizeByProcess(recent.data||[],machineOrder);
  const productionTotal=(s.production?.okQty||0)+(s.production?.ngQty||0);
  const yieldRate=productionTotal?`${(s.production.okQty/productionTotal*100).toFixed(1)}%`:"0%";
  const kpis = [
    ["오늘 완료 LOT", s.production?.completedLots || 0, "생산 완료"],
    ["오늘 생산 OK", s.production?.okQty || 0, `NG ${s.production?.ngQty || 0}`],
    ["진행 중 작업지시", s.workOrders?.running || 0, `전체 ${s.workOrders?.total || 0}`],
    ["가동 중 설비", `${s.machines?.running || 0} / ${s.machines?.total || 0}`, `이상 ${s.machines?.error || 0}`],
    ["활성 알람", s.alarms?.active || 0, `오늘 발생 ${s.alarms?.occurredToday || 0}`],
    ["부족 자재", s.materials?.lowStockItemCount || 0, `기준 ${s.materials?.lowStockThreshold ?? 100} 이하`],
  ];
  return <div className="mes-page">
    <PageHeader title="생산 대시보드" description={`Backend 집계 기준 · ${formatDate(s.generatedAt)}`} actions={<button className="btn secondary" onClick={reload}>새로고침</button>} />
    <div className="mes-grid kpis">{kpis.map(([label,value,hint]) => <div className="mes-card mes-kpi" key={label}><span className="label">{label}</span><strong className="value">{value}</strong><span className="hint">{hint}</span></div>)}</div>
    <div className="dashboard-charts">
      <section className="mes-card chart-card chart-wide"><div className="chart-heading"><div><h2>최근 공정별 생산량</h2><p>최근 생산 실적의 OK·NG 누적 비교</p></div><div className="chart-key"><span className="ok">OK</span><span className="ng">NG</span></div></div><StackedBarChart rows={processRows}/></section>
      <section className="mes-card chart-card"><div className="chart-heading"><div><h2>오늘 양품률</h2><p>완료 LOT 집계 기준</p></div></div><DonutChart ariaLabel="오늘 OK 및 NG 생산 비율" centerValue={yieldRate} centerLabel="양품률" segments={[{label:"OK",value:s.production?.okQty||0,color:"#0ea5a4"},{label:"NG",value:s.production?.ngQty||0,color:"#f43f5e"}]}/></section>
      <section className="mes-card chart-card"><div className="chart-heading"><div><h2>설비 상태</h2><p>등록 설비 실시간 상태</p></div></div><DonutChart ariaLabel="설비 상태 분포" centerValue={s.machines?.total||0} centerLabel="전체 설비" segments={[{label:"가동",value:s.machines?.running||0,color:"#22c55e"},{label:"대기",value:s.machines?.idle||0,color:"#38bdf8"},{label:"정지",value:s.machines?.stopped||0,color:"#f59e0b"},{label:"이상",value:s.machines?.error||0,color:"#ef4444"}]}/></section>
    </div>
    <section className="mes-card"><h2>공정·설비 흐름</h2><div className="process-flow">
      <div className="parallel-processes">{sortedMachines.filter(m=>["OP20","OP30"].includes(m.processCode)).map(m=><MachineMini key={m.machineId} machine={m}/>)}</div>
      <div style={{textAlign:"center",color:"#94a3b8"}}>합류 →</div>
      {sortedMachines.filter(m=>!["OP20","OP30"].includes(m.processCode)).map(m=><MachineMini key={m.machineId} machine={m}/>)}</div></section>
    <section className="mes-card"><h2>최근 생산 실적</h2><div className="mes-table-wrap"><table className="mes-table"><thead><tr><th>LOT</th><th>공정</th><th>설비</th><th>투입</th><th>OK</th><th>NG</th><th>상태</th><th>수집 시각</th></tr></thead><tbody>{(recent.data || []).map(log=><tr key={log.productionLogId}><td className="mono">{log.lotNo}</td><td>{log.processName}<br/><span className="mono">{log.processCode}</span></td><td className="mono">{log.machineId}</td><td>{log.inputQty}</td><td>{log.okQty}</td><td>{log.ngQty}</td><td><StatusBadge value={log.status}/></td><td>{formatDate(log.createdAt)}</td></tr>)}</tbody></table></div></section>
  </div>;
}

function MachineMini({ machine }) { return <div className="mes-card machine-card"><span className="machine-code">{machine.processCode} · {machine.machineId}</span><div className="machine-title">{machine.processName || machine.machineName}</div><StatusBadge value={machine.status}/></div>; }
