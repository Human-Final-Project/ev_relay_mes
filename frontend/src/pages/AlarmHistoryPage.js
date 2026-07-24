import React, { useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, PageHeader, StatusBadge, formatDate } from "../components/MesComponents";

const emptyFilters = {
  conditionType: "process",
  conditionValue: "",
  alarmCode: "",
  alarmLevel: "",
  cleared: "",
  startAt: "",
  endAt: "",
};

export default function AlarmHistoryPage({ currentUser }) {
  const [filters, setFilters] = useState(emptyFilters);
  const [applied, setApplied] = useState({});
  const [clearingId, setClearingId] = useState(null);
  const [actionError, setActionError] = useState(null);
  const alarms = useApiData(() => MesApi.getMachineAlarms(applied), [JSON.stringify(applied)]);
  const machines = useApiData(MesApi.getMachines, []);
  const processes = useApiData(MesApi.getProcesses, []);
  const alarmCodes = useApiData(MesApi.getAlarmCodes, []);
  const canClear = ["ADMIN", "MANAGER", "OPERATOR"].includes(currentUser?.role);

  const conditionOptions = filters.conditionType === "machine"
    ? (machines.data || []).map((machine) => ({ value: machine.machineId, label: `${machine.machineId} · ${machine.machineName}` }))
    : (processes.data || []).map((process) => ({ value: process.processCode, label: `${process.processCode} · ${process.processName}` }));

  const applyFilters = () => {
    const next = {
      alarmCode: filters.alarmCode || undefined,
      alarmLevel: filters.alarmLevel || undefined,
      startAt: filters.startAt || undefined,
      endAt: filters.endAt || undefined,
    };
    if (filters.conditionValue) {
      next[filters.conditionType === "machine" ? "machineId" : "processCode"] = filters.conditionValue;
    }
    if (filters.cleared !== "") next.cleared = filters.cleared === "true";
    setApplied(next);
  };

  const resetFilters = () => {
    setFilters(emptyFilters);
    setApplied({});
  };

  const clearAlarm = async (alarmId) => {
    try {
      setClearingId(alarmId);
      setActionError(null);
      await MesApi.clearMachineAlarm(alarmId);
      await Promise.all([alarms.reload(), machines.reload()]);
    } catch (error) {
      setActionError(error);
    } finally {
      setClearingId(null);
    }
  };

  const loading = alarms.loading && alarms.data === null;
  const error = alarms.data === null ? alarms.error : null;

  return <div className="mes-page">
    <PageHeader
      title="설비 알람"
      description="설비 WARNING·ERROR 이력을 조회합니다. 작업 차단 ERROR 알람에만 해제 기능을 제공합니다."
      actions={<button className="btn secondary" onClick={alarms.reload}>새로고침</button>}
    />

    <section className="mes-card">
      <div className="mes-filter">
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
        <Field label="알람 코드">
          <select value={filters.alarmCode} onChange={(event) => setFilters({ ...filters, alarmCode: event.target.value })}>
            <option value="">전체</option>
            {(alarmCodes.data || []).map((code) => <option key={code.alarmCode} value={code.alarmCode}>{code.alarmName} ({code.alarmCode})</option>)}
          </select>
        </Field>
        <Field label="레벨">
          <select value={filters.alarmLevel} onChange={(event) => setFilters({ ...filters, alarmLevel: event.target.value })}>
            <option value="">전체</option><option value="WARN">WARNING</option><option value="ERROR">ERROR</option>
          </select>
        </Field>
        <Field label="상태">
          <select value={filters.cleared} onChange={(event) => setFilters({ ...filters, cleared: event.target.value })}>
            <option value="">전체</option><option value="false">발생 중</option><option value="true">해제됨</option>
          </select>
        </Field>
        <Field label="시작 시각"><input type="datetime-local" value={filters.startAt} onChange={(event) => setFilters({ ...filters, startAt: event.target.value })}/></Field>
        <Field label="종료 시각"><input type="datetime-local" value={filters.endAt} onChange={(event) => setFilters({ ...filters, endAt: event.target.value })}/></Field>
        <button className="btn" onClick={applyFilters}>조회</button>
        <button className="btn secondary" onClick={resetFilters}>초기화</button>
      </div>
    </section>

    {actionError && <ErrorState error={actionError}/>} 
    <section className="mes-card">
      {loading ? <LoadingState/> : error ? <ErrorState error={error} onRetry={alarms.reload}/> : !(alarms.data || []).length ? <EmptyState/> :
        <div className="mes-table-wrap"><table className="mes-table">
          <thead><tr><th>설비</th><th>공정/LOT</th><th>알람</th><th>레벨</th><th>발생 시각</th><th>상태</th><th>해제 시각</th><th>메시지</th><th></th></tr></thead>
          <tbody>{alarms.data.map((alarm) => {
            const blocking = String(alarm.alarmLevel).toUpperCase() === "ERROR";
            return <tr key={alarm.machineAlarmHistoryId}>
              <td>{alarm.machineName}<br/><span className="mono">{alarm.machineId}</span></td>
              <td>{alarm.processName || alarm.processCode || "-"}<br/><span className="mono">{alarm.lotNo || "-"}</span></td>
              <td><strong>{alarm.alarmName || alarm.alarmCode}</strong><br/><span className="mono">{alarm.alarmCode}</span></td>
              <td><StatusBadge value={alarm.alarmLevel}/></td>
              <td>{formatDate(alarm.occurredAt)}</td>
              <td>{blocking
                ? <span className={`mes-status ${alarm.cleared ? "status-completed" : "status-stopped"}`}>{alarm.cleared ? "해제됨" : "발생 중"}</span>
                : <span className="mes-status status-idle">경고 기록</span>}
              </td>
              <td>{blocking ? formatDate(alarm.clearedAt) : "-"}</td>
              <td>{alarm.message || "-"}</td>
              <td>{canClear && blocking && !alarm.cleared && <button className="btn small" disabled={clearingId === alarm.machineAlarmHistoryId} onClick={() => clearAlarm(alarm.machineAlarmHistoryId)}>{clearingId === alarm.machineAlarmHistoryId ? "처리 중" : "해제"}</button>}</td>
            </tr>;
          })}</tbody>
        </table></div>}
    </section>
  </div>;
}
