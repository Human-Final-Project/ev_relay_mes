import React, { useEffect, useMemo, useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { DonutChart } from "../components/MesCharts";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, StatusBadge, formatDate } from "../components/MesComponents";
import { Link } from "react-router-dom";

const processOrder = ["OP20", "OP30", "OP40_OP50", "OP60", "OP70", "OP80"];
const chartColors = ["#2563eb", "#0ea5e9", "#14b8a6", "#22c55e", "#f59e0b", "#ef4444"];

export default function DashboardPage({ currentUser }) {
  const today = todayRange();
  const week = currentWeekRange();
  const weekStart = toLocalDate(week.start);
  const machines = useApiData(MesApi.getMachines, []);
  const workOrders = useApiData(() => MesApi.getWorkOrders({}), []);
  const weeklyTarget = useApiData(
    () => MesApi.getWeeklyProductionTarget(weekStart),
    [weekStart]
  );
  const weeklyLogs = useApiData(
    () => MesApi.getProductionLogs({
      startAt: toLocalDateTime(week.start),
      endAt: toLocalDateTime(week.end),
      status: "COMPLETED",
    }),
    [weekStart]
  );
  const productionLogs = useApiData(
    () => MesApi.getProductionLogs({ startAt: today.startAt, endAt: today.endAt, status: "COMPLETED" }),
    [today.startAt, today.endAt]
  );
  const defects = useApiData(
    () => MesApi.getDefects({ startAt: today.startAt, endAt: today.endAt }),
    [today.startAt, today.endAt]
  );
  const notices = useApiData(MesApi.getNotices, []);
  const [targetForm, setTargetForm] = useState(null);
  const [targetSaving, setTargetSaving] = useState(false);
  const [targetError, setTargetError] = useState(null);
  const canSetTarget = ["ADMIN", "MANAGER"].includes(currentUser?.role);
  const reloadMachines = machines.reload;
  const reloadProductionLogs = productionLogs.reload;
  const reloadDefects = defects.reload;
  const reloadWorkOrders = workOrders.reload;
  const reloadWeeklyTarget = weeklyTarget.reload;
  const reloadWeeklyLogs = weeklyLogs.reload;

  useEffect(() => {
    const machineTimer = setInterval(reloadMachines, 1000);
    const aggregateTimer = setInterval(() => {
      reloadProductionLogs();
      reloadDefects();
      reloadWorkOrders();
      reloadWeeklyTarget();
      reloadWeeklyLogs();
    }, 5000);
    return () => {
      clearInterval(machineTimer);
      clearInterval(aggregateTimer);
    };
  }, [reloadMachines, reloadProductionLogs, reloadDefects,
    reloadWorkOrders, reloadWeeklyTarget, reloadWeeklyLogs]);

  const reload = () => {
    machines.reload();
    productionLogs.reload();
    defects.reload();
    workOrders.reload();
    weeklyTarget.reload();
    weeklyLogs.reload();
    notices.reload();
  };
  const resources = [machines, productionLogs, defects, workOrders, weeklyTarget, weeklyLogs];
  const loading = resources.some((resource) => resource.loading && resource.data === null);
  const error = resources.find((resource) => resource.error && resource.data === null)?.error;

  const hourly = useMemo(() => summarizeHourly(productionLogs.data || []), [productionLogs.data]);
  const defectTypes = useMemo(() => summarizeDefects(defects.data || []), [defects.data]);
  const weeklySchedule = summarizeWeeklySchedule(workOrders.data || [], week);
  const weeklyOutput = summarizeFinalOutput(weeklyLogs.data || []);
  const target = weeklyTarget.data || { targetQty: 0, targetDefectRate: 5, configured: false };
  const attainment = target.targetQty > 0
    ? Math.min(100, weeklyOutput.ok / target.targetQty * 100) : 0;
  const remaining = Math.max(0, Number(target.targetQty || 0) - weeklyOutput.ok);
  const defectRate = weeklyOutput.total > 0 ? weeklyOutput.ng / weeklyOutput.total * 100 : 0;
  const targetDefectRate = Number(target.targetDefectRate ?? 5);
  const sortedMachines = useMemo(() => [...(machines.data || [])].sort(
    (left, right) => processOrder.indexOf(left.processCode) - processOrder.indexOf(right.processCode)
  ), [machines.data]);

  const openTargetForm = () => {
    setTargetError(null);
    setTargetForm({
      targetQty: target.configured ? String(target.targetQty) : "",
      targetDefectRate: String(target.targetDefectRate ?? 5),
    });
  };

  const saveTarget = async () => {
    setTargetSaving(true);
    setTargetError(null);
    try {
      await MesApi.saveWeeklyProductionTarget(weekStart, {
        targetQty: Number(targetForm.targetQty),
        targetDefectRate: Number(targetForm.targetDefectRate),
      });
      setTargetForm(null);
      await weeklyTarget.reload();
    } catch (error) {
      setTargetError(error);
    } finally {
      setTargetSaving(false);
    }
  };

  if (loading) return <LoadingState/>;
  if (error) return <ErrorState error={error} onRetry={reload}/>;

  return <div className="mes-page operations-dashboard dashboard-v2">
    <PageHeader
      title="대시보드"
      description={`${formatWeekLabel(week)} 목표 달성과 불량·일정 위험을 실시간으로 확인합니다.`}
      actions={<><span className="live-indicator">● 설비 1초 · 집계 5초 갱신</span>{canSetTarget && <button className="btn" onClick={openTargetForm}>주간 목표 설정</button>}<button className="btn secondary" onClick={reload}>지금 갱신</button></>}
    />

    <DashboardNotices notices={notices}/>

    <div className="dashboard-primary-kpis">
      <MetricCard label="금주 목표" value={Number(target.targetQty || 0).toLocaleString()} unit="EA" description={target.configured ? "별도 주간 생산 목표" : "목표 미설정"} icon="flag" tone={target.configured ? "normal" : "warning"}/>
      <MetricCard label="금주 양품 실적" value={weeklyOutput.ok.toLocaleString()} unit="EA" description={`부족 ${remaining.toLocaleString()}개`} icon="factory"/>
      <MetricCard label="금주 달성률" value={`${attainment.toFixed(1)}%`} unit="PLAN" description={`목표 대비 ${weeklyOutput.ok.toLocaleString()} / ${Number(target.targetQty || 0).toLocaleString()}`} icon="speed" tone={attainment < 80 ? "warning" : "normal"}/>
      <MetricCard label="금주 불량률" value={`${defectRate.toFixed(1)}%`} unit="NG RATE" description={`목표 ${targetDefectRate.toFixed(1)}% 이하 · NG ${weeklyOutput.ng.toLocaleString()}`} icon="percent" tone={defectRate > targetDefectRate ? "danger" : "normal"}/>
      <MetricCard label="일정 위험" value={(weeklySchedule.warning + weeklySchedule.delayed).toLocaleString()} unit="ORDERS" description={`주의 ${weeklySchedule.warning} · 지연 ${weeklySchedule.delayed} · 미지정 ${weeklySchedule.unscheduled}`} icon="event_busy" tone={weeklySchedule.delayed ? "danger" : weeklySchedule.warning || weeklySchedule.unscheduled ? "warning" : "normal"}/>
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

    <section className="mes-card dashboard-machine-panel">
      <div className="pipeline-heading"><div><h2>설비 가동 현황</h2><p>실시간 설비 상태와 현재 작업 LOT</p></div><MachineStatusSummary rows={sortedMachines}/></div>
      <div className="dashboard-machine-grid">
        {sortedMachines.map((machine) => <article className={`dashboard-machine-card status-${String(machine.status || "IDLE").toLowerCase()}`} key={machine.machineId}>
          <div><strong>{machine.processCode} · {machine.processName}</strong><span className="mono">{machine.machineId}</span></div>
          <StatusBadge value={machine.status}/>
          <dl><dt>현재 LOT</dt><dd>{machine.currentLotNo || "-"}</dd><dt>진행률</dt><dd>{machine.targetQty > 0 ? `${machine.processedQty || 0}/${machine.targetQty} (${machine.progressPercent || 0}%)` : "대기"}</dd></dl>
        </article>)}
      </div>
    </section>

    {targetForm && <Modal
      title={`${formatWeekLabel(week)} 주간 목표 설정`}
      onClose={() => setTargetForm(null)}
      footer={<>
        <button className="btn secondary" onClick={() => setTargetForm(null)}>취소</button>
        <button
          className="btn"
          disabled={targetSaving
            || Number(targetForm.targetQty) <= 0
            || targetForm.targetDefectRate === ""
            || Number(targetForm.targetDefectRate) < 0
            || Number(targetForm.targetDefectRate) > 100}
          onClick={saveTarget}
        >저장</button>
      </>}
    >
      <div className="mes-form-grid">
        <Field label="주간 목표 수량">
          <input
            type="number"
            min="1"
            value={targetForm.targetQty}
            onChange={(event) => setTargetForm({...targetForm, targetQty:event.target.value})}
          />
        </Field>
        <Field label="목표 불량률">
          <input
            type="number"
            min="0"
            max="100"
            step="0.1"
            value={targetForm.targetDefectRate}
            onChange={(event) => setTargetForm({...targetForm, targetDefectRate:event.target.value})}
          />
        </Field>
      </div>
      <p className="form-hint">
        주간 목표는 대시보드 성과 기준입니다. 작업지시 목표 수량과 자동 보충 생산에는 영향을 주지 않습니다.
      </p>
      {targetError && <ErrorState error={targetError}/>}
    </Modal>}

  </div>;
}

function DashboardNotices({ notices }) {
  const rows = (notices.data || []).slice(0, 1);
  return <section className="mes-card dashboard-notice-panel">
    <div className="dashboard-notice-heading">
      <div><span className="material-symbols-outlined">campaign</span><h2>최근 공지</h2></div>
      <Link to="/notices">전체보기</Link>
    </div>
    {notices.loading && notices.data === null ? <span className="dashboard-notice-message">공지를 불러오는 중입니다.</span> :
      notices.error && notices.data === null ? <span className="dashboard-notice-message error">공지를 불러오지 못했습니다.</span> :
      !rows.length ? <span className="dashboard-notice-message">등록된 공지가 없습니다.</span> :
      <div className="dashboard-notice-list">
        {rows.map((notice) => <Link to="/notices" className="dashboard-notice-item" key={notice.noticeId}>
          <span className={`dashboard-notice-type ${notice.pinned ? "pinned" : ""}`}>
            {notice.pinned ? "고정" : "공지"}
          </span>
          <strong>{notice.title}</strong>
          <span>{notice.authorName}</span>
          <time>{formatDate(notice.createdAt)}</time>
        </Link>)}
      </div>}
  </section>;
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

function summarizeFinalOutput(logs) {
  return logs
    .filter((log) => log.processCode === "OP80")
    .reduce((result, log) => {
      result.ok += Number(log.okQty) || 0;
      result.ng += Number(log.ngQty) || 0;
      result.total = result.ok + result.ng;
      return result;
    }, { ok: 0, ng: 0, total: 0 });
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

function summarizeWeeklySchedule(orders, week) {
  const now = new Date();
  const activeStatuses = new Set(["CREATED", "RELEASED", "RUNNING"]);
  const included = orders.filter((order) => {
    if (order.status === "CANCELED") return false;
    const start = parseDate(order.plannedStartAt);
    const end = parseDate(order.plannedEndAt);
    if (!start || !end) return activeStatuses.has(order.status);
    return start < week.end && end > week.start;
  });

  const result = {
    orderCount: included.length,
    normal: 0,
    warning: 0,
    delayed: 0,
    unscheduled: 0,
  };

  included.forEach((order) => {
    const target = Number(order.targetQty) || 0;
    const completed = Number(order.completedOkQty) || 0;
    const remaining = Math.max(0, target - completed);
    const start = parseDate(order.plannedStartAt);
    const end = parseDate(order.plannedEndAt);
    if (!start || !end) {
      result.unscheduled += 1;
    } else if (remaining === 0 || order.status === "COMPLETED") {
      result.normal += 1;
    } else if (end < now) {
      result.delayed += 1;
    } else {
      const duration = Math.max(1, end.getTime() - start.getTime());
      const elapsedRate = Math.min(1, Math.max(0, (now.getTime() - start.getTime()) / duration));
      const completionRate = target > 0 ? completed / target : 0;
      if (elapsedRate - completionRate >= 0.1) result.warning += 1;
      else result.normal += 1;
    }
  });
  return result;
}

function todayRange() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { startAt: toLocalDateTime(start), endAt: toLocalDateTime(end) };
}

function currentWeekRange() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const day = start.getDay();
  start.setDate(start.getDate() - (day === 0 ? 6 : day - 1));
  const end = new Date(start);
  end.setDate(end.getDate() + 7);
  return { start, end };
}

function formatWeekLabel(week) {
  const format = (date) => `${date.getMonth() + 1}.${date.getDate()}`;
  const inclusiveEnd = new Date(week.end);
  inclusiveEnd.setDate(inclusiveEnd.getDate() - 1);
  return `${format(week.start)}~${format(inclusiveEnd)}`;
}

function parseDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function toLocalDateTime(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function toLocalDate(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
