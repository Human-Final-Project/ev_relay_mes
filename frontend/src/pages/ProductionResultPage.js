import React, { useMemo, useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, PageHeader, formatDate } from "../components/MesComponents";
import { DonutChart, StackedBarChart, summarizeByProcess } from "../components/MesCharts";

const processOrder = ["OP20", "OP30", "OP40_OP50", "OP60", "OP70", "OP80"];
const emptyFilters = { workOrderId: "", lotNo: "", conditionType: "process", conditionValue: "", startAt: "", endAt: "" };

export default function ProductionResultPage() {
  const [filters, setFilters] = useState(emptyFilters);
  const [applied, setApplied] = useState({ status: "COMPLETED" });
  const workOrders = useApiData(() => MesApi.getWorkOrders({}), []);
  const machines = useApiData(MesApi.getMachines, []);
  const processes = useApiData(MesApi.getProcesses, []);
  const lots = useApiData(
    () => filters.workOrderId
      ? MesApi.getLots({ workOrderId: filters.workOrderId })
      : Promise.resolve({ data: [] }),
    [filters.workOrderId]
  );
  const logs = useApiData(() => MesApi.getProductionLogs(applied), [JSON.stringify(applied)]);

  const summary = useMemo(() => {
    const rows = logs.data || [];
    const ok = rows.reduce((sum, row) => sum + (Number(row.okQty) || 0), 0);
    const ng = rows.reduce((sum, row) => sum + (Number(row.ngQty) || 0), 0);
    return { ok, ng, processes: summarizeByProcess(rows, processOrder) };
  }, [logs.data]);

  const conditionOptions = filters.conditionType === "machine"
    ? (machines.data || []).map((machine) => ({ value: machine.machineId, label: `${machine.machineId} · ${machine.machineName}` }))
    : (processes.data || []).map((process) => ({ value: process.processCode, label: `${process.processCode} · ${process.processName}` }));

  const apply = () => {
    const next = {
      workOrderId: filters.workOrderId || undefined,
      lotNo: filters.lotNo || undefined,
      status: "COMPLETED",
      startAt: filters.startAt || undefined,
      endAt: filters.endAt || undefined,
    };
    if (filters.conditionValue) {
      next[filters.conditionType === "machine" ? "machineId" : "processCode"] = filters.conditionValue;
    }
    setApplied(next);
  };

  const reset = () => {
    setFilters(emptyFilters);
    setApplied({ status: "COMPLETED" });
  };

  return <div className="mes-page">
    <PageHeader title="생산 실적" description="작업지시를 선택한 뒤 해당 작업지시의 전체 LOT 또는 특정 LOT 생산 실적을 조회합니다." actions={<button className="btn secondary" onClick={logs.reload}>새로고침</button>}/>

    <section className="mes-card">
      <div className="mes-filter">
        <Field label="작업지시">
          <select
            value={filters.workOrderId}
            onChange={(event) => setFilters({ ...filters, workOrderId: event.target.value, lotNo: "" })}
          >
            <option value="">전체 작업지시</option>
            {(workOrders.data || []).map((order) =>
              <option key={order.workOrderId} value={order.workOrderId}>
                {order.orderNo} · {order.itemName}
              </option>
            )}
          </select>
        </Field>
        <Field label="LOT">
          <select
            value={filters.lotNo}
            disabled={!filters.workOrderId || lots.loading}
            onChange={(event) => setFilters({ ...filters, lotNo: event.target.value })}
          >
            <option value="">{filters.workOrderId ? "해당 작업지시 전체 LOT" : "작업지시를 먼저 선택하세요"}</option>
            {(lots.data || []).map((lot) =>
              <option key={lot.lotId} value={lot.lotNo}>
                {lot.lotNo} · {lot.lotType === "SUPPLEMENT" ? `보충 ${lot.productionRound - 1}차` : "최초"}
              </option>
            )}
          </select>
        </Field>
        <Field label="조건">
          <select value={filters.conditionType} onChange={(event) => setFilters({ ...filters, conditionType: event.target.value, conditionValue: "" })}>
            <option value="process">공정</option>
            <option value="machine">설비</option>
          </select>
        </Field>
        <Field label={filters.conditionType === "machine" ? "설비 선택" : "공정 선택"}>
          <select value={filters.conditionValue} onChange={(event) => setFilters({ ...filters, conditionValue: event.target.value })}>
            <option value="">전체</option>
            {conditionOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </Field>
        <Field label="시작 시각"><input type="datetime-local" value={filters.startAt} onChange={(event) => setFilters({ ...filters, startAt: event.target.value })}/></Field>
        <Field label="종료 시각"><input type="datetime-local" value={filters.endAt} onChange={(event) => setFilters({ ...filters, endAt: event.target.value })}/></Field>
        <button className="btn" onClick={apply}>조회</button>
        <button className="btn secondary" onClick={reset}>초기화</button>
      </div>
    </section>

    {logs.loading ? <LoadingState/> : logs.error ? <ErrorState error={logs.error} onRetry={logs.reload}/> : !(logs.data || []).length ? <EmptyState/> : <>
      <section className="mes-card production-analytics">
        <div><div className="chart-heading"><div><h3>공정별 생산량</h3><p>조회 조건 내 완료 실적</p></div><div className="chart-key"><span className="ok">OK</span><span className="ng">NG</span></div></div><StackedBarChart rows={summary.processes}/></div>
        <DonutChart ariaLabel="조회 생산 실적 OK 및 NG 비율" centerValue={summary.ok + summary.ng ? `${(summary.ok / (summary.ok + summary.ng) * 100).toFixed(1)}%` : "0%"} centerLabel="양품률" segments={[{ label: "OK", value: summary.ok, color: "#0ea5a4" }, { label: "NG", value: summary.ng, color: "#f43f5e" }]}/>
      </section>

      <div className="mes-table-wrap"><table className="mes-table">
        <thead><tr><th>LOT</th><th>공정/설비</th><th>투입</th><th>OK</th><th>NG</th><th>공정효율</th><th>종료</th></tr></thead>
        <tbody>{logs.data.map((log) => <tr key={log.productionLogId}>
          <td className="mono">{log.lotNo}</td>
          <td>{log.processName}<br/><span className="mono">{log.processCode} · {log.machineId}</span></td>
          <td>{log.inputQty}</td><td>{log.okQty}</td><td>{log.ngQty}</td>
          <td>{log.inputQty ? `${(log.okQty / log.inputQty * 100).toFixed(1)}%` : "-"}</td>
          <td>{formatDate(log.endedAt)}</td>
        </tr>)}</tbody>
      </table></div>
    </>}
  </div>;
}
