import React, { useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import {
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  StatusBadge,
} from "../components/MesComponents";

const EMPTY_FILTERS = {
  machineId: "",
  alarmCode: "",
  alarmLevel: "",
  cleared: "",
  startAt: "",
  endAt: "",
};

export default function AlarmHistoryPage({ currentUser, embedded = false }) {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [applied, setApplied] = useState({});
  const [clearingId, setClearingId] = useState(null);
  const [actionError, setActionError] = useState(null);
  const alarms = useApiData(() => MesApi.getMachineAlarms(applied), [JSON.stringify(applied)]);
  const machines = useApiData(MesApi.getMachines, []);
  const alarmCodes = useApiData(MesApi.getAlarmCodes, []);
  const canClear = ["ADMIN", "MANAGER", "OPERATOR"].includes(currentUser?.role);

  const applyFilters = () => {
    const next = { ...filters };
    if (next.cleared !== "") next.cleared = next.cleared === "true";
    setApplied(next);
  };

  const resetFilters = () => {
    setFilters(EMPTY_FILTERS);
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

  return <div className={embedded ? "alarm-history-embedded" : "mes-page"}>
    {!embedded && <PageHeader
      title="설비 알람 이력"
      description="설비별 WARNING·ERROR 발생 및 해제 상태를 조회합니다. 해제자는 화면에 표시하지 않습니다."
      actions={<button className="btn secondary" onClick={alarms.reload}>새로고침</button>}
    />}

    <section className="mes-card">
      <div className="mes-filter">
        <Field label="설비">
          <select value={filters.machineId} onChange={(e) => setFilters({ ...filters, machineId: e.target.value })}>
            <option value="">전체</option>
            {(machines.data || []).map((machine) => <option key={machine.machineId} value={machine.machineId}>{machine.machineId}</option>)}
          </select>
        </Field>
        <Field label="알람 코드">
          <select value={filters.alarmCode} onChange={(e) => setFilters({ ...filters, alarmCode: e.target.value })}>
            <option value="">전체</option>
            {(alarmCodes.data || []).map((code) => <option key={code.alarmCode} value={code.alarmCode}>{code.alarmName} ({code.alarmCode})</option>)}
          </select>
        </Field>
        <Field label="레벨">
          <select value={filters.alarmLevel} onChange={(e) => setFilters({ ...filters, alarmLevel: e.target.value })}>
            <option value="">전체</option>
            <option value="WARN">WARNING</option>
            <option value="ERROR">ERROR</option>
          </select>
        </Field>
        <Field label="상태">
          <select value={filters.cleared} onChange={(e) => setFilters({ ...filters, cleared: e.target.value })}>
            <option value="">전체</option>
            <option value="false">발생 중</option>
            <option value="true">해제됨</option>
          </select>
        </Field>
        <Field label="시작 시각"><input type="datetime-local" value={filters.startAt} onChange={(e) => setFilters({ ...filters, startAt: e.target.value })}/></Field>
        <Field label="종료 시각"><input type="datetime-local" value={filters.endAt} onChange={(e) => setFilters({ ...filters, endAt: e.target.value })}/></Field>
        <button className="btn" onClick={applyFilters}>조회</button>
        <button className="btn secondary" onClick={resetFilters}>초기화</button>
        {embedded && <button className="btn secondary" onClick={alarms.reload}>새로고침</button>}
      </div>
    </section>

    {actionError && <ErrorState error={actionError}/>} 
    <section className="mes-card">
      {loading ? <LoadingState/> : error ? <ErrorState error={error} onRetry={alarms.reload}/> : !(alarms.data || []).length ? <EmptyState/> :
        <div className="mes-table-wrap"><table className="mes-table">
          <thead><tr><th>설비</th><th>공정/LOT</th><th>알람</th><th>레벨</th><th>발생 시각</th><th>상태</th><th>해제 시각</th><th>내용</th><th></th></tr></thead>
          <tbody>{alarms.data.map((alarm) => <tr key={alarm.machineAlarmHistoryId}>
            <td><div className="alarm-inline-cell" title={`${alarm.machineName || ""} ${alarm.machineId || ""}`}><strong className="mono">{alarm.machineId}</strong><span>{alarm.machineName}</span></div></td>
            <td><div className="alarm-inline-cell" title={`${alarm.processName || alarm.processCode || "-"} / ${alarm.lotNo || "-"}`}><strong className="mono">{alarm.processCode || "-"}</strong><span className="mono">{alarm.lotNo || "-"}</span></div></td>
            <td><div className="alarm-inline-cell alarm-name-cell" title={`${alarm.alarmName || alarm.alarmCode} (${alarm.alarmCode})`}><strong>{alarm.alarmName || alarm.alarmCode}</strong><span className="mono">{alarm.alarmCode}</span></div></td>
            <td><StatusBadge value={alarm.alarmLevel}/></td>
            <td className="alarm-time">{formatAlarmDate(alarm.occurredAt)}</td>
            <td><span className={`mes-status ${alarm.cleared ? "status-completed" : "status-stopped"}`}>{alarm.cleared ? "해제됨" : "발생 중"}</span></td>
            <td className="alarm-time">{formatAlarmDate(alarm.clearedAt)}</td>
            <td><div className="alarm-description" title={alarm.description || alarm.message || "-"}>{alarm.description || alarm.message || "-"}</div></td>
            <td>{canClear && !alarm.cleared && <button className="btn small" disabled={clearingId === alarm.machineAlarmHistoryId} onClick={() => clearAlarm(alarm.machineAlarmHistoryId)}>{clearingId === alarm.machineAlarmHistoryId ? "처리 중" : "해제"}</button>}</td>
          </tr>)}</tbody>
        </table></div>}
    </section>
  </div>;
}

function formatAlarmDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (number) => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
